package com.gaebriu.cyclo.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gaebriu.cyclo.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Uma versão publicada no GitHub, mais recente do que a instalada. */
data class AvailableUpdate(
    val tag: String,
    val version: AppVersion,
    /** A página da release (notas e ficheiros). */
    val pageUrl: String,
    /** O .apk da release (null = a release não tem; "Download now" abre a página). */
    val apkUrl: String?,
    val apkSize: Long,
)

/** O estado do download, para o new-update-group. */
sealed interface DownloadState {
    data object Idle : DownloadState

    /** [received] de [total] bytes ([total] ≤ 0 = desconhecido). */
    data class Running(val received: Long, val total: Long) : DownloadState {
        val fraction: Float? get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null
    }

    data class Done(val file: File) : DownloadState
    data class Failed(val reason: Reason) : DownloadState {
        /** [Invalid]: o .apk descarregado não é o Cyclo da versão anunciada. */
        enum class Reason { Network, Storage, Invalid }
    }
}

/**
 * Atualizações do Cyclo a partir das releases do GitHub (o repositório do projeto,
 * [R.string.github_url]): verifica se há uma versão mais recente, descarrega o .apk dela com o
 * progresso real e entrega-o ao instalador do Android. Não há instalação automática nem tokens:
 * só a API pública do GitHub, sem autenticação, e o instalador do sistema (o utilizador confirma).
 *
 * - Verifica uma vez por arranque a frio ([checkIfDue]): um processo novo verifica ao abrir a app;
 *   voltar do multitarefa ou do segundo plano não é um arranque novo e não volta a verificar. O
 *   resultado fica guardado (com o ETag do GitHub, para pedidos repetidos não gastarem o limite da
 *   API) e serve até à verificação seguinte. Sem rede, fica o último resultado.
 * - Lê a lista de releases (não a "latest", que ignora as prereleases: as versões do Cyclo são
 *   alphas/betas) e escolhe a MAIS RECENTE de todas (não a seguinte à instalada): com a Beta 1.3
 *   instalada e a 1.4 e a 1.5 publicadas, o aviso mostra logo a 1.5 e descarrega o .apk dela.
 * - O download corre no processo (continua com as definições fechadas) e pode ser cancelado.
 * - Dispensar (o X) esconde o aviso durante [DismissMs] (guardado entre sessões); "Undo" na barra de
 *   baixo ([undoDismiss]) volta a mostrá-lo logo, sem nova verificação. Passado o intervalo, se a
 *   atualização continuar disponível, volta a aparecer.
 */
object CycloUpdates {
    private const val Prefs = "updates"
    private const val KeyLastCheck = "last_check"
    private const val KeyEtag = "etag"
    private const val KeyCheckedFor = "checked_for"
    private const val KeyRelease = "release"
    private const val KeyDismissedAt = "dismissed_at"
    private const val KeyDismissedTag = "dismissed_tag"

    /** Quanto tempo o aviso fica escondido depois de o dispensar. */
    const val DismissMs = 12L * 60 * 60 * 1000

    private const val ConnectTimeoutMs = 10_000
    private const val ReadTimeoutMs = 20_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private val _available = MutableStateFlow<AvailableUpdate?>(null)

    /** A atualização disponível (null = nenhuma, ou a instalada já é a mais recente). */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _dismissedUntil = MutableStateFlow(0L)

    /** Até quando o aviso está dispensado (epoch ms). */
    val dismissedUntil: StateFlow<Long> = _dismissedUntil.asStateFlow()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private var loaded = false

    /** Este processo já verificou (ou está a verificar): um arranque a frio, uma verificação. */
    @Volatile
    private var checkedThisProcess = false

    /** Acabou um download nesta sessão: o instalador abre uma vez, sozinho ([takeAutoInstall]). */
    @Volatile
    private var autoInstall = false

    /** true uma só vez depois de cada download acabado (abre o instalador sem tocar em nada). */
    fun takeAutoInstall(): Boolean = autoInstall.also { autoInstall = false }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    private fun installedName(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    fun installedVersion(context: Context): AppVersion? = installedName(context)?.let(AppVersion::parse)

    /** Lê o que ficou guardado (a última release vista e quando foi dispensada). */
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        // Um processo novo não tem download a decorrer: o que ficou da sessão anterior (um .apk já
        // instalado ou a meio) já não serve.
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
        val dismissedAt = prefs.getLong(KeyDismissedAt, 0L)
        _dismissedUntil.value = if (dismissedAt > 0) dismissedAt + DismissMs else 0L
        _available.value = prefs.getString(KeyRelease, null)?.let { decode(it) }?.takeIf { isNewer(context, it) }
    }

