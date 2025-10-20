
package com.screenmask

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.os.IBinder

class OverlayService : Service() {

    companion object {
        private const val TAG = "ScreenMask/Overlay"
    }

    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableListOf<View>()
    private val ruleManager by lazy { RuleManager(this) }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val hasPerm = Settings.canDrawOverlays(this)
        Log.i(TAG, "onCreate: canDrawOverlays=$hasPerm")
        if (!hasPerm) {
            Log.w(TAG, "No overlay permission, stopping self")
            stopSelf()
            return
        }
        // 先清一遍，避免重复叠加
        safeClearAll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rules = ruleManager.getRules()
        Log.i(TAG, "onStartCommand: startId=$startId, rules=${rules.size}, enabled=${rules.count { it.enabled }}")
        createOverlays()
        return START_NOT_STICKY
    }

    private fun createOverlays() {
        safeClearAll()

        val enabledRules = ruleManager.getRules().filter { it.enabled }
        Log.i(TAG, "createOverlays: enabled=${enabledRules.size}")

        enabledRules.forEach { rule ->
            val width = (rule.right - rule.left).coerceAtLeast(0)
            val height = (rule.bottom - rule.top).coerceAtLeast(0)
            Log.d(TAG, "add overlay: id=${rule.id} rect=(${rule.left},${rule.top},${rule.right},${rule.bottom}) size=${width}x$height color=${rule.color}")

            if (width <= 0 || height <= 0) {
                Log.w(TAG, "skip invalid rect: width=$width height=$height")
                return@forEach
            }

            val view = View(this)
            view.setBackgroundColor(rule.color)

            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            params.x = rule.left
            params.y = rule.top

            try {
                windowManager.addView(view, params)
                overlayViews.add(view)
            } catch (e: Exception) {
                Log.e(TAG, "addView failed for id=${rule.id}", e)
            }
        }
    }

    private fun safeClearAll() {
        overlayViews.forEach {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayViews.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: clearing ${overlayViews.size} views")
        safeClearAll()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}