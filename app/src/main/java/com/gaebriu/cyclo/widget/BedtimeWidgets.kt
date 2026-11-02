package com.gaebriu.cyclo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.gaebriu.cyclo.MainActivity
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.BedtimeEngine
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.CycloRepository
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.reminder.BedtimeReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Widgets do ecrã principal: um por rotina, com o nome, a hora de acordar e a hora recomendada para
 * ir para a cama ([BedtimeEngine]).
 *
 * - Fixa-se pelo "Add as a widget" das rotinas guardadas ([requestPin]); a rotina fica ligada ao id
 *   do widget quando o lançador confirma ([WidgetPinnedReceiver]).
 * - Um widget posto pela lista de widgets do lançador (sem rotina), ou cuja rotina foi apagada,
 *   mostra a próxima rotina a acordar.
 * - Quando a hora muda, a nova entra por baixo e a antiga sai por cima (ViewFlipper); o
 *   CycloSync só chama isto depois de as mudanças assentarem.
 */
object BedtimeWidgets {
    private const val Prefs = "bedtime_widgets"
    private fun routineKey(id: Int) = "routine_$id"
    private fun shownKey(id: Int) = "shown_$id"
    private fun childKey(id: Int) = "child_$id"

    internal const val ExtraRoutineId = "routine_id"
    private const val ActionPinned = "com.gaebriu.cyclo.action.WIDGET_PINNED"

