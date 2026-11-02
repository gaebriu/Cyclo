package com.gaebriu.cyclo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.gaebriu.cyclo.ui.routine.RepeatOption
import com.gaebriu.cyclo.ui.routine.Weekday
import com.gaebriu.cyclo.ui.routine.hasDay
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
 * Widgets do ecrã principal: um por rotina, uma versão compacta do cartão dela nas rotinas guardadas
 * (nome, hora de acordar, repetição, editar e ligar/desligar, véu quando desligada), com a hora
 * recomendada para ir para a cama e para começar a abrandar ([BedtimeEngine]), na fonte do sistema.
 *
 * - Há uma versão para cada tamanho (pequena, pequena alta, larga sem e com botões, grande; ver
 *   [WidgetLayout]); vão todas juntas e o lançador usa a que cabe no tamanho atual.
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

    const val ExtraRoutineId = "routine_id"
    private const val ActionPinned = "com.gaebriu.cyclo.action.WIDGET_PINNED"

    /** O lápis do widget: abre a app no editor da rotina [ExtraRoutineId]. */
    const val ActionEdit = "com.gaebriu.cyclo.action.EDIT_ROUTINE"
    internal const val ActionToggle = "com.gaebriu.cyclo.action.TOGGLE_ROUTINE"

    /**
     * A linha da repetição, como no cartão: "Show just this once", ou "S M T W T F S" com os dias
     * da rotina a #FCFCFE e os outros a #A3A2A7 a 39 %.
     */
    private fun repeatSummary(context: Context, routine: Routine): CharSequence {
        if (routine.repeat == RepeatOption.JustOnce) return context.getString(R.string.repeat_just_once)
        val mask = routine.repeatMask
        val text = SpannableStringBuilder()
        Weekday.entries.forEachIndexed { index, day ->
            if (index > 0) text.append(" ")
            val start = text.length
            text.append(context.getString(day.shortLabel))
            val color = if (mask.hasDay(day)) DayOn else DayOff
            text.setSpan(ForegroundColorSpan(color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private const val DayOn = 0xFFFCFCFE.toInt()

    /** #A3A2A7 a 39 % (0x63). */
    private const val DayOff = 0x63A3A2A7

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
                for (key in listOf(line.keyFor(hero = false), line.keyFor(hero = true))) {
                    edit.remove(shownKey(id) + key).remove(childKey(id) + key)
                }
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
        val repeatText: CharSequence
        // A cafeína, os fatores, as emoções e o cronótipo mudam as duas: mais tempo para adormecer
        // antecipa a hora de deitar, e mais preparação antecipa o wind-down.
        // Cada uma em duas formas: a frase inteira ("In bed by 22:45", nas linhas do widget largo)
        // e só a hora (a hora grande e a lua dos widgets pequeno e grande), ou a frase que a substitui.
        val bedText: String
        val bedTime: String?
        val windDownText: String
        val windDownTime: String
        if (routine == null) {
            name = context.getString(R.string.app_name)
            wakeText = context.getString(R.string.widget_no_routine)
            repeatText = ""
            bedText = context.getString(R.string.widget_no_bedtime)
            bedTime = null
            windDownText = ""
            windDownTime = ""
        } else {
            name = routine.name
            wakeText = LocalTime.of(routine.wakeHour, routine.wakeMinute).format(formatter)
            repeatText = repeatSummary(context, routine)
            val wake = routine.nextWake(now)
            if (wake == null) {
                bedText = context.getString(if (routine.active) R.string.widget_no_bedtime else R.string.widget_routine_off)
                bedTime = null
                windDownText = ""
                windDownTime = ""
            } else {
                val plan = BedtimeEngine.plan(wake, now, data.age, data.chronotype, data.fallAsleep, data.dailyLog)
                if (plan.shortfall) {
                    bedText = context.getString(R.string.widget_bed_now)
                    bedTime = null
                    windDownText = context.getString(R.string.widget_wind_down_now)
                    windDownTime = context.getString(R.string.widget_wind_down_now_short)
                } else {
                    bedTime = plan.bedtime.format(formatter)
                    bedText = context.getString(R.string.widget_bed_at, bedTime)
                    if (plan.windDownStart.isAfter(now)) {
                        windDownTime = plan.windDownStart.format(formatter)
                        windDownText = context.getString(R.string.widget_wind_down_at, windDownTime)
                    } else {
                        windDownText = context.getString(R.string.widget_wind_down_now)
                        windDownTime = context.getString(R.string.widget_wind_down_now_short)
                    }
                }
            }
        }

        val edit = prefs.edit()
        // As duas formas de cada linha, com o seu estado do ViewFlipper (calculado uma vez e
        // aplicado a todas as versões dessa forma).
        val heroLines = listOf(
            flip(prefs, edit, appWidgetId, BedtimeLine, hero = true, text = bedTime ?: bedText, animate = animate),
            flip(prefs, edit, appWidgetId, WindDownLine, hero = true, text = windDownTime, animate = animate),
        )
        val fullLines = listOf(
            flip(prefs, edit, appWidgetId, BedtimeLine, hero = false, text = bedText, animate = animate),
            flip(prefs, edit, appWidgetId, WindDownLine, hero = false, text = windDownText, animate = animate),
        )
        edit.apply()

        // Uma versão por tamanho: o lançador escolhe a que cabe e está mais perto do tamanho do
        // widget, em cada orientação (e ao rodar ou dobrar o aparelho).
        fun build(layout: WidgetLayout): RemoteViews {
            val views = RemoteViews(context.packageName, layout.res)
            views.setTextViewText(R.id.widget_name, name)
            views.setTextViewText(R.id.widget_wake, wakeText)
            views.setTextViewText(R.id.widget_repeat, repeatText)
            // O pequeno só mostra a hora de acordar e a repetição quando é alto.
            views.setViewVisibility(R.id.widget_details, if (layout.details) View.VISIBLE else View.GONE)
            // "In bed by" por cima da hora grande só quando há uma hora (não por cima de "Time for bed").
            views.setViewVisibility(R.id.widget_bed_label_row, if (bedTime != null) View.VISIBLE else View.GONE)
            // O largo sem botões (estreito) usa a largura toda.
            if (layout == WidgetLayout.WideNoButtons) {
                views.setViewLayoutMargin(R.id.widget_text, RemoteViews.MARGIN_END, 0f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            }
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

            // Os botões do cartão: editar abre o editor da rotina na app; o visto liga e desliga-a.
            val active = routine?.active == true
            views.setViewVisibility(R.id.widget_buttons, if (routine == null || !layout.buttons) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_veil, if (routine != null && !active) View.VISIBLE else View.GONE)
            if (layout.buttons) {
                views.setInt(R.id.widget_toggle_icon, "setBackgroundResource", if (active) R.drawable.widget_toggle_on else R.drawable.widget_toggle_off)
                // Desligado, o visto fica a 24 %.
                views.setInt(R.id.widget_toggle_icon, "setImageAlpha", if (active) 255 else 61)
            }
            if (routine != null && layout.buttons) {
                views.setContentDescription(R.id.widget_edit, context.getString(R.string.saved_edit, routine.name))
                views.setContentDescription(
                    R.id.widget_toggle,
                    context.getString(R.string.saved_toggle, routine.name) + ", " +
                        context.getString(if (active) R.string.saved_state_on else R.string.saved_state_off),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_edit,
                    PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        Intent(context, MainActivity::class.java)
                            .setAction(ActionEdit)
                            .putExtra(ExtraRoutineId, routine.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_toggle,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        Intent(context, WidgetActionReceiver::class.java)
                            .setAction(ActionToggle)
                            .putExtra(ExtraRoutineId, routine.id),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            (if (layout.hero) heroLines else fullLines).forEach { it.applyTo(views) }
            return views
        }

        // Sempre a atualização completa: com o mesmo layout, o lançador reaplica-a sobre a vista
        // que está no ecrã (RemoteViews.canRecycleView), por isso o ViewFlipper anima a troca.
        // (A parcial não serviria: só junta as ações da raiz, não chega às versões por tamanho.)
        manager.updateAppWidget(
            appWidgetId,
            RemoteViews(WidgetLayout.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to build(it) }),
        )
    }

    /**
     * As versões do widget, da mais pequena para a maior (a cápsula de uma só fila não existe: o
     * widget não desce abaixo de duas filas). [minWidthDp] x [minHeightDp] é o tamanho mais pequeno
     * em que cada uma cabe; o lançador escolhe a que cabe e está mais perto do tamanho do widget.
     *
     * @param hero a hora de ir para a cama em grande, e só as horas nas duas linhas.
     */
    private enum class WidgetLayout(
        val res: Int,
        val minWidthDp: Float,
        val minHeightDp: Float,
        val hero: Boolean,
        val details: Boolean,
        val buttons: Boolean,
    ) {
        /** Estreito e baixo (2 x 2): o nome e as horas. */
        Small(R.layout.widget_bedtime_small, 110f, 110f, hero = true, details = false, buttons = false),

        /** Estreito e alto (2 x 3, 3 x 3...): também a hora de acordar e a repetição. */
        SmallTall(R.layout.widget_bedtime_small, 110f, 176f, hero = true, details = true, buttons = false),

        /** 180 a 250 dp de largura e baixo (3 x 2): as quatro linhas, sem botões. */
        WideNoButtons(R.layout.widget_bedtime_wide, 180f, 110f, hero = false, details = false, buttons = false),

        /** Pelo menos 250 dp de largura e baixo (4 x 2): as quatro linhas e os botões. */
        Wide(R.layout.widget_bedtime_wide, 250f, 110f, hero = false, details = false, buttons = true),

        /** Pelo menos 250 x 176 dp (4 x 3 e maiores): tudo, com a hora em grande. */
        Large(R.layout.widget_bedtime, 250f, 176f, hero = true, details = false, buttons = true),
    }

    /** Uma linha com ViewFlipper: os ids dos dois textos e do flipper. */
    private class FlipLine(val key: String, val flipper: Int, val a: Int, val b: Int) {
        /** A forma só com a hora (versões com a hora grande) guarda o seu estado à parte. */
        fun keyFor(hero: Boolean) = if (hero) "${key}_hero" else key
    }

    private val BedtimeLine = FlipLine("bed", R.id.widget_bedtime_flipper, R.id.widget_bedtime_a, R.id.widget_bedtime_b)
    private val WindDownLine = FlipLine("wind", R.id.widget_winddown_flipper, R.id.widget_winddown_a, R.id.widget_winddown_b)

    /** O que pôr numa linha: o texto de cada filho do flipper e qual fica à vista. */
    private class FlipState(val line: FlipLine, val texts: Map<Int, String>, val child: Int) {
        fun applyTo(views: RemoteViews) {
            texts.forEach { (id, text) -> views.setTextViewText(id, text) }
            views.setDisplayedChild(line.flipper, child)
        }
    }

    /**
     * Põe [text] na linha. Se mudou e [animate], o texto antigo fica no filho à vista e o novo vai
     * para o outro, que entra com a animação do ViewFlipper.
     */
    private fun flip(
        prefs: android.content.SharedPreferences,
        edit: android.content.SharedPreferences.Editor,
        appWidgetId: Int,
        line: FlipLine,
        hero: Boolean,
        text: String,
        animate: Boolean,
    ): FlipState {
        val key = line.keyFor(hero)
        val shown = prefs.getString(shownKey(appWidgetId) + key, null)
        var child = prefs.getInt(childKey(appWidgetId) + key, 0)
        fun id(c: Int) = if (c == 0) line.a else line.b
        val texts = if (shown != null && shown != text && animate) {
            val old = id(child)
            child = 1 - child
            mapOf(old to shown, id(child) to text)
        } else {
            mapOf(id(child) to text)
        }
        edit.putString(shownKey(appWidgetId) + key, text).putInt(childKey(appWidgetId) + key, child)
        return FlipState(line, texts, child)
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

/** O visto do widget: liga ou desliga a rotina (como o botão do cartão) e redesenha os widgets. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BedtimeWidgets.ActionToggle) return
        val routineId = intent.getLongExtra(BedtimeWidgets.ExtraRoutineId, -1L)
        if (routineId < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repository = CycloRepository(app)
                val routine = repository.data.first().routines.firstOrNull { it.id == routineId } ?: return@launch
                repository.setRoutineActive(routineId, !routine.active, System.currentTimeMillis())
                // Já com a mudança: o widget responde logo (o CycloSync trata do lembrete).
                BedtimeWidgets.updateAll(app, repository.data.first(), animate = false)
            } catch (e: Exception) {
                // Falhou a escrita: o widget fica como estava (nada mudou).
            } finally {
                pending.finish()
            }
        }
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
