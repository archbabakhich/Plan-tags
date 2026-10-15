package de.lionsun.plantags.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import de.lionsun.plantags.R
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The week header of the day view: weekday names on a colored band and, under them, one ring per day.
 * Every ring is split into arcs, one per tag color, sized by how much time that tag occupies in the day.
 */
class WeekStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    class DayCell(
        val dayCode: String,
        val weekdayLabel: String,
        val dayOfMonth: Int,
        val isToday: Boolean,
        /** (color, minutes) per tag, already merged by color */
        val segments: List<Pair<Int, Int>>,
    )

    var onDayClick: ((dayCode: String) -> Unit)? = null

    private var cells: List<DayCell> = emptyList()
    private var selectedDayCode = ""

    private val density = resources.displayMetrics.density
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val bandHeight = 30f * density
    private val ringAreaHeight = 66f * density
    private val ringRadius = 19f * density
    private val ringStroke = 3.2f * density

    private var primaryColor = 0
    private var textColor = 0
    private val accentColor = ContextCompat.getColor(context, R.color.plantags_accent)
    private val trackColor = ContextCompat.getColor(context, R.color.plantags_grid)

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStroke
        strokeCap = Paint.Cap.ROUND
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }
    private val weekdayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(14f)
        textAlign = Paint.Align.CENTER
    }

    private val arcBounds = RectF()
    private val selectionBounds = RectF()

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (cells.isEmpty() || width == 0) {
                return true
            }
            val cellWidth = width.toFloat() / cells.size
            var index = (e.x / cellWidth).toInt().coerceIn(0, cells.size - 1)
            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                index = cells.size - 1 - index
            }
            onDayClick?.invoke(cells[index].dayCode)
            return true
        }
    })

    init {
        updateColors()
    }

    fun updateColors() {
        primaryColor = context.getProperPrimaryColor()
        textColor = context.getProperTextColor()
        invalidate()
    }

    fun setWeek(newCells: List<DayCell>, selectedDayCode: String) {
        cells = newCells
        this.selectedDayCode = selectedDayCode
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (bandHeight + ringAreaHeight).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cells.isEmpty()) {
            return
        }

        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val cellWidth = width.toFloat() / cells.size

        bandPaint.color = primaryColor
        canvas.drawRect(0f, 0f, width.toFloat(), bandHeight, bandPaint)

        val weekdayFm = weekdayPaint.fontMetrics
        val numberFm = numberPaint.fontMetrics
        val ringCenterY = bandHeight + ringAreaHeight / 2f

        for ((i, cell) in cells.withIndex()) {
            val position = if (rtl) cells.size - 1 - i else i
            val centerX = position * cellWidth + cellWidth / 2f
            val isSelected = cell.dayCode == selectedDayCode

            weekdayPaint.color = if (isSelected) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt().adjustAlpha(0.78f)
            canvas.drawText(
                cell.weekdayLabel,
                centerX,
                bandHeight / 2f - (weekdayFm.ascent + weekdayFm.descent) / 2f,
                weekdayPaint
            )

            drawRing(canvas, centerX, ringCenterY, cell)

            numberPaint.isFakeBoldText = isSelected || cell.isToday
            numberPaint.color = if (cell.isToday) accentColor else textColor
            canvas.drawText(
                String.format(Locale.getDefault(), "%d", cell.dayOfMonth),
                centerX,
                ringCenterY - (numberFm.ascent + numberFm.descent) / 2f,
                numberPaint
            )

            if (isSelected) {
                val half = ringRadius + 6f * density
                selectionBounds.set(centerX - half, ringCenterY - half, centerX + half, ringCenterY + half)
                selectionPaint.color = accentColor
                canvas.drawRoundRect(selectionBounds, 9f * density, 9f * density, selectionPaint)
            }
        }
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, cell: DayCell) {
        arcBounds.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)

        ringPaint.color = trackColor
        canvas.drawArc(arcBounds, 0f, 360f, false, ringPaint)

        if (cell.segments.isEmpty()) {
            return
        }

        // a ring is a 16 hour waking day; busier days are scaled down so the arcs always fit
        val dayMinutes = 16 * 60
        val total = cell.segments.sumOf { it.second }
        val scale = if (total > dayMinutes) dayMinutes.toFloat() / total else 1f
        val gap = if (cell.segments.size > 1) 7f else 0f

        var start = -90f
        for ((color, minutes) in cell.segments) {
            val sweep = minutes * scale / dayMinutes * 360f
            val drawn = max(sweep - gap, min(sweep, 10f))
            if (drawn <= 0f) {
                continue
            }
            ringPaint.color = color
            canvas.drawArc(arcBounds, start + gap / 2f, drawn, false, ringPaint)
            start += sweep
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
