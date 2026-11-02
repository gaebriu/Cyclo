package com.gaebriu.cyclo.data

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gaebriu.cyclo.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    fun addRoutine(draft: RoutineDraft, completeOnboarding: Boolean, onSaved: () -> Unit, onFailed: () -> Unit) =
        write(onSaved, onFailed) { repository.addRoutine(draft, completeOnboarding, System.currentTimeMillis()) }

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

    fun changeCaffeine(delta: Int) = write {
        repository.updateDailyLog(LocalDate.now()) { it.copy(caffeine = (it.caffeine + delta).coerceIn(0, MaxCaffeine)) }
    }

    fun resetCaffeine() {
        val before = data.value?.dailyLog?.takeIf { it.date == LocalDate.now() }?.caffeine ?: 0
        write(
            onSuccess = {
                showMessage(string(R.string.feed_caffeine_reset_done), string(R.string.undo)) {
                    write { repository.updateDailyLog(LocalDate.now()) { it.copy(caffeine = before) } }
                }
            },
        ) { repository.updateDailyLog(LocalDate.now()) { it.copy(caffeine = 0) } }
    }

    fun toggleFactor(factor: SleepFactor) = write {
        repository.updateDailyLog(LocalDate.now()) {
            it.copy(factors = if (factor in it.factors) it.factors - factor else it.factors + factor)
        }
    }

    fun setMood(mood: Float) = write {
        repository.updateDailyLog(LocalDate.now()) { it.copy(mood = mood.coerceIn(0f, 1f)) }
    }

    fun setUse24Hour(use24Hour: Boolean) = write { repository.setUse24Hour(use24Hour) }

    companion object {
        const val MaxCaffeine = 99
    }
}
