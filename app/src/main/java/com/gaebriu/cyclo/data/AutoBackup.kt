package com.gaebriu.cyclo.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.gaebriu.cyclo.BuildConfig
import com.gaebriu.cyclo.reminder.BedtimeAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * "Automatic backups" (Cyclo 3.1, Settings → Data): UMA cópia automática, sempre o mesmo ficheiro,
 * em Downloads/Cyclo ("cyclo-auto-backup.json"; "cyclo-beta-auto-backup.json" na Cyclo Beta), no
 * formato do "Export my data" ([buildBackupJson]): o "Restore my data" lê-a como qualquer outra cópia.
 *
 * Só nestes momentos (pedidos ou aprovados pelo utilizador; outros só com a aprovação dele):
 * 1. A cada [Interval] (um alarme inexato, sem acordar o telemóvel; ver [schedule]).
 * 2. Quando os dados mudam ([dataChanged], chamado pelo [com.gaebriu.cyclo.reminder.CycloSync] a cada
 *    mudança guardada), com uma pequena espera para juntar mudanças seguidas. Até à Cyclo 3.2.1 eram só
 *    as rotinas: a cafeína, a água, o sono e as emoções só entravam na cópia de 12 em 12 horas (ou
 *    nunca, se o alarme se atrasasse), e uma desinstalação perdia esses registos (Cyclo 3.2.2,
 *    aprovado pelo utilizador). O visto do widget faz a cópia logo ([backUp]), dentro do recetor.
 * 3. Na primeira abertura depois de uma atualização da app ([appUpdated]): os dados como ficaram da
 *    versão anterior, antes de qualquer mudança feita na nova.
 *
 * Sem escritas inúteis: guarda-se o resumo (SHA-256) do conteúdo da última cópia escrita (sem a hora da
 * cópia, que muda sempre); com o mesmo conteúdo e o ficheiro ainda lá, não se escreve nada.
 *
 * Substituição segura: a cópia nova escreve-se inteira num ficheiro à parte, ainda "pendente" (o
 * MediaStore esconde-o), sincroniza-se com o disco e volta a ler-se e validar-se ([parseBackup]); só
 * depois se apaga a antiga e a nova passa a ter o nome final. Uma interrupção a meio deixa sempre uma
 * cópia válida: a antiga (a nova ainda pendente é apagada na vez seguinte) ou a nova (ainda pendente,
 * publicada na vez seguinte).
 *
 * Nota: depois de desinstalar e voltar a instalar, o Android deixa de dar a app como dona do ficheiro
 * antigo; a cópia nova fica ao lado dele (o MediaStore junta-lhe um número ao nome).
 */
object AutoBackup {
    /** De quanto em quanto tempo se faz a cópia automática. */
    val Interval: Duration = Duration.ofHours(12)

    /** Mudanças seguidas nos dados juntam-se numa só cópia, esta espera depois da última. */
    private const val SettleMs = 2_000L

    private const val Prefs = "auto_backup"
    private const val UriKey = "uri"
    private const val PendingKey = "pending_uri"
    private const val HashKey = "hash"
    private const val LastAtKey = "last_at"

    internal const val ActionBackup = "com.gaebriu.cyclo.action.AUTO_BACKUP"
    private const val AlarmRequestCode = 7

    private val fileName = if (BuildConfig.BETA_APP) "cyclo-beta-auto-backup.json" else "cyclo-auto-backup.json"
    private val folder = Environment.DIRECTORY_DOWNLOADS + "/Cyclo/"
    private val collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var pendingChange: Job? = null

    /**
     * Os dados guardados mudaram (rotinas, registos, perfil de sono...). Com o mesmo conteúdo da última
     * cópia (o resumo), não se escreve nada; desligada, não faz nada.
     */
    fun dataChanged(context: Context) {
        val app = context.applicationContext
        pendingChange?.cancel()
        pendingChange = scope.launch {
            delay(SettleMs)
            runCatching { backUp(app, CycloRepository(app).data.first()) }
        }
    }

    /** A primeira abertura depois de uma atualização da app, com os dados como estão (antes de os mudar). */
    fun appUpdated(context: Context, data: CycloData) {
        val app = context.applicationContext
        scope.launch { runCatching { backUp(app, data) } }
    }

