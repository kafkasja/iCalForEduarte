package dev.eduartesync.ui.portal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

/**
 * A donut ring with a percentage in the middle, for the attendance header. Drawn by hand: the
 * app has no chart library, and a ring with a label is all it needs.
 */
class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 0 to 100; null draws an empty ring with a dash. */
    var percent: Double? = null
        set(value) {
            field = value?.coerceIn(0.0, 100.0)
            invalidate()
        }

    /** Color of the filled part (and of the number). */
    var color: Int = MaterialColors.getColor(this, MaterialR.attr.colorPrimary)
        set(value) {
            field = value
            invalidate()
        }

    /** The small word under the number. */
    var caption: String = "present"
        set(value) {
            field = value
            invalidate()
        }

    private val stroke = dp(12f)
    private val bounds = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = MaterialColors.getColor(this@RingView, MaterialR.attr.colorSurfaceContainerHighest)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(28f)
        fontFeatureSettings = "tnum"
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
        color = MaterialColors.getColor(this@RingView, MaterialR.attr.colorOnSurfaceVariant)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            resolveSize(dp(DEFAULT_SIZE_DP).toInt(), widthMeasureSpec),
            resolveSize(dp(DEFAULT_SIZE_DP).toInt(), heightMeasureSpec),
        )
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val half = stroke / 2
        bounds.set(paddingLeft + half, paddingTop + half, width - paddingRight - half, height - paddingBottom - half)
        canvas.drawOval(bounds, trackPaint)

        val value = percent
        if (value != null && value > 0.0) {
            arcPaint.color = color
            canvas.drawArc(bounds, -90f, (value * 3.6).toFloat(), false, arcPaint)
        }

        numberPaint.color = if (value == null) captionPaint.color else color
        val cx = bounds.centerX()
        val number = value?.let { "${Math.round(it)}%" } ?: "–"
        val numberBaseline = bounds.centerY() + numberPaint.textSize * 0.3f
        canvas.drawText(number, cx, numberBaseline, numberPaint)
        canvas.drawText(caption, cx, numberBaseline + captionPaint.textSize * 1.5f, captionPaint)
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val DEFAULT_SIZE_DP = 120f
    }
}
