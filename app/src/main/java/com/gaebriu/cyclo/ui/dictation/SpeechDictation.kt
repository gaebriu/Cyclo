package com.gaebriu.cyclo.ui.dictation

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Ditado por voz com o reconhecimento de voz do Google (SpeechRecognizer), em segundo plano:
 * a interface nativa fica escondida e quem mostra o estado é a UI da app.
 *
 * - A permissão do microfone pede-se com o diálogo nativo do Android.
 * - Se a permissão for recusada, o botão de ditado desaparece ([isMicVisible]) até ser ativada
 *   nas definições do sistema (volta sozinho quando a app regressa ao primeiro plano).
 * - O idioma é o do telemóvel.
 */
@Stable
class SpeechDictationState internal constructor(
    private val context: Context,
    private val currentOnResult: () -> (String) -> Unit,
) {
    var isListening by mutableStateOf(false)
        private set

    private var permissionGranted by mutableStateOf(hasPermission())
    private var permissionDenied by mutableStateOf(false)
    private val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
    private var recognizer: SpeechRecognizer? = null

    internal var launchPermissionRequest: () -> Unit = {}

    /** O botão de ditado só aparece se o aparelho tiver reconhecimento de voz e a permissão não tiver sido recusada. */
    val isMicVisible: Boolean
        get() = recognitionAvailable && (permissionGranted || !permissionDenied)

    /** Toque no microfone: pede a permissão (se faltar) e começa a ouvir. */
    fun start() {
        if (!recognitionAvailable || isListening) return
        if (!hasPermission()) {
            launchPermissionRequest()
            return
        }
        beginListening()
    }

    /** X ou "voltar ao teclado": deixa de ouvir sem usar o que foi dito. */
    fun cancel() {
        recognizer?.cancel()
        finish()
    }

    internal fun onPermissionResult(granted: Boolean) {
        permissionGranted = granted
        if (granted) {
            permissionDenied = false
            beginListening()
        } else {
            permissionDenied = true
        }
    }

    /** Reavalia a permissão (por exemplo, depois de o utilizador a ativar nas definições). */
    internal fun refreshPermission() {
        permissionGranted = hasPermission()
        if (permissionGranted) permissionDenied = false
    }

    internal fun release() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    private fun beginListening() {
        release()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    finish()
                    if (!text.isNullOrBlank()) currentOnResult()(text)
                }

                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        permissionGranted = hasPermission()
                        if (!permissionGranted) permissionDenied = true
                    }
                    finish()
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        isListening = true
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            },
        )
    }

    private fun finish() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * @param onResult chamado com o texto reconhecido, quando o utilizador acaba de falar.
 */
@Composable
fun rememberSpeechDictation(onResult: (String) -> Unit): SpeechDictationState {
    val context = LocalContext.current
    val latestOnResult by rememberUpdatedState(onResult)
    val state = remember(context) { SpeechDictationState(context) { latestOnResult } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> state.onPermissionResult(granted) }
    state.launchPermissionRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.refreshPermission()
                Lifecycle.Event.ON_PAUSE -> state.cancel()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            state.release()
        }
    }
    return state
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}