    /**
     * Agenda a próxima cópia de 12 em 12 horas (ou cancela-a, desligado). Chamado a cada mudança nos
     * dados ([com.gaebriu.cyclo.reminder.CycloSync]): ligar o interruptor faz logo a primeira cópia.
     */
    fun schedule(context: Context, data: CycloData) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            AlarmRequestCode,
            Intent(context, BedtimeAlarmReceiver::class.java).setAction(ActionBackup),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!data.autoBackup) {
            alarms.cancel(intent)
            return
        }
        val last = prefs(context).getLong(LastAtKey, 0L)
        val at = maxOf(last + Interval.toMillis(), System.currentTimeMillis())
        // RTC (sem acordar o telemóvel): com ele a dormir, faz-se quando acordar.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, intent)
    }

    /** A cópia de 12 em 12 horas (o alarme). */
    suspend fun periodic(context: Context, data: CycloData) {
        if (System.currentTimeMillis() - prefs(context).getLong(LastAtKey, 0L) < Interval.toMillis() - SlackMs) return
        backUp(context, data)
    }

    /** Faz a cópia, se estiver ligada e se o conteúdo mudou (ou o ficheiro desapareceu). Verdadeiro = há cópia válida. */
    suspend fun backUp(context: Context, data: CycloData): Boolean = lock.withLock {
        if (!data.autoBackup) return@withLock false
        val prefs = prefs(context)
        val resolver = context.contentResolver
        recoverPending(context)
        val hash = sha256(buildBackupJson(data, Instant.EPOCH, ""))
        val current = prefs.getString(UriKey, null)?.let(Uri::parse)?.takeIf { exists(context, it) }
        val now = System.currentTimeMillis()
        if (current != null && hash == prefs.getString(HashKey, null)) {
            prefs.edit().putLong(LastAtKey, now).apply()
            return@withLock true
        }
        val json = buildBackupJson(data, Instant.ofEpochMilli(now), appVersionName(context))
        val bytes = json.toByteArray(Charsets.UTF_8)
        // 1. A cópia nova, inteira e pendente (invisível), num ficheiro à parte.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val fresh = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withLock current != null
        prefs.edit().putString(PendingKey, fresh.toString()).commit()
        val written = runCatching {
            resolver.openFileDescriptor(fresh, "w")?.use { fd ->
                FileOutputStream(fd.fileDescriptor).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
            } ?: error("no fd")
            // 2. Volta a ler e a validar o que ficou no disco.
            val back = resolver.openInputStream(fresh)?.use { it.readBytes() } ?: error("unreadable")
            check(back.contentEquals(bytes) && parseBackup(String(back, Charsets.UTF_8)) is BackupParseResult.Valid)
        }.isSuccess
        if (!written) {
            runCatching { resolver.delete(fresh, null, null) }
            prefs.edit().remove(PendingKey).commit()
            return@withLock current != null
        }
        // 3. Só agora a antiga sai e a nova fica com o nome final.
        if (current != null) runCatching { resolver.delete(current, null, null) }
        publish(context, fresh)
        prefs.edit()
            .putString(UriKey, fresh.toString())
            .remove(PendingKey)
            .putString(HashKey, hash)
            .putLong(LastAtKey, now)
            .commit()
        true
    }

    /** Uma cópia nova que ficou pendente (interrompida): publica-a se já estava validada (a antiga já saiu), senão apaga-a. */
    private fun recoverPending(context: Context) {
        val prefs = prefs(context)
        val pending = prefs.getString(PendingKey, null)?.let(Uri::parse) ?: return
        val current = prefs.getString(UriKey, null)?.let(Uri::parse)?.takeIf { exists(context, it) }
        val valid = runCatching {
            context.contentResolver.openInputStream(pending)?.use { parseBackup(String(it.readBytes(), Charsets.UTF_8)) } is BackupParseResult.Valid
        }.getOrDefault(false)
        if (current == null && valid) {
            publish(context, pending)
            prefs.edit().putString(UriKey, pending.toString()).remove(PendingKey).remove(HashKey).commit()
        } else {
            runCatching { context.contentResolver.delete(pending, null, null) }
            prefs.edit().remove(PendingKey).commit()
        }
    }

    private fun publish(context: Context, uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    private fun exists(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
    }.getOrDefault(false) && ContentUris.parseId(uri) > 0

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun appVersionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    /** Um alarme inexato pode chegar um pouco antes do esperado: esta folga ainda conta como "na hora". */
    private const val SlackMs = 10 * 60_000L
}
