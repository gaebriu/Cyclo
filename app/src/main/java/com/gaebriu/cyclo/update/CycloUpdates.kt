package com.gaebriu.cyclo.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gaebriu.cyclo.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    /** O .apk da release (null = a release não tem; o botão diz "See what's new"). */
    val apkUrl: String?,
    val apkSize: Long,
    /** Marcada como prerelease no GitHub (canal beta). */
    val beta: Boolean = false,
)

/**
 * Atualizações do Cyclo a partir das releases do GitHub (o repositório do projeto,
 * [R.string.github_url]): verifica se há uma versão mais recente e mostra-a no new-update-group, que
 * abre a página dela no GitHub, onde se descarrega o .apk. Sem tokens: só a API pública do GitHub,
 * sem autenticação.
 *
 * Cyclo 2.0: a app já não descarrega nem instala o .apk. Com esse instalador (a autorização
 * REQUEST_INSTALL_PACKAGES, o FileProvider e o código de descarga e instalação) o Play Protect
 * passou a bloquear o Cyclo como "Harmful app blocked", também com o código da Beta 2.2; o mesmo
 * código sem ele foi analisado como "This app looks safe" (2026-11-08, no emulador Cyclo_Release).
 *
 * - Verifica uma vez por arranque a frio ([checkIfDue]): um processo novo verifica ao abrir a app;
 *   voltar do multitarefa ou do segundo plano não é um arranque novo e não volta a verificar. O
 *   resultado fica guardado (com o ETag do GitHub, para pedidos repetidos não gastarem o limite da
 *   API) e serve até à verificação seguinte. Sem rede, fica o último resultado.
 * - Lê a lista de releases (não a "latest", que ignora as prereleases: as versões do Cyclo são
 *   alphas/betas) e escolhe a MAIS RECENTE de todas (não a seguinte à instalada): com a Beta 1.3
 *   instalada e a 1.4 e a 1.5 publicadas, o aviso mostra logo a 1.5.
 * - Dois canais (release-1.1): sem as atualizações beta só contam as estáveis; com elas, também as
 *   prereleases, mas uma estável elegível ganha sempre (ver [ReleaseSelection]). Guardam-se as duas
 *   candidatas (a melhor estável e, com a beta ligada, a melhor beta), por isso desligar a beta passa
 *   logo para a estável (ou esconde o aviso) sem rede. Ligar a beta verifica logo ([setBetaEnabled]).
 * - Dispensar (o X) esconde o aviso durante [DismissMs] (guardado entre sessões); "Undo" na barra de
 *   baixo ([undoDismiss]) volta a mostrá-lo logo, sem nova verificação. Passado o intervalo, se a
 *   atualização continuar disponível, volta a aparecer.
 */
object CycloUpdates {
    private const val Prefs = "updates"
    private const val KeyLastCheck = "last_check"
    private const val KeyEtag = "etag"
    private const val KeyCheckedFor = "checked_for"
    /** O formato antigo (até à release-1.0): uma só release. Apaga-se ao ler. */
    private const val KeyLegacyRelease = "release"
    private const val KeyStable = "release_stable"
    private const val KeyBeta = "release_beta"
    private const val KeyBetaEnabled = "beta_enabled"

    /** Com que valor das atualizações beta se fez a última verificação (o ETag só vale para ele). */
    private const val KeyCheckedBeta = "checked_beta"
    private const val KeyDismissedAt = "dismissed_at"
    private const val KeyDismissedTag = "dismissed_tag"

    /** Quanto tempo o aviso fica escondido depois de o dispensar. */
    const val DismissMs = 12L * 60 * 60 * 1000

    private const val ConnectTimeoutMs = 10_000
    private const val ReadTimeoutMs = 20_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null

    private val _available = MutableStateFlow<AvailableUpdate?>(null)

    /** As candidatas da última verificação: a melhor estável e (com a beta ligada) a melhor beta. */
    @Volatile
    private var stable: AvailableUpdate? = null

    @Volatile
    private var beta: AvailableUpdate? = null

    private val _betaEnabled = MutableStateFlow(false)

