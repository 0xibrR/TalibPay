package com.irdev.talibpay.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import androidx.core.os.ConfigurationCompat
import com.google.android.material.color.MaterialColors
import com.irdev.talibpay.R
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.TrendPoint
import com.irdev.talibpay.domain.YearMonth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * مخطط أعمدة للتحصيل الشهري: لكل شهر عمود للمستحق (فاتح) وعمود للمحصّل، مع مفتاح أسفله.
 * يُرسم على Canvas مباشرة (بلا مكتبات) فيظهر كما هو عند تصدير التقرير كصورة أو PDF.
 * الضغط على شهر يستدعي [onMonthClick]، والشهر المختار مميَّز بخلفية.
 */
class CollectionChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMonthClick: ((YearMonth) -> Unit)? = null

    private var points: List<TrendPoint> = emptyList()
    private var selected: YearMonth? = null
    private var monthLabels: List<String> = emptyList()

    private val collectedColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
    // المستحق بلون المحصّل نفسه لكن شفاف: واضح في الوضعين الفاتح والداكن وفوق خلفية الشهر المختار
    private val billedColor = ColorUtils.setAlphaComponent(collectedColor, 0x5C)
    private val mutedText = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val strongText = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CollectionChartView, com.google.android.material.R.attr.colorOutlineVariant)
        strokeWidth = dp(1f)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = collectedColor
        alpha = 0x1F
    }
    private val labelPaint = textPaint(11f, mutedText)
    private val valuePaint = textPaint(10f, strongText)
    private val legendPaint = textPaint(12f, mutedText).apply { textAlign = Paint.Align.LEFT }

    private val legendCollected = context.getString(R.string.chart_collected)
    private val legendBilled = context.getString(R.string.chart_billed)

    private val barPath = Path()
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
        context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use {
            foreground = it.getDrawable(0)
        }
    }

    fun setData(points: List<TrendPoint>, selected: YearMonth?) {
        this.points = points
        this.selected = selected
        val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
        val format = SimpleDateFormat("MMM", locale)
        monthLabels = points.map { format.format(calendarOf(it.month).time) }
        contentDescription = points.joinToString("\n") {
            context.getString(
                R.string.chart_month_description,
                context.monthName(it.month), context.money(it.collected), context.money(it.billed)
            )
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resolveSize(dp(HEIGHT_DP).toInt(), heightMeasureSpec)
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        if (points.isEmpty()) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val legendHeight = legendPaint.textSize + dp(12f)
        val labelHeight = labelPaint.textSize + dp(10f)
        val chartTop = valuePaint.textSize + dp(8f)
        val chartBottom = height - legendHeight - labelHeight
        val slot = width.toFloat() / points.size
        val max = points.maxOf { maxOf(it.collected, it.billed) }.takeIf { it > 0 } ?: 1.0

        points.forEachIndexed { i, point ->
            val left = slotLeft(i, slot, rtl)
            val center = left + slot / 2
            val isSelected = point.month == selected
            if (isSelected) {
                rect.set(left + dp(2f), 0f, left + slot - dp(2f), chartBottom + labelHeight)
                canvas.drawRoundRect(rect, dp(8f), dp(8f), highlightPaint)
            }

            // المستحق أولًا من جهة البداية ثم المحصّل
            val barWidth = minOf(slot * 0.28f, dp(18f))
            val gap = dp(3f)
            val startBar = if (rtl) center + gap / 2 else center - gap / 2 - barWidth
            val endBar = if (rtl) center - gap / 2 - barWidth else center + gap / 2
            drawBar(canvas, startBar, barWidth, point.billed, max, chartTop, chartBottom, billedColor)
            val collectedTop = drawBar(canvas, endBar, barWidth, point.collected, max, chartTop, chartBottom, collectedColor)

            if (point.collected > 0) {
                canvas.drawText(Money.format(point.collected), endBar + barWidth / 2, collectedTop - dp(4f), valuePaint)
            }
            labelPaint.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            labelPaint.color = if (isSelected) collectedColor else mutedText
            canvas.drawText(monthLabels[i], center, chartBottom + labelHeight - dp(4f), labelPaint)
        }
        canvas.drawLine(0f, chartBottom, width.toFloat(), chartBottom, linePaint)
        drawLegend(canvas, height - dp(4f), rtl)
    }

    /** يرسم عمودًا بزوايا علوية مستديرة ويعيد أعلاه. */
    private fun drawBar(
        canvas: Canvas, left: Float, width: Float, value: Double, max: Double,
        top: Float, bottom: Float, color: Int
    ): Float {
        if (value <= 0) return bottom
        val height = maxOf(((bottom - top) * (value / max)).toFloat(), dp(2f))
        val radius = minOf(dp(4f), width / 2, height)
        rect.set(left, bottom - height, left + width, bottom)
        barPath.reset()
        barPath.addRoundRect(rect, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        barPaint.color = color
        canvas.drawPath(barPath, barPaint)
        return rect.top
    }

    /** مفتاح الألوان في المنتصف: ● المحصّل   ● المستحق (بترتيب القراءة). */
    private fun drawLegend(canvas: Canvas, baseline: Float, rtl: Boolean) {
        val dot = dp(8f)
        val inner = dp(6f)
        val between = dp(20f)
        val items = listOf(legendCollected to collectedColor, legendBilled to billedColor)
        val widths = items.map { (text, _) -> dot + inner + legendPaint.measureText(text) }
        var x = (width - (widths.sum() + between)) / 2
        val ordered = if (rtl) items.zip(widths).reversed() else items.zip(widths)
        for ((item, itemWidth) in ordered) {
            val (text, color) = item
            val dotLeft = if (rtl) x + itemWidth - dot else x
            val textLeft = if (rtl) x else x + dot + inner
            barPaint.color = color
            canvas.drawCircle(dotLeft + dot / 2, baseline - legendPaint.textSize / 3, dot / 2, barPaint)
            canvas.drawText(text, textLeft, baseline, legendPaint)
            x += itemWidth + between
        }
    }

    @SuppressLint("ClickableViewAccessibility") // performClick يُستدعى عند الضغط
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP && points.isNotEmpty() && width > 0) {
            val index = (event.x / (width.toFloat() / points.size)).toInt().coerceIn(0, points.size - 1)
            val pointIndex = if (layoutDirection == LAYOUT_DIRECTION_RTL) points.size - 1 - index else index
            onMonthClick?.invoke(points[pointIndex].month)
        }
        return handled
    }

    private fun slotLeft(index: Int, slot: Float, rtl: Boolean): Float =
        (if (rtl) points.size - 1 - index else index) * slot

    private fun textPaint(sp: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        this.color = color
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun calendarOf(month: YearMonth) = Calendar.getInstance().apply {
        clear()
        set(month.year, month.month - 1, 1)
    }

    private companion object {
        const val HEIGHT_DP = 200f
    }
}
