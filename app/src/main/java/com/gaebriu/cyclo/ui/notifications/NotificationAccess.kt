package com.gaebriu.cyclo.ui.notifications

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gaebriu.cyclo.reminder.BedtimeReminder
import com.gaebriu.cyclo.reminder.LogSleepReminder

/**
 * A autorização de notificações dos lembretes ("Bedtime" e "Log sleep"), partilhada pelas
 * definições e pelo Introduction 6 (Beta 2.3).
 *
 * - [isAllowed]: a autorização POST_NOTIFICATIONS dada, as notificações da app ligadas no sistema e
 *   o canal do lembrete não desligado. Um interruptor só se mostra ligado com isto (nunca ligado
 *   sem as notificações poderem mesmo chegar). Volta a ver-se ao regressar à app (pode ter mudado
 *   nas definições do sistema) e quando o pedido responde.
 * - [request]: já autorizado, responde logo que sim; sem a autorização, abre o pedido nativo do
 *   Android sempre que o sistema o deixa mostrar (depois de duas recusas, o próprio Android
 *   responde "não" sem mostrar nada, e o interruptor fica desligado); autorizado mas com as
 *   notificações da app ou do canal desligadas no sistema, o pedido não existe e responde que não.
 *   Quem pede decide o que mostrar com o "não" (as definições mostram a barra; o Introduction 6,
 *   nada).
 */
@Stable
class NotificationAccess internal constructor(private val context: Context) {
    /** Sobe quando a autorização pode ter mudado: quem lê [isAllowed] volta a ler. */
    private var version by mutableIntStateOf(0)

    internal var launch: (() -> Boolean)? = null
    private var pending: ((Boolean) -> Unit)? = null

    /** Se as notificações do canal [channelId] podem chegar (ver a classe). */
    fun isAllowed(channelId: String): Boolean {
        @Suppress("UNUSED_EXPRESSION") version
        return BedtimeReminder.isPermitted(context) && BedtimeReminder.isChannelEnabled(context, channelId)
    }

    /** Se a autorização em si (POST_NOTIFICATIONS) já foi dada: sem ela, [request] abre o pedido. */
    fun isPermissionGranted(): Boolean {
        @Suppress("UNUSED_EXPRESSION") version
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Pede o que faltar para o canal [channelId]; [onResult] diz se as notificações já podem chegar. */
    fun request(channelId: String, onResult: (Boolean) -> Unit) {
        if (isAllowed(channelId)) {
            onResult(true)
            return
        }
        if (isPermissionGranted()) {
            // Autorizado, mas desligado nas definições do sistema: o pedido nativo não aparece.
            onResult(false)
            return
        }
        // Só um pedido de cada vez (dois toques seguidos não abrem dois pedidos).
        if (pending != null) return
        pending = { granted -> onResult(granted && isAllowed(channelId)) }
        if (launch?.invoke() != true) {
            pending = null
            onResult(false)
        }
    }

    internal fun onPermissionResult(granted: Boolean) {
        version++
        val callback = pending
        pending = null
        callback?.invoke(granted)
    }

    /** O estado de tudo o que conta para os lembretes (para ver se mudou ao voltar à app). */
    internal fun snapshot(): Int {
        var bits = 0
        if (BedtimeReminder.isPermitted(context)) bits = bits or 1
        if (BedtimeReminder.isChannelEnabled(context, BedtimeReminder.ChannelId)) bits = bits or 2
        if (BedtimeReminder.isChannelEnabled(context, LogSleepReminder.ChannelId)) bits = bits or 4
        return bits
    }

    internal fun refresh() {
        version++
    }
}

/**
 * A [NotificationAccess] da app. [onChanged] corre quando, ao voltar à app ou depois do pedido, a
 * autorização mudou (para reagendar os lembretes).
 */
@Composable
fun rememberNotificationAccess(onChanged: () -> Unit): NotificationAccess {
    val context = LocalContext.current
    val access = remember(context) { NotificationAccess(context.applicationContext) }
    val latestOnChanged by rememberUpdatedState(onChanged)
    val last = remember(access) { intArrayOf(access.snapshot()) }
    fun checkChanged() {
        val now = access.snapshot()
        if (now != last[0]) {
            last[0] = now
            latestOnChanged()
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Antes do ON_RESUME: o reagendamento vem já daqui.
        checkChanged()
        access.onPermissionResult(granted)
    }
    access.launch = {
        try {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, access) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access.refresh()
                checkChanged()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return access
}