    /** "Beta updates" nas definições (desligado por omissão; fica no aparelho, fora das cópias). */
    val betaEnabled: StateFlow<Boolean> = _betaEnabled.asStateFlow()

    /** A tag dispensada com o X (uma beta nova, diferente, volta a aparecer). */
    @Volatile
    private var dismissedTag: String? = null

    /** A atualização disponível (null = nenhuma, ou a instalada já é a mais recente). */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _dismissedUntil = MutableStateFlow(0L)

    /** Até quando o aviso está dispensado (epoch ms). */
    val dismissedUntil: StateFlow<Long> = _dismissedUntil.asStateFlow()

    private var loaded = false

    /** Este processo já verificou (ou está a verificar): um arranque a frio, uma verificação. */
    @Volatile
    private var checkedThisProcess = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    private fun installedName(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    /** Lê o que ficou guardado (a última release vista e quando foi dispensada). */
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        // O .apk que as versões anteriores (até à Beta 2.4) descarregavam para a cache já não serve.
        File(context.cacheDir, "updates").deleteRecursively()
        val dismissedAt = prefs.getLong(KeyDismissedAt, 0L)
        _dismissedUntil.value = if (dismissedAt > 0) dismissedAt + DismissMs else 0L
        dismissedTag = prefs.getString(KeyDismissedTag, null)
        _betaEnabled.value = prefs.getBoolean(KeyBetaEnabled, false)
        // As candidatas guardadas só valem para a versão instalada com que foram escolhidas.
        val sameInstall = prefs.getString(KeyCheckedFor, null) == installedName(context)
        stable = prefs.getString(KeyStable, null)?.takeIf { sameInstall }?.let { decode(it) }
        beta = prefs.getString(KeyBeta, null)?.takeIf { sameInstall && _betaEnabled.value }?.let { decode(it) }
        prefs.edit().remove(KeyLegacyRelease).apply()
        publish()
    }

    /**
     * A primeira abertura depois de uma atualização (Cyclo 2.0): o "Beta updates" fica como estava.
     * As betas 2.0–2.4 recebiam sempre as betas e apagavam a preferência ao ler, por isso quem vem de
     * uma delas fica com ela LIGADA; quem já tinha a preferência guardada (release-1.1) fica com o
     * valor dela; uma instalação nova começa desligada, como na release-1.1. Só decide uma vez: depois
     * de guardada, a preferência é a do utilizador.
     *
     * @param previousVersionCode o versionCode da última abertura antes desta (null = nunca abriu).
     */
    fun keepBetaChannel(context: Context, previousVersionCode: Int?) {
        val app = context.applicationContext
        if (prefs(app).contains(KeyBetaEnabled)) return
        if (ReleaseSelection.cameFromAlwaysBeta(previousVersionCode)) setBetaEnabled(app, true)
    }

    /** O aviso segue as candidatas e a preferência: a estável primeiro, a beta só com ela ligada. */
    @Synchronized
    private fun publish(context: Context? = null) {
        val next = ReleaseSelection.Candidates(stable, beta).pick(_betaEnabled.value)
        // Uma beta nova (outra tag que não a dispensada) aparece mesmo com o aviso dispensado.
        if (context != null && next != null && next.beta && _betaEnabled.value && next.tag != dismissedTag &&
            _dismissedUntil.value > System.currentTimeMillis()
        ) {
            undoDismiss(context)
        }
        _available.value = next
    }

