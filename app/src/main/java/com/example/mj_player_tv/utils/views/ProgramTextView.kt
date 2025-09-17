package com.example.mj_player_tv.utils.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.example.mj_player_tv.R
import androidx.core.graphics.withClip

@SuppressLint("AppCompatCustomView", "ResourceType")
class ProgramTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    // Optionen aus Tivimate
    private val drawDivider: Boolean
    private val rounded: Boolean

    private val paintHighlight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintProgress = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintDivider = Paint(Paint.ANTI_ALIAS_FLAG)

    private val dividerSize = 2  // px, Beispiel
    private val paddingMin = 12  // px, minimaler Padding
    private val paddingMax = 18  // px, maximaler Padding
    private var path: Path? = null

    private var highlighted = false
    private var progressWidth = 0f
    var isAdjustingPadding = false

    init {
        // Attributes aus XML
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ProgramTextView, defStyleAttr, 0)
        drawDivider = ta.getBoolean(R.styleable.ProgramTextView_ptv_drawDivider, true)
        rounded = ta.getBoolean(R.styleable.ProgramTextView_ptv_rounded, false)
        val progressAlpha = ta.getInteger(R.styleable.ProgramTextView_ptv_progressAlpha, 12)
        ta.recycle()

        // Paint Setup
        paintHighlight.style = Paint.Style.FILL
        paintHighlight.color = context.getColor(R.color.program_selection_overlay)

        paintProgress.style = Paint.Style.FILL
        paintProgress.color = -0x1
        paintProgress.alpha = progressAlpha
        val scale = resources.displayMetrics.density
        val paddingLeftPx = (8 * scale + 0.5f).toInt()
        setPadding(paddingLeftPx, paddingTop, paddingLeftPx, paddingBottom)
        paintDivider.style = Paint.Style.FILL
        paintDivider.color = context.getColor(R.color.divider)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) // default kleiner
        isSingleLine = true
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        background = ContextCompat.getDrawable(context, R.drawable.channel_item_background)
    }

    override fun onDraw(canvas: Canvas) {
        // Highlight
        if (highlighted) {
            if (rounded) {
                path?.let { canvas.drawPath(it, paintHighlight) }
            } else {
                canvas.drawRect(
                    0f,
                    0f,
                    if (drawDivider) width - dividerSize.toFloat() else width.toFloat(),
                    height.toFloat(),
                    paintHighlight
                )
            }
        } else if (drawDivider) {
            canvas.drawRect(
                width - dividerSize.toFloat(),
                0f,
                width.toFloat(),
                height.toFloat(),
                paintDivider
            )
        }

        // Progress Overlay
        if (progressWidth > 0f) {
            if (rounded) {
                path?.let {
                    canvas.withClip(0f, 0f, progressWidth, height.toFloat()) {
                        drawPath(it, paintProgress)
                    }
                }
            } else {
                var w = progressWidth
                if (drawDivider && progressWidth == width.toFloat()) w -= dividerSize
                canvas.drawRect(0f, 0f, w, height.toFloat(), paintProgress)
            }
        }

        super.onDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (rounded && w != 0 && h != 0) {
            val r = 2f
            if (path == null) path = Path()
            path?.apply {
                reset()
                moveTo(0f, 0f)
                rLineTo(w.toFloat(), 0f)
                rLineTo(0f, h - r)
                rQuadTo(0f, r, -r, r)
                rLineTo(-w + 2 * r, 0f)
                rQuadTo(-r, 0f, -r, -r)
                close()
            }
        }
    }

    override fun requestLayout() {
        if (isAdjustingPadding) forceLayout() else super.requestLayout()
    }

    fun setHighlighted(value: Boolean) {
        if (highlighted != value) {
            highlighted = value
            paintProgress.alpha = if (value) 28 else paintProgress.alpha
            invalidate()
        }
    }

    fun setProgressWidth(width: Float) {
        if (progressWidth != width) {
            progressWidth = width
            invalidate()
        }
    }

    fun setTextColorId(colorRes: Int) {
        setTextColor(context.getColor(colorRes))
    }

    fun setTextSizeSp(size: Float) {
        textSize = size
    }

    fun setMaxLineCount(maxLines: Int) {
        setMaxLines(maxLines)
    }
}
