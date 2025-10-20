
package com.screenmask

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object FileLogger {
    private lateinit var app: Context
    private var logDir: File? = null
    private var writer: BufferedWriter? = null

    private val fmtDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val fmtTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private const val PREFS = "debug_prefs"
    private const val KEY_LAST_CRASH = "last_crash_path"
    private const val MAX_LOG_SIZE = 524_288 // 512 KB

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val dir = app.getExternalFilesDir("logs") ?: File(app.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logDir = dir
        openWriter()
        log("FileLogger", "init, dir=${dir.absolutePath}")
    }

    @Synchronized
    private fun openWriter() {
        try {
            val base = logDir ?: return
            val f = File(base, "current.log")
            if (f.exists() && f.length() > MAX_LOG_SIZE) {
                val rotated = File(base, "app-${fmtDate.format(Date())}.log")
                f.copyTo(rotated, overwrite = true)
                f.writeText("")
            }
            writer?.close()
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(f, true), Charsets.UTF_8))
        } catch (_: Exception) {}
    }

    @Synchronized
    fun log(tag: String, msg: String, tr: Throwable? = null) {
        Log.i(tag, msg, tr)
        try {
            if (writer == null) openWriter()
            writer?.write("${fmtTime.format(Date())} $tag: $msg\n")
            if (tr != null) {
                writer?.write(Log.getStackTraceString(tr))
                writer?.write("\n")
            }
            writer?.flush()
        } catch (_: Exception) {}
    }

    fun writeCrash(t: Throwable) {
        try {
            val dir = logDir ?: return
            val f = File(dir, "crash-${fmtDate.format(Date())}.log")
            f.writeText("${t.javaClass.name}: ${t.message}\n${Log.getStackTraceString(t)}")
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_CRASH, f.absolutePath).apply()
        } catch (_: Exception) {}
    }
}

class App : Application() {
    private var prev: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        prev = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                FileLogger.log("Crash", "Uncaught on thread=${t.name}", e)
                FileLogger.writeCrash(e)
            } catch (_: Exception) {}
            prev?.uncaughtException(t, e) ?: run {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }
}