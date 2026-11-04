package com.gaebriu.cyclo.widget

import android.graphics.Canvas
import android.graphics.Bitmap
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.gaebriu.cyclo.MainActivity
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.BedtimeEngine
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.CycloRepository
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.reminder.BedtimeReminder
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.routine.RepeatOption
import com.gaebriu.cyclo.ui.routine.Weekday
import com.gaebriu.cyclo.ui.routine.hasDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.min

/**
 * Os dois widgets do ecrã principal (Figma, Componentes 4):
 *
 * - **Routine Widget** (244:48, 345 x 164): uma rotina, como o cartão das rotinas guardadas, com o
 *   nome, o sol e a hora de acordar, o calendário e a repetição, o edit-routine-b (abre o editor
 *   dela na app) e o activate/deactivate-routine-b (liga e desliga). Sem o delete. Desligada, leva
 *   o véu de 63 % do cartão. Fixa-se pelo "Add as a widget" das rotinas guardadas e do Introduction 5
 *   ([requestPin]); a rotina fica ligada ao id do widget quando o lançador confirma. Um widget sem
 *   rotina (posto pela lista do lançador, ou com a rotina apagada) mostra a próxima a acordar.
 * - **Wake Time Widget** (244:107, 345 x 171): o wake-time-container do Feed, com os mesmos dados
 *   ([BedtimeEngine.feedWake] e [BedtimeEngine.feedBedtimes]) e os mesmos ícones; só a cor dos
 *   ícones muda (#FCFCFE). Fixa-se pelo "Add as a widget" do menu do Wake Time do Feed
 *   ([requestPinWakeTime]). Quando o tempo para adormecer muda, cada hora nova rola (ViewFlipper).
 *
 * O cartão é desenhado à escala que cabe no espaço do lançador, com a proporção do Figma
 * ([WidgetCanvas]): um RemoteViews por tamanho real do widget (retrato e paisagem), o lançador
 * escolhe. O CycloSync só chama isto depois de as mudanças assentarem; o alarme de refrescar do
 * [BedtimeReminder] trata das mudanças que só dependem da hora.
 */
object BedtimeWidgets {
    private const val Prefs = "bedtime_widgets"
    private const val WakePrefs = "wake_widgets"
    private fun routineKey(id: Int) = "routine_$id"

    const val ExtraRoutineId = "routine_id"
    private const val ActionPinned = "com.gaebriu.cyclo.action.WIDGET_PINNED"

    /** O lápis do widget: abre a app no editor da rotina [ExtraRoutineId]. */
    const val ActionEdit = "com.gaebriu.cyclo.action.EDIT_ROUTINE"
    internal const val ActionToggle = "com.gaebriu.cyclo.action.TOGGLE_ROUTINE"

    // Medidas do Figma (Componentes 4), em unidades do Figma, a partir do canto do cartão.
    private const val Width = 345f
    private const val RoutineHeight = 164f
    private const val WakeHeight = 171f
    private const val Inset = 15f
    private const val IconToText = 21f
    private const val EditX = 237f
    private const val ToggleX = 291f
    private const val ButtonsY = 110f

    /** Zona de toque à volta de cada botão de 39 (48 dp com o widget ao tamanho do Figma). */
    private const val TouchOutset = 4.5f

    /** As linhas das horas do wake-time-container: o topo dos ícones e o das horas. */
    private val RowIconTop = floatArrayOf(63f, 117f)
    private val RowTextTop = floatArrayOf(84f, 138f)
    private const val ItemGap = 15f

    /** Margem à volta de cada hora no seu bitmap (para a sombra do texto). */
    private const val SlotPad = 4f

    private val SlotIds = intArrayOf(R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3, R.id.slot4, R.id.slot5)
    private val SlotChildren = arrayOf(
        intArrayOf(R.id.slot0_a, R.id.slot0_b),
        intArrayOf(R.id.slot1_a, R.id.slot1_b),
        intArrayOf(R.id.slot2_a, R.id.slot2_b),
        intArrayOf(R.id.slot3_a, R.id.slot3_b),
        intArrayOf(R.id.slot4_a, R.id.slot4_b),
        intArrayOf(R.id.slot5_a, R.id.slot5_b),
    )

