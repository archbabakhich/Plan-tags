package de.lionsun.plantags.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import de.lionsun.plantags.R
import de.lionsun.plantags.extensions.config
import de.lionsun.plantags.extensions.shouldStrikeThrough
import de.lionsun.plantags.helpers.Formatter
import de.lionsun.plantags.models.Event
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperTextColor
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A single-day timeline: all-day events as bars on top, then a 24 hour grid where timed events
 * are drawn as rounded colored blocks. Overlapping events are placed side by side.
 */
class DayTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Item(
        val event: Event,
        val color: Int,
        val startMin: Int,
        val endMin: Int,
        val displayEndMin: Int,
        val isAllDay: Boolean,
    ) {
        var column = 0
        var columns = 1
        val rect = RectF()
        var titleLayout: StaticLayout? = null
        var timeLayout: StaticLayout? = null
    }

    var onEventClick: ((Event) -> Unit)? = null
    var onEmptyClick: ((minuteOfDay: Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val hourHeight = 64f * density
    private val labelWidth = 54f * density
    private val topPadding = 14f * density
    private val bottomPadding = 96f * density
    private val sidePadding = 12f * density
    private val allDayBarHeight = 30f * density
    private val allDayGap = 5f * density
    private val corner = 9f * density
    private val blockGap = 3f * density
    private val minBlockHeight = 24f * density

    private var allDayItems = ArrayList<Item>()
    private var timedItems = ArrayList<Item>()
    private var dayCode = ""
    private var showNowLine = false
    private var nowMinute = 0
    private var dimPast = true
    private var dimCompletedTasks = true
    private var use24Hours = true

    private var textColor = 0
    private val gridColor = ContextCompat.getColor(context, R.color.plantags_grid)
    private val labelColor = ContextCompat.getColor(context, R.color.plantags_label)
    private val nowColor = ContextCompat.getColor(context, R.color.plantags_accent)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hourPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(13f)
        isFakeBoldText = true
    }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val hit = (allDayItems + timedItems).lastOrNull { it.rect.contains(e.x, e.y) }
            if (hit != null) {
                onEventClick?.invoke(hit.event)
            } else if (e.y >= gridTop() && e.y <= gridTop() + 24 * hourHeight) {
                val rawMinutes = ((e.y - gridTop()) / hourHeight * 60f).toInt()
                onEmptyClick?.invoke((rawMinutes / 30) * 30)
            }
            return true
        }
    })

    init {
        updateColors()
    }

    fun updateColors() {
        textColor = context.getProperTextColor()
        use24Hours = context.config.use24HourFormat
        dimPast = context.config.dimPastEvents
        dimCompletedTasks = context.config.dimCompletedTasks
        invalidate()
    }

    /**
     * @param dayCode the day to display, in the Formatter day code format (yyyyMMdd)
     * @param events events that may overlap the day; others are ignored
     * @param colorFor resolves the block color of an event (its own color or the tag color)
     */
    fun setEvents(dayCode: String, events: List<Event>, colorFor: (Event) -> Int) {
        this.dayCode = dayCode
        val dayInt = dayCode.toInt()
        val allDay = ArrayList<Item>()
        val timed = ArrayList<Item>()
        val minDisplayMinutes = (minBlockHeight / hourHeight * 60f).roundToInt()

        for (event in events) {
            val start = Formatter.getDateTimeFromTS(event.startTS)
            val end = Formatter.getDateTimeFromTS(event.endTS)
            val startCode = Formatter.getDayCodeFromDateTime(start)
            val endCode = Formatter.getDayCodeFromDateTime(end)
            if (startCode.toInt() > dayInt || endCode.toInt() < dayInt) {
                continue
            }

            val color = colorFor(event)
            if (event.getIsAllDay() || (startCode != endCode && event.endTS - event.startTS >= 24 * 3600)) {
                allDay.add(Item(event, color, 0, 1440, 1440, true))
                continue
            }

            val startMin = if (startCode == dayCode) start.minuteOfDay else 0
            val endMin = if (endCode == dayCode) end.minuteOfDay else 1440
            if (endCode == dayCode && startCode != dayCode && endMin == 0) {
                continue
            }

            val displayEnd = min(1440, max(endMin, startMin + minDisplayMinutes))
            timed.add(Item(event, color, startMin, endMin, displayEnd, false))
        }

        allDayItems = ArrayList(allDay.sortedWith(compareBy({ it.event.startTS }, { it.event.title })))
        timedItems = ArrayList(timed.sortedWith(compareBy({ it.startMin }, { -it.displayEndMin }, { it.event.title })))
        assignColumns(timedItems)

        showNowLine = dayCode == Formatter.getTodayCode()
        if (showNowLine) {
            nowMinute = Formatter.getDateTimeFromTS(System.currentTimeMillis() / 1000L).minuteOfDay
        }

        requestLayout()
        invalidate()
    }

    private fun assignColumns(items: List<Item>) {
        val cluster = ArrayList<Item>()
        val columnEnds = ArrayList<Int>()
        var clusterEnd = -1

        fun flush() {
            val total = max(1, columnEnds.size)
            cluster.forEach { it.columns = total }
            cluster.clear()
            columnEnds.clear()
            clusterEnd = -1
        }

        for (item in items) {
            if (cluster.isNotEmpty() && item.startMin >= clusterEnd) {
                flush()
            }

            var column = columnEnds.indexOfFirst { it <= item.startMin }
            if (column == -1) {
                columnEnds.add(item.displayEndMin)
                column = columnEnds.lastIndex
            } else {
                columnEnds[column] = item.displayEndMin
            }

            item.column = column
            cluster.add(item)
            clusterEnd = max(clusterEnd, item.displayEndMin)
        }
        flush()
    }

    private fun allDayAreaHeight(): Float {
        return if (allDayItems.isEmpty()) {
            0f
        } else {
            allDayItems.size * (allDayBarHeight + allDayGap) + allDayGap
        }
    }

    private fun gridTop() = topPadding + allDayAreaHeight()

    /** Y position (in this view) of a given minute of the day, used to scroll to the current time. */
    fun getYForMinute(minute: Int): Int = (gridTop() + minute / 60f * hourHeight).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (gridTop() + 24 * hourHeight + bottomPadding).roundToInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutItems()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutItems()
    }

    private fun isRtl() = layoutDirection == LAYOUT_DIRECTION_RTL

    /** Converts a rectangle computed for left-to-right into the real direction. */
    private fun mirror(rect: RectF, left: Float, top: Float, right: Float, bottom: Float) {
        if (isRtl()) {
            rect.set(width - right, top, width - left, bottom)
        } else {
            rect.set(left, top, right, bottom)
        }
    }

    private fun layoutItems() {
        if (width == 0) {
            return
        }

        val contentLeft = labelWidth
        val contentRight = width - sidePadding
        val contentWidth = contentRight - contentLeft

        var barTop = topPadding + allDayGap
        for (item in allDayItems) {
            mirror(item.rect, contentLeft, barTop, contentRight, barTop + allDayBarHeight)
            item.titleLayout = null
            item.timeLayout = null
            barTop += allDayBarHeight + allDayGap
        }

        val gridTop = gridTop()
        for (item in timedItems) {
            val columnWidth = contentWidth / item.columns
            val left = contentLeft + item.column * columnWidth
            val top = gridTop + item.startMin / 60f * hourHeight + 1f * density
            val bottom = gridTop + item.displayEndMin / 60f * hourHeight - 1f * density
            mirror(item.rect, left, top, left + columnWidth - blockGap, max(bottom, top + minBlockHeight - 2f * density))
            item.titleLayout = null
            item.timeLayout = null
        }
    }

    private fun hourLabel(hour: Int): String {
        return if (use24Hours) {
            String.format(Locale.getDefault(), "%02d:00", hour)
        } else {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            String.format(Locale.getDefault(), "%d %s", h12, if (hour < 12) "AM" else "PM")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gridTop = gridTop()
        val rtl = isRtl()

        // grid lines and hour labels
        linePaint.color = gridColor
        hourPaint.color = labelColor
        val lineStartLtr = labelWidth
        val lineEndLtr = width - sidePadding
        val lineStart = if (rtl) width - lineEndLtr else lineStartLtr
        val lineEnd = if (rtl) width - lineStartLtr else lineEndLtr
        val labelCenterX = if (rtl) width - labelWidth / 2f else labelWidth / 2f
        val fm = hourPaint.fontMetrics
        for (hour in 0..24) {
            val y = gridTop + hour * hourHeight
            canvas.drawLine(lineStart, y, lineEnd, y, linePaint)
            if (hour in 0..23) {
                canvas.drawText(hourLabel(hour), labelCenterX, y - (fm.ascent + fm.descent) / 2f, hourPaint)
            }
        }

        val now = System.currentTimeMillis() / 1000L
        for (item in allDayItems) {
            drawItem(canvas, item, now, singleLine = true)
        }
        for (item in timedItems) {
            drawItem(canvas, item, now, singleLine = false)
        }

        if (showNowLine) {
            val y = gridTop + nowMinute / 60f * hourHeight
            linePaint.color = nowColor
            linePaint.strokeWidth = 2f * density
            canvas.drawLine(lineStart, y, lineEnd, y, linePaint)
            fillPaint.color = nowColor
            canvas.drawCircle(if (rtl) lineEnd else lineStart, y, 4.5f * density, fillPaint)
            linePaint.strokeWidth = 1f * density
        }
    }

    private fun drawItem(canvas: Canvas, item: Item, now: Long, singleLine: Boolean) {
        val event = item.event
        var fill = item.color
        var ink = fill.getContrastColor()

        val dimmed = if (event.isTask()) {
            dimCompletedTasks && event.isTaskCompleted()
        } else {
            dimPast && event.endTS < now && event.endTS != event.startTS
        }
        if (dimmed) {
            fill = fill.adjustAlpha(0.55f)
            ink = ink.adjustAlpha(0.85f)
        }

        fillPaint.color = fill
        canvas.drawRoundRect(item.rect, corner, corner, fillPaint)

        val padding = 7f * density
        val textWidth = (item.rect.width() - 2 * padding).roundToInt()
        if (textWidth <= 0) {
            return
        }

        titlePaint.color = ink
        titlePaint.isStrikeThruText = event.shouldStrikeThrough()
        val lineHeight = titlePaint.fontSpacing
        val availableHeight = item.rect.height() - 2 * (if (singleLine) 0f else 5f * density)
        val maxLines = if (singleLine) 1 else max(1, (availableHeight / lineHeight).toInt())

        if (item.titleLayout == null) {
            item.titleLayout = StaticLayout.Builder
                .obtain(event.title, 0, event.title.length, titlePaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        }

        val title = item.titleLayout!!
        canvas.save()
        canvas.clipRect(item.rect)
        if (singleLine) {
            canvas.translate(item.rect.left + padding, item.rect.centerY() - title.height / 2f)
        } else {
            canvas.translate(item.rect.left + padding, item.rect.top + 5f * density)
        }
        title.draw(canvas)
        canvas.restore()

        // time range below the title when there is room for it
        if (!singleLine && item.columns <= 2 && item.rect.height() >= 58f * density) {
            if (item.timeLayout == null) {
                val range = "${Formatter.getTimeFromTS(context, event.startTS)} – ${Formatter.getTimeFromTS(context, event.endTS)}"
                timePaint.color = ink.adjustAlpha(0.9f)
                item.timeLayout = StaticLayout.Builder
                    .obtain(range, 0, range.length, timePaint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(1)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            }
            val timeLayout = item.timeLayout!!
            val timeTop = item.rect.top + 5f * density + title.height + 2f * density
            if (timeTop + timeLayout.height <= item.rect.bottom) {
                canvas.save()
                canvas.translate(item.rect.left + padding, timeTop)
                timeLayout.draw(canvas)
                canvas.restore()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
