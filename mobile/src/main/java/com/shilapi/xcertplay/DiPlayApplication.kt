package com.shilapi.xcertplay

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.multidex.MultiDexApplication
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiPlayApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(this, thread, throwable)
            } catch (ignored: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        private const val TAG = "DiPlayApplication"

        fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val stackTrace = stringWriter.toString()

            val logEntry = buildString {
                append("\n================ FATAL EXCEPTION ================\n")
                append("Time: ").append(timestamp).append("\n")
                append("Thread: ").append(thread.name).append(" (id=").append(thread.id).append(")\n")
                append("Android SDK: ").append(Build.VERSION.SDK_INT).append("\n")
                append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
                append("Exception: ").append(stackTrace).append("\n")
                append("=================================================\n")
            }

            Log.e(TAG, logEntry)

            runCatching {
                val logDir = File(context.filesDir, "logs")
                if (!logDir.exists()) logDir.mkdirs()
                val logFile = File(logDir, "diplay_crash.log")
                logFile.appendText(logEntry)
            }
        }
    }
}