    /**
     * Liga ou desliga as atualizações beta. Ligar (depois de confirmar o action-menu-beta-program)
     * guarda logo e verifica já no GitHub (sem esperar pelo próximo arranque); nunca descarrega nem
     * instala nada. Desligar tira logo a beta do estado (memória e cache): o aviso passa à estável
     * elegível ou desaparece.
     */
    fun setBetaEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        load(app)
        if (_betaEnabled.value == enabled) return
        // Mudar de canal invalida o ETag: um 304 diria "nada mudou" e a beta, apagada ao desligar,
        // não voltava ao ligar outra vez (ligar → desligar → ligar).
        val edit = prefs(app).edit().putBoolean(KeyBetaEnabled, enabled).remove(KeyEtag)
        synchronized(this) {
            _betaEnabled.value = enabled
            if (!enabled) {
                beta = null
                edit.remove(KeyBeta)
            }
            edit.apply()
            publish(app)
        }
        // Uma verificação nova (sem o ETag da anterior, que era do outro canal).
        checkJob?.cancel()
        checkJob = null
        if (enabled) checkIfDue(app, force = true)
    }

    /**
     * Verifica uma vez por processo (o arranque a frio); as chamadas seguintes da mesma sessão (voltar
     * do multitarefa, recriar a atividade) não fazem nada. Não bloqueia: corre em fundo.
     */
    fun checkIfDue(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        load(app)
        if (!force && checkedThisProcess) return
        if (checkJob?.isActive == true) return
        checkedThisProcess = true
        checkJob = scope.launch { check(app) }
    }

    private suspend fun check(context: Context) {
        val prefs = prefs(context)
        val repo = repositoryPath(context) ?: return
        // O canal com que esta verificação começou; se mudar a meio, o resultado já não serve.
        val withBeta = _betaEnabled.value
        val connection = try {
            (URL("https://api.github.com/repos/$repo/releases?per_page=100").openConnection() as HttpURLConnection).apply {
                connectTimeout = ConnectTimeoutMs
                readTimeout = ReadTimeoutMs
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Cyclo-Android")
                // O ETag só vale para a versão instalada e o canal com que se escolheram as candidatas
                // guardadas (outra versão ou ligar a beta pode escolher outra release da mesma lista).
                prefs.getString(KeyEtag, null)
                    ?.takeIf {
                        prefs.getString(KeyCheckedFor, null) == installedName(context) &&
                            prefs.getBoolean(KeyCheckedBeta, false) == withBeta
                    }
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
                    val found = ReleaseSelection.select(body, installedName(context))
                    currentCoroutineContext().ensureActive()
                    synchronized(this) {
                        // A preferência mudou entretanto: este resultado é do outro canal.
                        if (_betaEnabled.value != withBeta) return
                        stable = found.stable
                        beta = found.beta.takeIf { withBeta }
                        prefs.edit()
                            .putLong(KeyLastCheck, System.currentTimeMillis())
                            .putString(KeyEtag, connection.getHeaderField("ETag"))
                            .putString(KeyCheckedFor, installedName(context))
                            .putBoolean(KeyCheckedBeta, withBeta)
                            .apply {
                                stable?.let { putString(KeyStable, encode(it)) } ?: remove(KeyStable)
                                beta?.let { putString(KeyBeta, encode(it)) } ?: remove(KeyBeta)
                            }
                            .apply()
                        publish(context)
                    }
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
        dismissedTag = _available.value?.tag
        _dismissedUntil.value = now + DismissMs
    }

    /** "Undo" depois de dispensar: o aviso volta já, com a release que já se conhecia (sem verificar). */
    fun undoDismiss(context: Context) {
        prefs(context).edit()
            .remove(KeyDismissedAt)
            .remove(KeyDismissedTag)
            .apply()
        dismissedTag = null
        _dismissedUntil.value = 0L
    }

    /** "dono/repositório" a partir do endereço do GitHub do projeto. */
    private fun repositoryPath(context: Context): String? =
        context.getString(R.string.github_url)
            .removePrefix("https://github.com/")
            .trim('/')
            .takeIf { it.count { c -> c == '/' } == 1 }

    private fun encode(update: AvailableUpdate): String = JSONObject()
        .put("tag", update.tag)
        .put("page", update.pageUrl)
        .put("apk", update.apkUrl ?: JSONObject.NULL)
        .put("size", update.apkSize)
        .put("beta", update.beta)
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
            beta = o.optBoolean("beta", false),
        )
    }.getOrNull()

    /** Abre a página da release no navegador, onde se descarrega o .apk (e se leem as notas). */
    fun openReleasePage(context: Context, update: AvailableUpdate): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
