
package com.screenmask

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SelectAreaActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenMask/Select"
    }

    private lateinit var selectionView: SelectionView
    private val ruleManager by lazy { RuleManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate()")
        window.setFormat(PixelFormat.TRANSLUCENT)

        selectionView = SelectionView(this) { left, top, right, bottom ->
            Log.i(TAG, "onAreaSelected: rect=($left,$top,$right,$bottom)")
            val color = captureColor(left, top, right, bottom)
            Log.i(TAG, "captured color: #${Integer.toHexString(color).uppercase()}")
            saveRule(left, top, right, bottom, color)
        }

        setContentView(selectionView)
    }

    private fun captureColor(left: Int, top: Int, right: Int, bottom: Int): Int {
        try {
            val rootView = window.decorView.rootView
            val w = rootView.width
            val h = rootView.height
            Log.d(TAG, "captureColor: rootView size=${w}x$h")

            if (w <= 0 || h <= 0) {
                Log.w(TAG, "rootView size invalid, fallback color")
                return Color.parseColor("#80000000")
            }

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)

            val centerX = (left + right) / 2
            val centerY = (top + bottom) / 2

            if (centerX in 0 until bitmap.width && centerY in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(centerX, centerY)
                bitmap.recycle()
                return pixel
            } else {
                Log.w(TAG, "center out of bounds: ($centerX,$centerY) not in 0..${bitmap.width}x${bitmap.height}")
            }

            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "captureColor failed", e)
        }

        return Color.parseColor("#80000000")
    }

    private fun saveRule(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        val rule = Rule(
            id = System.currentTimeMillis(),
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            color = color,
            enabled = true
        )

        Log.i(TAG, "saveRule id=${rule.id} rect=($left,$top,$right,$bottom) color=#${Integer.toHexString(color).uppercase()}")
        ruleManager.addRule(rule)

        runOnUiThread {
            Toast.makeText(this, "已保存遮挡规则", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

class SelectionView(
    context: android.content.Context,
    private val onAreaSelected: (Int, Int, Int, Int) -> Unit
) : View(context) {

    companion object {
        private const val TAG = "ScreenMask/SelectView"
    }

    private val paint = Paint().apply {
        color = Color.parseColor("#4000FF00")
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isSelecting = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                isSelecting = true
                Log.d(TAG, "ACTION_DOWN: ($startX,$startY)")
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    endX = event.x
                    endY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isSelecting) {
                    endX = event.x
                    endY = event.y
                    isSelecting = false

                    val left = minOf(startX, endX).toInt()
                    val top = minOf(startY, endY).toInt()
                    val right = maxOf(startX, endX).toInt()
                    val bottom = maxOf(startY, endY).toInt()
                    Log.d(TAG, "ACTION_UP: rect=($left,$top,$right,$bottom)")

                    if (right - left > 10 && bottom - top > 10) {
                        onAreaSelected(left, top, right, bottom)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isSelecting || (endX != startX && endY != startY)) {
            val left = minOf(startX, endX)
            val top = minOf(startY, endY)
            val right = maxOf(startX, endX)
            val bottom = maxOf(startY, endY)

            canvas.drawRect(left, top, right, bottom, paint)
            canvas.drawRect(left, top, right, bottom, strokePaint)
        }
    }
}