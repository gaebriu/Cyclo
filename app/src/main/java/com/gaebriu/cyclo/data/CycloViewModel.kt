package com.gaebriu.cyclo.data

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.reminder.LogSleepReminder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * Mensagem da barra de baixo (undo-action): um texto e, opcionalmente, um botão à direita
 * ("Undo" depois de apagar, "Try again" depois de um erro).
 */
@Immutable
class AppMessage(
    val id: Long,
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val isError: Boolean = false,
)

/** Os dados da app e tudo o que os muda. As escritas que falham mostram o erro com "Try again". */
class CycloViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CycloRepository(application)

    /** null enquanto ainda não se leu nada (o ecrã de abertura espera por isto). */
    val data: StateFlow<CycloData?> = repository.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _message = MutableStateFlow<AppMessage?>(null)
    val message: StateFlow<AppMessage?> = _message.asStateFlow()
    private var nextMessageId = 0L

    private fun string(id: Int) = getApplication<Application>().getString(id)

    fun showMessage(text: String, actionLabel: String? = null, isError: Boolean = false, action: (() -> Unit)? = null) {
        _message.value = AppMessage(++nextMessageId, text, actionLabel, action, isError)
    }

    fun dismissMessage(id: Long) {
        if (_message.value?.id == id) _message.value = null
    }

    /** Corre uma escrita; se falhar, mostra o erro com um botão para tentar outra vez. */
    private fun write(onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure()
                showMessage(
                    text = string(R.string.error_save_failed),
                    actionLabel = string(R.string.try_again),
                    isError = true,
                ) { write(onSuccess, onFailure, block) }
            }
        }
    }

    /** [onboarding]: a rotina do tutorial, com os lembretes do Introduction 6 (null = uma rotina nova). */
    fun addRoutine(draft: RoutineDraft, onboarding: OnboardingReminders?, onSaved: () -> Unit, onFailed: () -> Unit) =
        write(onSaved, onFailed) { repository.addRoutine(draft, onboarding, System.currentTimeMillis()) }

    fun updateRoutine(id: Long, draft: RoutineDraft, onSaved: () -> Unit, onFailed: () -> Unit) =
        write(onSaved, onFailed) { repository.updateRoutine(id, draft, System.currentTimeMillis()) }

    fun setRoutineActive(id: Long, active: Boolean) =
        write { repository.setRoutineActive(id, active, System.currentTimeMillis()) }

    /** Apaga logo e mostra "Routine removed" com "Undo", que a volta a pôr no mesmo sítio. */
    fun deleteRoutine(routine: Routine) {
        val index = data.value?.routines?.indexOfFirst { it.id == routine.id } ?: 0
        write(
            onSuccess = {
                showMessage(string(R.string.routine_removed), string(R.string.undo)) {
                    write { repository.restoreRoutine(routine, index) }
                }
            },
        ) { repository.deleteRoutine(routine.id) }
    }

    /**
     * "+" junta um café com a hora de agora (o [BedtimeEngine] usa-a para saber quanto ainda atua à
     * hora de deitar); "−" tira o mais recente.
     */
    fun changeCaffeine(delta: Int) {
        val now = System.currentTimeMillis()
        write {
            repository.updateDailyLog(LocalDate.now()) {
                val count = (it.caffeine + delta).coerceIn(0, MaxCaffeine)
                val times = when {
                    count > it.caffeine -> it.caffeineTimes + List(count - it.caffeine) { now }
                    count < it.caffeine -> it.caffeineTimes.dropLast(it.caffeine - count)
                    else -> it.caffeineTimes
                }
                it.copy(caffeine = count, caffeineTimes = times.takeLast(count))
            }
        }
    }

    fun resetCaffeine() {
        val log = data.value?.dailyLog?.takeIf { it.date == LocalDate.now() }
        val before = log?.caffeine ?: 0
        val beforeTimes = log?.caffeineTimes.orEmpty()
        write(
            onSuccess = {
                showMessage(string(R.string.feed_caffeine_reset_done), string(R.string.undo)) {
                    write { repository.updateDailyLog(LocalDate.now()) { it.copy(caffeine = before, caffeineTimes = beforeTimes) } }
                }
            },
        ) { repository.updateDailyLog(LocalDate.now()) { it.copy(caffeine = 0, caffeineTimes = emptyList()) } }
    }

    /** Marcar um fator guarda também a hora (ver [BedtimeEngine]); desmarcar apaga-a. */
    fun toggleFactor(factor: SleepFactor) {
        val now = System.currentTimeMillis()
        write {
            repository.updateDailyLog(LocalDate.now()) {
                if (factor in it.factors) {
                    it.copy(factors = it.factors - factor, factorTimes = it.factorTimes - factor)
                } else {
                    it.copy(factors = it.factors + factor, factorTimes = it.factorTimes + (factor to now))
                }
            }
        }
    }

    fun setMood(mood: Float) = write {
        repository.updateDailyLog(LocalDate.now()) { it.copy(mood = mood.coerceIn(0f, 1f)) }
    }

    // --- Tracking ---------------------------------------------------------------------------

    /**
     * A app está à vista: se já passou a hora de acordar de hoje (ver [countsAsOpened]), o dia fica
     * marcado como "aberto" (a cafeína e o humor dele contam na Tracking). Só escreve uma vez por dia.
     * Chamado pela atividade (ao voltar à frente e a cada minuto à vista), nunca por widgets ou alarmes.
     */
    fun markOpened() {
        val current = data.value ?: return
        if (!current.onboarded) return
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        if (current.dailyLog?.takeIf { it.date == today }?.opened == true) return
        if (!countsAsOpened(current.routines, now)) return
        write { repository.updateDailyLog(today) { if (it.opened) it else it.copy(opened = true) } }
    }

    /**
     * "Done" do action-menu-log-sleep: o sono de hoje fica registado (uma vez por dia). A notificação
     * "How well did you sleep last night?", se ainda estiver à vista, sai (já não faz falta).
     */
    fun logSleep(quality: SleepQuality) = write(onSuccess = { LogSleepReminder.cancelNotification(getApplication()) }) {
        repository.updateDailyLog(LocalDate.now()) { if (it.sleep != null) it else it.copy(sleep = quality, opened = true) }
    }

    /**
     * Pedido para abrir a Tracking com o action-menu-log-sleep (o toque na notificação do lembrete
     * "Log sleep"). A [com.gaebriu.cyclo.ui.CycloApp] consome-o quando o ecrã principal está pronto.
     */
    private val _logSleepRequest = MutableStateFlow(false)
    val logSleepRequest: StateFlow<Boolean> = _logSleepRequest.asStateFlow()

    fun requestLogSleep() {
        _logSleepRequest.value = true
    }

    fun consumeLogSleepRequest() {
        _logSleepRequest.value = false
    }

    private val _sleepPromptDismissed = MutableStateFlow(false)

    /**
     * O X do sleep-evaluation-prompt esconde-o até a app voltar a abrir. Vive no ViewModel: sobrevive
     * a rodar o ecrã, mas não a fechar a app (o ViewModel acaba com a atividade).
     */
    val sleepPromptDismissed: StateFlow<Boolean> = _sleepPromptDismissed.asStateFlow()

    fun dismissSleepPrompt() {
        _sleepPromptDismissed.value = true
    }

    /** Nova ordem dos cartões da Tracking. */
    fun setTrackingOrder(order: List<TrackingSection>) = write { repository.setTrackingOrder(order) }

    fun setUse24Hour(use24Hour: Boolean) = write { repository.setUse24Hour(use24Hour) }

    /** "Advanced textures" (definições, Performance). */
    fun setAdvancedTextures(on: Boolean) = write { repository.setAdvancedTextures(on) }

    /** Nova ordem das rotinas (arrastar nas rotinas guardadas). */
    fun reorderRoutines(ids: List<Long>) = write { repository.reorderRoutines(ids) }

    /** Nova ordem dos blocos do Feed (arrastar no Feed). */
    fun setFeedOrder(order: List<FeedSection>) = write { repository.setFeedOrder(order) }

    /** Guarda o versionCode desta abertura (o "acabou de ser atualizada" só vale uma vez). */
    fun markVersionSeen(versionCode: Int) = write { repository.setLastRunVersion(versionCode) }

    fun setAge(age: Int) {
        if (age in AgeRange) write { repository.setAge(age) }
    }

    fun setChronotype(chronotype: Chronotype) = write { repository.setChronotype(chronotype) }

    fun setFallAsleep(time: FallAsleepTime) = write { repository.setFallAsleep(time) }

    /** O interruptor "Bedtime". Ligar só chega aqui com a autorização de notificações dada. */
    fun setBedtimeReminder(on: Boolean) = write { repository.setBedtimeReminder(on) }

    /** O interruptor "Log sleep" (Beta 2.3). Como o "Bedtime": ligar só chega aqui com autorização. */
    fun setLogSleepReminder(on: Boolean) = write { repository.setLogSleepReminder(on) }

    /** Autorização de notificações recusada: o interruptor fica desligado. */
    fun notificationsDenied() = showMessage(string(R.string.settings_notifications_denied))

    /** Rotina a abrir no editor, pedida pelo lápis do widget (a [com.gaebriu.cyclo.ui.CycloApp] consome-a). */
    private val _editRequest = MutableStateFlow<Long?>(null)
    val editRequest: StateFlow<Long?> = _editRequest.asStateFlow()

    fun requestEdit(routineId: Long) {
        _editRequest.value = routineId
    }

    fun consumeEditRequest() {
        _editRequest.value = null
    }

    /** O lançador não deixa fixar o widget a partir da app. */
    fun widgetPinUnsupported() = showMessage(string(R.string.widget_pin_unsupported))

    /** O X do new-update-group: "Update dismissed" com "Undo", que o volta a mostrar logo. */
    fun updateDismissed(undo: () -> Unit) =
        showMessage(string(R.string.update_dismissed), actionLabel = string(R.string.undo), action = undo)

    /** Não há nada para abrir a página do GitHub. */
    fun gitHubOpenFailed() = showMessage(string(R.string.github_open_failed), isError = true)

    // --- Exportar e repor -------------------------------------------------------------------

    /** Pedidos para abrir o seletor de ficheiros do sistema (o "Try again" das mensagens). */
    private val _pickerRequests = MutableSharedFlow<FilePicker>(extraBufferCapacity = 1)
    val pickerRequests: SharedFlow<FilePicker> = _pickerRequests.asSharedFlow()

    /** Uma cópia lida e válida, à espera da confirmação (action-menu-big). */
    private val _pendingRestore = MutableStateFlow<BackupContents?>(null)
    val pendingRestore: StateFlow<BackupContents?> = _pendingRestore.asStateFlow()

    /** A reposição está a decorrer (loading-data). */
    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private fun retry(picker: FilePicker): () -> Unit = { _pickerRequests.tryEmit(picker) }

    /** Nome sugerido para a cópia: cyclo-backup-AAAA-MM-DD.json. */
    fun backupFileName(): String = "cyclo-backup-${LocalDate.now()}.json"

    /**
     * Escreve a cópia no ficheiro escolhido no seletor ([uri] null = cancelado). Só diz que correu
     * bem depois de o ficheiro estar escrito e fechado.
     */
    fun exportTo(uri: Uri?) {
        if (uri == null) {
            showMessage(string(R.string.export_cancelled), string(R.string.try_again), isError = true, action = retry(FilePicker.Export))
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val current = repository.data.first()
                    val json = buildBackupJson(current, Instant.now(), appVersionName(app))
                    // "wt": apaga o que lá estivesse (com "w" alguns fornecedores não cortam o ficheiro).
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Não deixa um ficheiro vazio ou a meio para trás.
                    runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                    false
                }
            }
            if (ok) {
                showMessage(string(R.string.export_done))
            } else {
                showMessage(string(R.string.export_failed), string(R.string.try_again), isError = true, action = retry(FilePicker.Export))
            }
        }
    }

    /**
     * Lê e valida a cópia escolhida ([uri] null = nada escolhido). Se for válida, fica à espera da
     * confirmação ([pendingRestore]); nada muda nos dados até lá.
     */
    fun readBackup(uri: Uri?) {
        if (uri == null) {
            showMessage(string(R.string.restore_no_file), string(R.string.try_again), isError = true, action = retry(FilePicker.Restore))
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytes(MaxBackupBytes + 1)
                        if (bytes.size > MaxBackupBytes) null else String(bytes, Charsets.UTF_8)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            if (text == null) {
                showMessage(string(R.string.restore_unreadable), string(R.string.try_again), isError = true, action = retry(FilePicker.Restore))
                return@launch
            }
            when (val result = withContext(Dispatchers.Default) { parseBackup(text.removePrefix("﻿")) }) {
                is BackupParseResult.Valid -> _pendingRestore.value = result.contents
                is BackupParseResult.Rejected -> showMessage(
                    string(if (result.problem == BackupProblem.TooNew) R.string.restore_too_new else R.string.restore_invalid),
                    string(R.string.try_again),
                    isError = true,
                    action = retry(FilePicker.Restore),
                )
            }
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    /**
     * Confirmado: repõe tudo numa só escrita, com o loading-data à vista pelo menos [minShownMs]
     * (a animação inteira dele, que depende do texto; ver ui/settings/LoadingDataOverlay.kt).
     */
    fun confirmRestore(minShownMs: Long = MinRestoreMs) {
        val contents = _pendingRestore.value ?: return
        _pendingRestore.value = null
        _restoring.value = true
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val ok = try {
                repository.restore(contents)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            val left = minShownMs - (SystemClock.elapsedRealtime() - started)
            if (left > 0) delay(left)
            _restoring.value = false
            if (ok) {
                showMessage(string(R.string.restore_done))
            } else {
                showMessage(string(R.string.restore_failed), string(R.string.try_again), isError = true, action = retry(FilePicker.Restore))
            }
        }
    }

    companion object {
        const val MaxCaffeine = 99

        /** Mínimo de reserva para o loading-data (a app passa o tempo real da animação). */
        const val MinRestoreMs = 3020L
    }
}

/** Que seletor de ficheiros abrir. */
enum class FilePicker { Export, Restore }

private fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