    /**
     * Verifica uma vez por processo (o arranque a frio); as chamadas seguintes da mesma sessão (voltar
     * do multitarefa, recriar a atividade) não fazem nada. Não bloqueia: corre em fundo.
     */
    fun checkIfDue(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        load(app)
        // A instalada pode ter mudado (a app foi atualizada): o que estava guardado deixa de valer.
        _available.value = _available.value?.takeIf { isNewer(app, it) }
        if (!force && checkedThisProcess) return
        if (checkJob?.isActive == true) return
        checkedThisProcess = true
        checkJob = scope.launch { check(app) }
    }

    private suspend fun check(context: Context) {
        val prefs = prefs(context)
        val repo = repositoryPath(context) ?: return
        val connection = try {
            (URL("https://api.github.com/repos/$repo/releases?per_page=20").openConnection() as HttpURLConnection).apply {
                connectTimeout = ConnectTimeoutMs
                readTimeout = ReadTimeoutMs
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Cyclo-Android")
                // O ETag só vale para a versão instalada com que se escolheu a release guardada (uma
                // versão instalada diferente pode escolher outra release da mesma lista).
                prefs.getString(KeyEtag, null)
                    ?.takeIf { prefs.getString(KeyCheckedFor, null) == installedName(context) }
                    ?.let { setRequestProperty("If-None-Match", it) }
            }
        } catch (e: IOException) {
            return
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    // Nada mudou: o que está guardado continua a valer.
                    prefs.edit().putLong(KeyLastCheck, System.currentTimeMillis()).apply()
                }
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val newest = newestRelease(body, installedVersion(context))
                    prefs.edit()
                        .putLong(KeyLastCheck, System.currentTimeMillis())
                        .putString(KeyEtag, connection.getHeaderField("ETag"))
                        .putString(KeyCheckedFor, installedName(context))
                        .apply {
                            if (newest != null) putString(KeyRelease, encode(newest)) else remove(KeyRelease)
                        }
                        .apply()
                    _available.value = newest
                }
                // 404 (repositório privado ou sem releases), 403 (limite da API), erros do servidor:
                // fica o último resultado e tenta-se na próxima vez.
                else -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sem rede ou resposta estragada: fica o último resultado.
        } finally {
            connection.disconnect()
        }
    }

    /** O X do new-update-group: esconde o aviso durante 12 horas (guardado entre sessões). */
    fun dismiss(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KeyDismissedAt, now)
            .putString(KeyDismissedTag, _available.value?.tag)
            .apply()
        _dismissedUntil.value = now + DismissMs
    }

    /** "Undo" depois de dispensar: o aviso volta já, com a release que já se conhecia (sem verificar). */
    fun undoDismiss(context: Context) {
        prefs(context).edit()
            .remove(KeyDismissedAt)
            .remove(KeyDismissedTag)
            .apply()
        _dismissedUntil.value = 0L
    }

    /** Começa a descarregar o .apk de [update] (ou recomeça, depois de um erro ou de cancelar). */
    fun startDownload(context: Context, update: AvailableUpdate) {
        val url = update.apkUrl ?: return
        val app = context.applicationContext
        if (downloadJob?.isActive == true) return
        _download.value = DownloadState.Running(0L, update.apkSize)
        downloadJob = scope.launch {
            val dir = File(app.cacheDir, "updates").apply { mkdirs() }
            // Uma só atualização guardada de cada vez.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "cyclo-${update.tag}.apk")
            val partial = File(dir, "cyclo-${update.tag}.apk.part")
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = ConnectTimeoutMs
                    readTimeout = ReadTimeoutMs
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Cyclo-Android")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                try {
                    if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.apkSize
                    var received = 0L
                    var lastReported = 0L
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                received += read
                                // O progresso real, sem inundar a interface (a cada ~1 % ou 64 KB).
                                if (received - lastReported >= maxOf(64 * 1024L, total / 100)) {
                                    lastReported = received
                                    _download.value = DownloadState.Running(received, total)
                                }
                            }
                        }
                    }
                    if (total > 0 && received != total) throw IOException("Incomplete")
                    _download.value = DownloadState.Running(received, total)
                } finally {
                    connection.disconnect()
                }
                if (!partial.renameTo(target)) throw IOException("Rename failed")
                // O .apk tem de ser mesmo o Cyclo da versão anunciada (não outra app nem outra versão).
                if (!matches(app, target, update)) {
                    target.delete()
                    _download.value = DownloadState.Failed(DownloadState.Failed.Reason.Invalid)
                    return@launch
                }
                autoInstall = true
                _download.value = DownloadState.Done(target)
            } catch (e: CancellationException) {
                partial.delete()
                _download.value = DownloadState.Idle
                throw e
            } catch (e: IOException) {
                partial.delete()
                val noSpace = e.message?.contains("ENOSPC") == true
                _download.value = DownloadState.Failed(if (noSpace) DownloadState.Failed.Reason.Storage else DownloadState.Failed.Reason.Network)
            } catch (e: Exception) {
                partial.delete()
                _download.value = DownloadState.Failed(DownloadState.Failed.Reason.Network)
            }
        }
    }

    /** O X ao lado da barra: pára mesmo o download (fecha a ligação e apaga o ficheiro a meio). */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _download.value = DownloadState.Idle
    }

    /** Depois de um erro: volta ao botão "Download now". */
    fun resetDownload() {
        if (_download.value !is DownloadState.Running) _download.value = DownloadState.Idle
    }

    /**
     * Entrega o .apk ao instalador do Android (o utilizador confirma). Se a app ainda não pode
     * instalar apps, abre primeiro a autorização "Instalar apps desconhecidas" do Cyclo.
     *
     * @return false se não houver nada que o abra.
     */
    fun install(context: Context, file: File): Boolean {
        val packageManager = context.packageManager
        val intent = if (!packageManager.canRequestPackageInstalls()) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** O .apk em [file] é deste pacote e tem a versão de [update] (lido do próprio ficheiro). */
    private fun matches(context: Context, file: File, update: AvailableUpdate): Boolean {
        val info = runCatching { context.packageManager.getPackageArchiveInfo(file.path, 0) }.getOrNull() ?: return false
        if (info.packageName != context.packageName) return false
        val version = info.versionName?.let(AppVersion::parse) ?: return false
        return version.compareTo(update.version) == 0
    }

    /** "dono/repositório" a partir do endereço do GitHub do projeto. */
    private fun repositoryPath(context: Context): String? =
        context.getString(R.string.github_url)
            .removePrefix("https://github.com/")
            .trim('/')
            .takeIf { it.count { c -> c == '/' } == 1 }

    private fun isNewer(context: Context, update: AvailableUpdate): Boolean {
        val installed = installedVersion(context) ?: return false
        return update.version > installed
    }

    /** A release mais recente do que [installed], entre as publicadas (sem rascunhos). */
    internal fun newestRelease(json: String, installed: AppVersion?): AvailableUpdate? {
        if (installed == null) return null
        val releases = JSONArray(json)
        // Uma versão final instalada só recebe versões finais; uma alpha/beta/rc recebe qualquer
        // versão mais recente (incluindo a final que a segue).
        val acceptPrereleases = installed.isPrerelease
        var best: AvailableUpdate? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            val version = AppVersion.parse(tag) ?: continue
            if (version <= installed) continue
            if (version.isPrerelease && !acceptPrereleases) continue
            if (best != null && version <= best.version) continue
            val assets = release.optJSONArray("assets") ?: JSONArray()
            // O .apk da release (com.gaebriu.cyclo.apk; havendo mais do que um, o que tem a tag no nome).
            var apk: JSONObject? = null
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (apk == null || name.contains(tag, ignoreCase = true)) apk = asset
            }
            best = AvailableUpdate(
                tag = tag,
                version = version,
                pageUrl = release.optString("html_url"),
                apkUrl = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") },
                apkSize = apk?.optLong("size", 0L) ?: 0L,
            )
        }
        return best
    }

    private fun encode(update: AvailableUpdate): String = JSONObject()
        .put("tag", update.tag)
        .put("page", update.pageUrl)
        .put("apk", update.apkUrl ?: JSONObject.NULL)
        .put("size", update.apkSize)
        .toString()

    private fun decode(text: String): AvailableUpdate? = runCatching {
        val o = JSONObject(text)
        val tag = o.getString("tag")
        AvailableUpdate(
            tag = tag,
            version = AppVersion.parse(tag) ?: return null,
            pageUrl = o.getString("page"),
            apkUrl = if (o.isNull("apk")) null else o.getString("apk"),
            apkSize = o.optLong("size", 0L),
        )
    }.getOrNull()

    /** Abre a página da release no navegador (sem .apk, ou para ler as notas). */
    fun openReleasePage(context: Context, update: AvailableUpdate): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