    /** #A3A2A7 a 39 % (0x63): os dias que a rotina não tem. */
    private const val DayOff = 0x63A3A2A7

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
            val color = if (mask.hasDay(day)) WidgetCanvas.White else DayOff
            text.setSpan(ForegroundColorSpan(color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    /** Pede ao lançador para fixar o Routine Widget da rotina [routineId]. false se não for possível. */
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

    /** Pede ao lançador para fixar o Wake Time Widget. false se não for possível. */
    fun requestPinWakeTime(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, WakeTimeWidgetProvider::class.java), Bundle(), null)
    }

    internal fun bind(context: Context, appWidgetId: Int, routineId: Long) {
        prefs(context).edit().putLong(routineKey(appWidgetId), routineId).apply()
    }

    internal fun forget(context: Context, appWidgetIds: IntArray) {
        val edit = prefs(context).edit()
        appWidgetIds.forEach { edit.remove(routineKey(it)) }
        edit.apply()
    }

    internal fun forgetWake(context: Context, appWidgetIds: IntArray) {
        val edit = context.getSharedPreferences(WakePrefs, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { id -> SlotIds.indices.forEach { edit.remove(shownKey(id, it)).remove(childKey(id, it)) } }
        edit.apply()
    }

    /** Passa a rotina de cada widget do id antigo para o novo (widgets repostos pelo lançador). */
    internal fun remap(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = prefs(context)
        val bound = oldIds.map { prefs.getLong(routineKey(it), -1L) }
        forget(context, oldIds)
        val edit = prefs.edit()
        newIds.forEachIndexed { i, id ->
            val routineId = bound.getOrNull(i) ?: -1L
            if (routineId >= 0) edit.putLong(routineKey(id), routineId)
        }
        edit.apply()
    }

    /** Redesenha os dois widgets com [data] (as horas novas do Wake Time rolam se [animate]). */
    fun updateAll(context: Context, data: CycloData, animate: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, BedtimeWidgetProvider::class.java))
            .forEach { runCatching { updateRoutine(context, manager, it, data) } }
        manager.getAppWidgetIds(ComponentName(context, WakeTimeWidgetProvider::class.java))
            .forEach { runCatching { updateWakeTime(context, manager, it, data, animate) } }
    }

    // --- Routine Widget ---------------------------------------------------------------------

    private fun updateRoutine(context: Context, manager: AppWidgetManager, appWidgetId: Int, data: CycloData) {
        val now = LocalDateTime.now()
        val formatter = BedtimeReminder.timeFormatter(context, data.use24Hour)
        val boundId = prefs(context).getLong(routineKey(appWidgetId), -1L)
        val routine = data.routines.firstOrNull { it.id == boundId }
            ?: BedtimeEngine.nextWake(data.routines, now)?.first
            ?: data.routines.firstOrNull()

        val name = routine?.name ?: context.getString(R.string.app_name)
        val wake = routine?.let { LocalTime.of(it.wakeHour, it.wakeMinute).format(formatter) }
        val repeat = routine?.let { repeatSummary(context, it) }
        val active = routine?.active == true
        val description = if (routine == null) {
            "$name, ${context.getString(R.string.widget_no_routine)}"
        } else {
            listOf(
                name,
                wake,
                repeat.toString(),
                context.getString(if (active) R.string.saved_state_on else R.string.saved_state_off),
            ).joinToString(", ")
        }

        fun build(size: SizeF): RemoteViews {
            val scale = fit(size, RoutineHeight)
            val canvas = WidgetCanvas(context, scale * context.resources.displayMetrics.density)
            val image = canvas.bitmap(Width, RoutineHeight) { routineCard(context, canvas, name, wake, repeat, active) }
            val views = RemoteViews(context.packageName, R.layout.widget_routine)
            sizeCard(views, scale, RoutineHeight)
            views.setImageViewBitmap(R.id.widget_image, image)
            views.setContentDescription(R.id.widget_image, description)
            views.setOnClickPendingIntent(android.R.id.background, openApp(context))

            val buttons = if (routine == null) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.widget_edit, buttons)
            views.setViewVisibility(R.id.widget_toggle, buttons)
            if (routine != null) {
                place(views, R.id.widget_edit, scale, EditX - TouchOutset, ButtonsY - TouchOutset, WidgetCanvas.ButtonSize + TouchOutset * 2, WidgetCanvas.ButtonSize + TouchOutset * 2)
                place(views, R.id.widget_toggle, scale, ToggleX - TouchOutset, ButtonsY - TouchOutset, WidgetCanvas.ButtonSize + TouchOutset * 2, WidgetCanvas.ButtonSize + TouchOutset * 2)
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
            return views
        }

        manager.updateAppWidget(appWidgetId, RemoteViews(sizes(manager, appWidgetId, RoutineHeight).associateWith { build(it) }))
    }

    /**
     * O cartão do Routine Widget (Figma 244:48) em unidades do Figma: o mesmo desenho no ecrã
     * principal e na pré-visualização do lançador ([previewBitmaps]). [wake] null = sem rotina.
     */
    private fun Canvas.routineCard(context: Context, canvas: WidgetCanvas, name: String, wake: String?, repeat: CharSequence?, active: Boolean) {
        with(canvas) {
            card(Width, RoutineHeight)
            // routine-name (SemiBold 15), a hora (sol) e a repetição (calendário), Medium 15.
            text(name, Inset, 15f, paint(semiBold = true), Width - Inset * 2)
            if (wake == null || repeat == null) {
                text(context.getString(R.string.widget_no_routine), Inset, 47f, paint(semiBold = false), Width - Inset * 2)
                return
            }
            icon(CycloAppIconPaths.Sunny, Inset, 48f, WidgetCanvas.White)
            text(wake, Inset + IconToText, 47f, paint(semiBold = false), Width - Inset * 2 - IconToText)
            icon(CycloAppIconPaths.CalendarToday, Inset, 78f, WidgetCanvas.White)
            text(repeat, Inset + IconToText, 77f, paint(semiBold = false), Width - Inset * 2 - IconToText)
            // fx upper-shadow das rotinas desligadas: #101011 a 63 %, por baixo dos botões.
            if (!active) roundRect(Width, RoutineHeight, 0xA1101011.toInt())
            // edit-routine-b: #A3A2A7 a 15 % e a borda #313233 a 24 %, misturados sobre o cartão.
            circleButton(EditX, ButtonsY, fill = 0xFF262628.toInt(), border = 0x3D313233)
            icon(CycloAppIconPaths.RoutineEdit, EditX, ButtonsY, WidgetCanvas.White)
            // activate/deactivate-routine-b: ligado #4A3C99; desligado #A3A2A7 a 24 % e o visto a 24 %.
            if (active) {
                circleButton(ToggleX, ButtonsY, fill = 0xFF4A3C99.toInt(), border = 0x3D4A3C99)
            } else {
                circleButton(ToggleX, ButtonsY, fill = 0xFF333335.toInt(), border = 0x3DA3A2A7)
            }
            icon(CycloAppIconPaths.RoutineActive, ToggleX, ButtonsY, WidgetCanvas.White, alpha = if (active) 1f else 0.24f)
        }
    }

    // --- Wake Time Widget -------------------------------------------------------------------

    /** Uma hora do wake-time-container já posta: o ícone, o texto e a caixa (unidades do Figma). */
    private class Slot(val icon: String, val text: String, val left: Float, val width: Float, val row: Int)

    private fun updateWakeTime(context: Context, manager: AppWidgetManager, appWidgetId: Int, data: CycloData, animate: Boolean) {
        val now = LocalDateTime.now()
        val formatter = BedtimeReminder.timeFormatter(context, data.use24Hour)
        // Os mesmos dados do Feed: a primeira hora de acordar a 24 h e as seis horas para adormecer.
        val next = BedtimeEngine.feedWake(data.routines, now)
        val wakeText = next?.let { (routine, _) -> LocalTime.of(routine.wakeHour, routine.wakeMinute).format(formatter) }
        val bedtimes = next?.let { (routine, wake) ->
            BedtimeEngine.feedBedtimes(routine, wake, now, data.age, data.chronotype, data.fallAsleep, data.dailyLog)
        }.orEmpty()
        val times = bedtimes.map { it.time.format(formatter) }

        // O estado de cada ViewFlipper: a hora antiga fica à vista e a nova entra no outro filho.
        val prefs = context.getSharedPreferences(WakePrefs, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val flips = times.mapIndexed { i, text -> flip(prefs, edit, appWidgetId, i, text, animate) }
        edit.apply()

        val description = if (next == null) {
            context.getString(R.string.widget_no_bedtime)
        } else {
            context.getString(R.string.feed_wake_title) + " " + wakeText + ". " +
                bedtimes.joinToString(", ") {
                    context.resources.getQuantityString(R.plurals.feed_bedtime_description, it.cycles, it.time.format(formatter), it.cycles)
                }
        }

        fun build(size: SizeF): RemoteViews {
            val scale = fit(size, WakeHeight)
            val canvas = WidgetCanvas(context, scale * context.resources.displayMetrics.density)
            val timePaint = canvas.paint(semiBold = true, size = timeSize(canvas, times), shadow = true)
            val slots = layoutSlots(canvas, bedtimes.map { CycloAppIconPaths.bedtime(it.cycles) }, flips.map { it.texts.values.toList() }, timePaint)
            val image = canvas.bitmap(Width, WakeHeight) { wakeCard(context, canvas, wakeText, slots) }
            val views = RemoteViews(context.packageName, R.layout.widget_wake_time)
            sizeCard(views, scale, WakeHeight)
            views.setImageViewBitmap(R.id.widget_image, image)
            views.setContentDescription(R.id.widget_image, description)
            views.setOnClickPendingIntent(android.R.id.background, openApp(context))

            SlotIds.forEachIndexed { i, id ->
                val slot = slots.getOrNull(i)
                val flipState = flips.getOrNull(i)
                if (slot == null || flipState == null) {
                    views.setViewVisibility(id, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(id, View.VISIBLE)
                val boxW = slot.width + SlotPad * 2
                val boxH = WidgetCanvas.LineHeight + SlotPad * 2
                place(views, id, scale, slot.left - SlotPad, RowTextTop[slot.row] - SlotPad, boxW, boxH)
                flipState.texts.forEach { (child, label) ->
                    val bitmap = canvas.bitmap(boxW, boxH) {
                        with(canvas) { text(label, boxW / 2f, SlotPad, timePaint, slot.width + SlotPad, centerX = true) }
                    }
                    views.setImageViewBitmap(SlotChildren[i][child], bitmap)
                }
                views.setDisplayedChild(id, flipState.child)
            }
            return views
        }

        // Sempre a atualização completa: com o mesmo layout, o lançador reaplica-a sobre a vista que
        // está no ecrã (RemoteViews.canRecycleView), por isso o ViewFlipper anima a troca.
        manager.updateAppWidget(appWidgetId, RemoteViews(sizes(manager, appWidgetId, WakeHeight).associateWith { build(it) }))
    }

    /**
     * O cartão do Wake Time Widget (Figma 244:107) sem as horas (essas são ViewFlippers por cima),
     * em unidades do Figma: o mesmo desenho no ecrã principal e na pré-visualização. [wakeText] null =
     * nenhuma hora de acordar nas próximas 24 h.
     */
    private fun Canvas.wakeCard(context: Context, canvas: WidgetCanvas, wakeText: String?, slots: List<Slot>) {
        with(canvas) {
            card(Width, WakeHeight)
            // wake-time-text: o sol e a hora de acordar (Medium 15, com a sombra do Feed).
            icon(CycloAppIconPaths.Sunny, Inset, 16f, WidgetCanvas.White)
            text(
                wakeText ?: context.getString(R.string.widget_no_bedtime),
                Inset + IconToText,
                15f,
                paint(semiBold = false, shadow = true),
                Width - Inset * 2 - IconToText,
            )
            if (wakeText == null) return
            // separator: 0,5, #A3A2A7 a 15 %.
            drawLine(
                Inset,
                48f,
                Width - Inset,
                48f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x26A3A2A7
                    strokeWidth = 0.5f
                    strokeCap = Paint.Cap.ROUND
                },
            )
            // Os ícones das horas (os do Feed, a #FCFCFE), centrados por cima de cada hora.
            slots.forEach { slot ->
                icon(slot.icon, slot.left + slot.width / 2f - 7.5f, RowIconTop[slot.row], WidgetCanvas.White)
            }
        }
    }

    /**
     * As pré-visualizações do lançador (lista de widgets), desenhadas com o mesmo código dos widgets
     * reais e os valores de exemplo do Figma (Componentes 4): Routine Widget e Wake Time Widget, com
     * [pxPerUnit] píxeis por unidade do Figma. Geradas para res/drawable-nodpi (e -pt-nodpi) como
     * widget_*_preview_image.png pelo teste instrumentado WidgetPreviewGenerator: o lançador mostra
     * a pré-visualização sem correr código da app.
     */
    internal fun previewBitmaps(context: Context, pxPerUnit: Float): Pair<Bitmap, Bitmap> {
        val canvas = WidgetCanvas(context, pxPerUnit)
        val routine = canvas.bitmap(Width, RoutineHeight) {
            routineCard(
                context,
                canvas,
                context.getString(R.string.widget_preview_name),
                context.getString(R.string.widget_preview_wake),
                context.getString(R.string.repeat_just_once),
                active = true,
            )
        }
        val times = listOf(
            R.string.widget_preview_bed_0,
            R.string.widget_preview_bed_1,
            R.string.widget_preview_bed_2,
            R.string.widget_preview_bed_3,
            R.string.widget_preview_bed_4,
            R.string.widget_preview_bed_5,
        ).map(context::getString)
        val timePaint = canvas.paint(semiBold = true, size = timeSize(canvas, times), shadow = true)
        // Os ícones do Feed pela mesma ordem (6 ciclos primeiro).
        val slots = layoutSlots(canvas, (6 downTo 1).map { CycloAppIconPaths.bedtime(it) }, times.map { listOf(it) }, timePaint)
        val wake = canvas.bitmap(Width, WakeHeight) {
            wakeCard(context, canvas, context.getString(R.string.widget_preview_wake), slots)
            // As horas no sítio delas (o mesmo que o bitmap de cada ViewFlipper desenha, sem a margem).
            slots.forEach { slot ->
                with(canvas) { text(slot.text, slot.left + slot.width / 2f, RowTextTop[slot.row], timePaint, slot.width + SlotPad, centerX = true) }
            }
        }
        return routine to wake
    }

    /**
     * O tamanho das horas: 15 (Figma) ou, se uma fila não couber nos 315 do cartão (horas de 12 h
     * longas), o maior que cabe. As filas são as do Feed: duas horas e depois quatro.
     */
    private fun timeSize(canvas: WidgetCanvas, times: List<String>): Float {
        var size = WidgetCanvas.TextSize
        while (size > 9f) {
            val paint = canvas.paint(semiBold = true, size = size)
            val fits = listOf(times.take(2), times.drop(2)).all { row ->
                row.sumOf { maxOf(15f, canvas.width(it, paint)).toDouble() } + ItemGap * (row.size - 1).coerceAtLeast(0) <= Width - Inset * 2
            }
            if (fits) return size
            size -= 0.5f
        }
        return size
    }

    /**
     * Põe as horas como no Feed (BedtimeRow): duas na primeira fila e quatro na segunda, centradas,
     * a 15 umas das outras, cada uma da largura do texto (pelo menos a do ícone). Num ViewFlipper a
     * meio de uma troca conta o texto mais largo dos dois.
     */
    private fun layoutSlots(canvas: WidgetCanvas, icons: List<String>, texts: List<List<String>>, paint: android.text.TextPaint): List<Slot> {
        val widths = texts.map { options -> options.maxOf { maxOf(15f, canvas.width(it, paint)) } }
        val rows = listOf(0 until minOf(2, icons.size), 2 until icons.size)
        val result = ArrayList<Slot>(icons.size)
        rows.forEachIndexed { row, range ->
            if (range.isEmpty()) return@forEachIndexed
            val rowWidth = range.sumOf { widths[it].toDouble() }.toFloat() + ItemGap * (range.count() - 1)
            var x = Width / 2f - rowWidth / 2f
            range.forEach { i ->
                result += Slot(icons[i], texts[i].last(), x, widths[i], row)
                x += widths[i] + ItemGap
            }
        }
        return result
    }

    private fun shownKey(id: Int, slot: Int) = "shown_${id}_$slot"
    private fun childKey(id: Int, slot: Int) = "child_${id}_$slot"

    /** O que pôr numa hora: o texto de cada filho do ViewFlipper (0 ou 1) e qual fica à vista. */
    private class FlipState(val texts: Map<Int, String>, val child: Int)

    /** Se a hora mudou e [animate], a antiga fica no filho à vista e a nova vai para o outro. */
    private fun flip(prefs: SharedPreferences, edit: SharedPreferences.Editor, appWidgetId: Int, slot: Int, text: String, animate: Boolean): FlipState {
        val shown = prefs.getString(shownKey(appWidgetId, slot), null)
        var child = prefs.getInt(childKey(appWidgetId, slot), 0)
        val texts = if (shown != null && shown != text && animate) {
            val old = child
            child = 1 - child
            linkedMapOf(old to shown, child to text)
        } else {
            linkedMapOf(child to text)
        }
        edit.putString(shownKey(appWidgetId, slot), text).putInt(childKey(appWidgetId, slot), child)
        return FlipState(texts, child)
    }

    // --- Comum ------------------------------------------------------------------------------

    /** A escala que cabe em [size] mantendo a proporção do Figma (345 x [height]). */
    private fun fit(size: SizeF, height: Float): Float = min(size.width / Width, size.height / height).coerceAtLeast(0.1f)

    /** O cartão com o tamanho do Figma à escala [scale], centrado no espaço do widget. */
    private fun sizeCard(views: RemoteViews, scale: Float, height: Float) {
        views.setViewLayoutWidth(R.id.widget_card, Width * scale, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.widget_card, height * scale, TypedValue.COMPLEX_UNIT_DIP)
    }

    /** Põe a vista [id] em ([x], [y]) com [w] x [h], em unidades do Figma à escala [scale]. */
    private fun place(views: RemoteViews, id: Int, scale: Float, x: Float, y: Float, w: Float, h: Float) {
        views.setViewLayoutMargin(id, RemoteViews.MARGIN_LEFT, x * scale, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, y * scale, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutWidth(id, w * scale, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(id, h * scale, TypedValue.COMPLEX_UNIT_DIP)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Os tamanhos em que o widget está no lançador (um por orientação), em dp. Sem eles (antes de o
     * lançador os dar), os mínimos e máximos das opções; sem nada, o tamanho do Figma.
     */
    private fun sizes(manager: AppWidgetManager, appWidgetId: Int, height: Float): List<SizeF> {
        val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
        val reported = runCatching {
            options?.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java).orEmpty()
        }.getOrDefault(emptyList()).filter { it.width > 0f && it.height > 0f }
        if (reported.isNotEmpty()) return reported.distinct()
        val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val maxH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val maxW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val fromOptions = listOf(SizeF(minW.toFloat(), maxH.toFloat()), SizeF(maxW.toFloat(), minH.toFloat()))
            .filter { it.width > 0f && it.height > 0f }
            .distinct()
        return fromOptions.ifEmpty { listOf(SizeF(Width, height)) }
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

/**
 * O Routine Widget no lançador (APPWIDGET_UPDATE e companhia): desenha-se com os dados atuais
 * quando aparece, quando o sistema pede, quando muda de tamanho e quando é reposto de uma cópia do
 * lançador. Apagado, esquece a rotina a que estava ligado. (O nome da classe é o de antes, para os
 * widgets já postos no ecrã principal continuarem a funcionar depois de atualizar a app.)
 *
 * Não há WorkManager nem updatePeriodMillis: os dados vêm todos do DataStore da app, o CycloSync
 * redesenha quando eles mudam, e o alarme de refrescar do [BedtimeReminder] trata das mudanças que
 * só dependem da hora.
 */
class BedtimeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        BedtimeWidgets.withData(this, context) { BedtimeWidgets.updateAll(context, it, animate = false) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Outro tamanho (redimensionado, rodado): o cartão volta a ser desenhado à escala que cabe.
        BedtimeWidgets.withData(this, context) { BedtimeWidgets.updateAll(context, it, animate = false) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        BedtimeWidgets.forget(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        // Lançador reposto (ex.: telemóvel novo): os ids mudam, a rotina de cada widget mantém-se.
        BedtimeWidgets.remap(context, oldWidgetIds, newWidgetIds)
    }
}

/** O Wake Time Widget no lançador: como o [BedtimeWidgetProvider], sem rotina ligada. */
class WakeTimeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        BedtimeWidgets.withData(this, context) { BedtimeWidgets.updateAll(context, it, animate = false) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        BedtimeWidgets.withData(this, context) { BedtimeWidgets.updateAll(context, it, animate = false) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        BedtimeWidgets.forgetWake(context, appWidgetIds)
    }
}

/** O visto do Routine Widget: liga ou desliga a rotina (como o botão do cartão) e redesenha os widgets. */
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
                // Já com a mudança: o widget responde logo, e o lembrete é reagendado (a app pode
                // não estar a correr, e o CycloSync só reage depois do debounce).
                com.gaebriu.cyclo.reminder.CycloSync.apply(app, repository.data.first(), animateWidgets = false)
            } catch (e: Exception) {
                // Falhou a escrita: o widget fica como estava (nada mudou).
            } finally {
                pending.finish()
            }
        }
    }
}

/** O lançador confirmou o Routine Widget fixado a partir de uma rotina: liga-o a ela e desenha-o. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val routineId = intent.getLongExtra(BedtimeWidgets.ExtraRoutineId, -1L)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || routineId < 0) return
        BedtimeWidgets.bind(context, appWidgetId, routineId)
        BedtimeWidgets.withData(this, context) { BedtimeWidgets.updateAll(context, it, animate = false) }
    }
}
