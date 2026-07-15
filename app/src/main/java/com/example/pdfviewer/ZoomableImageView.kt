package com.example.pdfviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * ImageView that supports pinch-to-zoom, drag-to-pan and double-tap zoom, plus
 * drawing search-result highlight rectangles on top of the rendered page.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrix = Matrix()
    private var scaleFactor = 1f
    private var minScale = 1f
    private var maxScale = 6f
    private var posX = 0f
    private var posY = 0f
    private var containerWidth = 0f
    private var containerHeight = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    private val highlightPaint = Paint().apply {
        color = 0x66FFEB3B.toInt()
        style = Paint.Style.FILL
    }
    private var highlights: List<RectF> = emptyList()

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        isFocusable = true
    }

    fun setHighlights(rects: List<RectF>) {
        highlights = rects
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        containerWidth = w.toFloat()
        containerHeight = h.toFloat()
        if (drawable != null) fitToContainer()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        if (bm != null && containerWidth > 0) fitToContainer()
    }

    private fun fitToContainer() {
        val d = drawable ?: return
        val bw = d.intrinsicWidth.toFloat()
        val bh = d.intrinsicHeight.toFloat()
        if (bw <= 0 || bh <= 0 || containerWidth <= 0) return
        val scale = min(containerWidth / bw, containerHeight / bh)
        minScale = scale
        scaleFactor = scale
        posX = (containerWidth - bw * scale) / 2f
        posY = (containerHeight - bh * scale) / 2f
        applyMatrix()
        invalidate()
    }

    private fun applyMatrix() {
        matrix.setScale(scaleFactor, scaleFactor)
        matrix.postTranslate(posX, posY)
        imageMatrix = matrix
    }

    private fun clampPan() {
        val d = drawable ?: return
        val bw = d.intrinsicWidth * scaleFactor
        val bh = d.intrinsicHeight * scaleFactor
        posX = if (bw <= containerWidth) (containerWidth - bw) / 2f
        else posX.coerceIn(containerWidth - bw, 0f)
        posY = if (bh <= containerHeight) (containerHeight - bh) / 2f
        else posY.coerceIn(containerHeight - bh, 0f)
    }

    private fun isZoomed(): Boolean = scaleFactor > minScale + 0.01f

    private fun setParentIntercept(disallow: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallow)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                setParentIntercept(isZoomed())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                setParentIntercept(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress || event.pointerCount > 1) {
                    setParentIntercept(true)
                } else if (event.pointerCount == 1) {
                    setParentIntercept(isZoomed())
                    posX += event.x - lastX
                    posY += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    clampPan()
                    applyMatrix()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setParentIntercept(isZoomed())
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (highlights.isEmpty()) return
        val tmp = RectF()
        for (r in highlights) {
            tmp.set(r)
            matrix.mapRect(tmp)
            canvas.drawRect(tmp, highlightPaint)
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prev = scaleFactor
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            val focusY = detector.focusY
            posX = focusX - (focusX - posX) * (scaleFactor / prev)
            posY = focusY - (focusY - posY) * (scaleFactor / prev)
            clampPan()
            applyMatrix()
            setParentIntercept(true)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val zoomIn = !isZoomed()
            scaleFactor = if (zoomIn) min(maxScale, minScale * 2.5f) else minScale
            val d = drawable ?: return true
            posX = (containerWidth - d.intrinsicWidth * scaleFactor) / 2f
            posY = (containerHeight - d.intrinsicHeight * scaleFactor) / 2f
            clampPan()
            applyMatrix()
            setParentIntercept(isZoomed())
            return true
        }
    }
}