    /** Se o lançador deixa fixar widgets a partir da app. */
    fun canPin(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Pede ao lançador para fixar o widget da rotina [routineId]. false se não for possível. */
    fun requestPin(context: Context, routineId: Long): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = PendingIntent.getBroadcast(
            context,
            routineId.toInt(),
            Intent(context, WidgetPinnedReceiver::class.java)
                .setAction(ActionPinned)
                .putExtra(ExtraRoutineId, routineId),
            // MUTABLE: o lançador junta o id do widget novo ao Intent.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return manager.requestPinAppWidget(ComponentName(context, BedtimeWidgetProvider::class.java), Bundle(), callback)
    }

    internal fun bind(context: Context, appWidgetId: Int, routineId: Long) {
        prefs(context).edit().putLong(routineKey(appWidgetId), routineId).apply()
    }

    internal fun forget(context: Context, appWidgetIds: IntArray) {
        val edit = prefs(context).edit()
        appWidgetIds.forEach { id ->
            edit.remove(routineKey(id))
            for (line in listOf(BedtimeLine, WindDownLine)) {
                edit.remove(shownKey(id) + line.key).remove(childKey(id) + line.key)
            }
        }
        edit.apply()
    }

    fun updateAll(context: Context, data: CycloData, animate: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, BedtimeWidgetProvider::class.java))
        ids.forEach { update(context, manager, it, data, animate) }
    }

    private fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int, data: CycloData, animate: Boolean) {
        val prefs = prefs(context)
        val now = LocalDateTime.now()
        val formatter = BedtimeReminder.timeFormatter(context, data.use24Hour)
        val boundId = prefs.getLong(routineKey(appWidgetId), -1L)
        val routine = data.routines.firstOrNull { it.id == boundId }
            ?: BedtimeEngine.nextWake(data.routines, now)?.first

        val name: String
        val wakeText: String
        val bedText: String
        // O que a cafeína, o exercício, a refeição, a sesta e as emoções mudam: o início do wind-down.
        val windDownText: String
        if (routine == null) {
            name = context.getString(R.string.app_name)
            wakeText = context.getString(R.string.widget_no_routine)
            bedText = context.getString(R.string.widget_no_bedtime)
            windDownText = ""
        } else {
            name = routine.name
            wakeText = LocalTime.of(routine.wakeHour, routine.wakeMinute).format(formatter)
            val wake = routine.nextWake(now)
            if (wake == null) {
                bedText = context.getString(if (routine.active) R.string.widget_no_bedtime else R.string.widget_routine_off)
                windDownText = ""
            } else {
                val plan = BedtimeEngine.plan(wake, now, data.age, data.chronotype, data.fallAsleep, data.dailyLog)
                if (plan.shortfall) {
                    bedText = context.getString(R.string.widget_bed_now)
                    windDownText = context.getString(R.string.widget_wind_down_now)
                } else {
                    bedText = context.getString(R.string.widget_bed_at, plan.bedtime.format(formatter))
                    windDownText = if (plan.windDownStart.isAfter(now)) {
                        context.getString(R.string.widget_wind_down_at, plan.windDownStart.format(formatter))
                    } else {
                        context.getString(R.string.widget_wind_down_now)
                    }
                }
            }
        }

        val views = RemoteViews(context.packageName, R.layout.widget_bedtime)
        views.setTextViewText(R.id.widget_name, name)
        views.setTextViewText(R.id.widget_wake, wakeText)
        views.setContentDescription(
            R.id.widget_root,
            context.getString(R.string.widget_content_description, name, wakeText, "$bedText. $windDownText"),
        )
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        val edit = prefs.edit()
        val bedChanged = setFlipper(views, prefs, edit, appWidgetId, BedtimeLine, bedText, animate)
        val windChanged = setFlipper(views, prefs, edit, appWidgetId, WindDownLine, windDownText, animate)
        // A atualização parcial reaplica sobre a vista que está no ecrã (não a volta a criar), por
        // isso o ViewFlipper anima a troca; a completa serve para o resto.
        if (animate && (bedChanged || windChanged)) {
            manager.partiallyUpdateAppWidget(appWidgetId, views)
        } else {
            manager.updateAppWidget(appWidgetId, views)
        }
        edit.apply()
    }

    /** Uma linha com ViewFlipper: os ids dos dois textos e do flipper. */
    private class FlipLine(val key: String, val flipper: Int, val a: Int, val b: Int)

    private val BedtimeLine = FlipLine("bed", R.id.widget_bedtime_flipper, R.id.widget_bedtime_a, R.id.widget_bedtime_b)
    private val WindDownLine = FlipLine("wind", R.id.widget_winddown_flipper, R.id.widget_winddown_a, R.id.widget_winddown_b)

    /**
     * Põe [text] na linha. Se mudou e [animate], o texto antigo fica no filho à vista e o novo vai
     * para o outro, que entra com a animação do ViewFlipper. Devolve se mudou.
     */
    private fun setFlipper(
        views: RemoteViews,
        prefs: android.content.SharedPreferences,
        edit: android.content.SharedPreferences.Editor,
        appWidgetId: Int,
        line: FlipLine,
        text: String,
        animate: Boolean,
    ): Boolean {
        val shown = prefs.getString(shownKey(appWidgetId) + line.key, null)
        var child = prefs.getInt(childKey(appWidgetId) + line.key, 0)
        val changed = shown != null && shown != text
        fun id(c: Int) = if (c == 0) line.a else line.b
        if (changed && animate) {
            val next = 1 - child
            views.setTextViewText(id(child), shown)
            views.setTextViewText(id(next), text)
            views.setDisplayedChild(line.flipper, next)
            child = next
        } else {
            views.setTextViewText(id(child), text)
            views.setDisplayedChild(line.flipper, child)
        }
        edit.putString(shownKey(appWidgetId) + line.key, text).putInt(childKey(appWidgetId) + line.key, child)
        return changed
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    /** Corre [block] com os dados atuais, fora da thread principal (para os receivers). */
    internal fun withData(receiver: BroadcastReceiver, context: Context, block: (CycloData) -> Unit) {
        val app = context.applicationContext
        val pending = receiver.goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                block(CycloRepository(app).data.first())
            } finally {
                pending.finish()
            }
        }
    }
}

/** O widget no lançador: desenha-se com os dados atuais quando aparece ou muda de tamanho. */
class BedtimeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        BedtimeWidgets.withData(this, context) { BedtimeWidgets.updateAll(context, it, animate = false) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        BedtimeWidgets.forget(context, appWidgetIds)
    }
}

/** O lançador confirmou o widget fixado a partir de uma rotina: liga-o a ela e desenha-o. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val routineId = intent.getLongExtra(BedtimeWidgets.ExtraRoutineId, -1L)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || routineId < 0) return
        BedtimeWidgets.bind(context, appWidgetId, routineId)
        BedtimeWidgets.withData(this, context) { BedtimeWidgets.updateAll(context, it, animate = false) }
    }
}
