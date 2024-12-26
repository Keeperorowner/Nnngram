/*
 * Copyright (C) 2019-2024 qwq233 <qwq233@qwq2333.top>
 * https://github.com/qwq233/Nullgram
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this software.
 *  If not, see
 * <https://www.gnu.org/licenses/>
 */

package xyz.nextalone.nnngram.utils

import android.content.Context
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import org.telegram.messenger.UserConfig
import org.telegram.ui.LaunchActivity
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Log {
    const val TAG = "Nnngram"
    private const val ENABLE_RC_LOG = false
    private const val ENABLE_NATIVE_LOG = false
    private const val MAX_LOG_FILE_SIZE = 1024 * 1024 * 10 // 10MB

    // 日志控制
    private val loggingEnabled: Boolean
        get() = BuildVars.LOGS_ENABLED

    private var minimumLogLevel = Level.DEBUG

    enum class Level(val priority: Int) {
        DEBUG(0), INFO(1), WARN(2), ERROR(3), FATAL(4)
    }

    // 日志文件
    private val logFile: File by lazy {
        File(AndroidUtilities.getLogsDir(), "log-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.txt").also { f ->
            if (!f.exists()) {
                f.createNewFile()
                f.init()
            }
        }
    }

    private fun File.init() {
        appendText("Current version: ${BuildConfig.VERSION_NAME}\n")
        appendText("Device Brand: ${Build.BRAND}\n")
        appendText("Device: ${Build.MODEL}\n")
        appendText("Manufacturer: ${Build.MANUFACTURER}\n")
        appendText("OS: ${Build.VERSION.SDK_INT}\n")
        appendText("ABI: ${Utils.abi}\n")
        for (i in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            UserConfig.getInstance(i)?.let {
                if (!it.isClientActivated) return@let
                appendText("User $i: ${it.getClientUserId()}\n")
            }
        }
    }

    init {
        runCatching {
            val parentFile = AndroidUtilities.getLogsDir()
            CoroutineScope(Dispatchers.IO).launch {
                parentFile.listFiles()?.forEach {
                    // delete logs older than 1 day
                    if (it.readAttributes().creationTime().toMillis() < System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000) {
                        it.delete()
                    }
                }
            }
            if (loggingEnabled) {
                logFile.appendText(">>>> Log start at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}\n", Charset.forName("UTF-8"))
                logFile.appendText("Current version: ${BuildConfig.VERSION_NAME}\n")
            }
        }.onFailure {
            if (it is Exception && AndroidUtilities.isENOSPC(it)) {
                LaunchActivity.checkFreeDiscSpaceStatic(1)
            }
            e("Logger crashes", it)
        }
    }

    private fun log(level: Level, tag: String?, msg: String, throwable: Throwable? = null) {
        if (!loggingEnabled || level.priority < minimumLogLevel.priority) return
        if (msg.contains("{rc}") && !ENABLE_RC_LOG) return

        val logMessage = if (tag != null) "$tag: $msg" else msg
        when (level) {
            Level.DEBUG -> android.util.Log.d(TAG, logMessage, throwable)
            Level.INFO -> android.util.Log.i(TAG, logMessage, throwable)
            Level.WARN -> {
                android.util.Log.w(TAG, logMessage, throwable)
                FirebaseCrashlytics.getInstance().log(logMessage)
            }
            Level.ERROR, Level.FATAL -> {
                android.util.Log.e(TAG, logMessage, throwable)
                FirebaseCrashlytics.getInstance().log(logMessage)
                throwable?.let { AnalyticsUtils.trackCrashes(it) }
            }
        }

        writeToFile(level, tag, msg)
        throwable?.let { writeToFile(level, tag, it.stackTraceToString()) }
    }

    private fun writeToFile(level: Level, tag: String?, msg: String) {
        if (!loggingEnabled) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                logFile.apply {
                    if (!exists()) {
                        createNewFile()
                        setWritable(true)
                        init()
                        appendText(">>>> Log start at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}\n", Charset.forName("UTF-8"))
                        appendText("Current version: ${BuildConfig.VERSION_NAME}\n")
                    }
                    if (readAttributes().size() > MAX_LOG_FILE_SIZE) {
                        refreshLog()
                    }
                    appendText("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())} ${level.name} ${tag ?: ""}: $msg\n", Charset.forName("UTF-8"))
                }
            }.onFailure {
                if (it is Exception && AndroidUtilities.isENOSPC(it)) {
                    LaunchActivity.checkFreeDiscSpaceStatic(1)
                }
            }
        }
    }

    @JvmStatic
    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)

    @JvmStatic
    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)

    @JvmStatic
    fun w(tag: String, msg: String) = log(Level.WARN, tag, msg)

    @JvmStatic
    fun e(tag: String, msg: String) = log(Level.ERROR, tag, msg)

    @JvmStatic
    @JvmOverloads
    fun d(msg: String, throwable: Throwable? = null) = log(Level.DEBUG, null, msg, throwable)

    @JvmStatic
    @JvmOverloads
    fun i(msg: String, throwable: Throwable? = null) = log(Level.INFO, null, msg, throwable)

    @JvmStatic
    @JvmOverloads
    fun w(msg: String, throwable: Throwable? = null) = log(Level.WARN, null, msg, throwable)

    @JvmStatic
    @JvmOverloads
    fun e(msg: String, throwable: Throwable? = null) = log(Level.ERROR, null, msg, throwable)

    @JvmStatic
    fun w(throwable: Throwable) = log(Level.WARN, null, "", throwable)

    @JvmStatic
    fun e(throwable: Throwable) = log(Level.ERROR, null, "", throwable)

    @JvmStatic
    fun fatal(throwable: Throwable?) {
        if (throwable != null) {
            log(Level.FATAL, null, "", throwable)
        }
    }

    @JvmStatic
    fun shareLog(context: Context) {
        if (logFile.exists()) ShareUtil.shareFile(context, logFile)
    }

    @JvmStatic
    fun refreshLog() {
        synchronized(logFile) {
            runCatching {
                logFile.apply {
                    delete()
                    createNewFile()
                    init()
                    appendText(">>>> Log start at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}\n", Charset.forName("UTF-8"))
                    appendText("Current version: ${BuildConfig.VERSION_NAME}\n")
                }
            }
        }
    }

    // 崩溃相关方法
    @JvmStatic
    @JvmOverloads
    fun crash(throwable: Throwable? = null) {
        if (!loggingEnabled) return
        throw throwable ?: NullPointerException("manual crash")
    }

    @JvmStatic
    fun throwException() = try {
        throw NullPointerException("manual crash")
    } catch (e: Exception) {
        w(e)
    }

    @JvmStatic
    fun nativeLog(level: Int, tag: String, msg: String) {
        if (!loggingEnabled || !ENABLE_NATIVE_LOG) return
        if (tag == "Nnngram") {
            when(level) {
                0 -> d("tgnet", msg)
                1 -> i("tgnet", msg)
                2 -> w("tgnet", msg)
                3 -> e("tgnet", msg)
            }
        }
        when(level) {
            0 -> android.util.Log.d("tgnet", "$tag: $msg")
            1 -> android.util.Log.i("tgnet", "$tag: $msg")
            2 -> android.util.Log.w("tgnet", "$tag: $msg")
            3 -> android.util.Log.e("tgnet", "$tag: $msg")
        }
    }

    // 日志级别控制
    @JvmStatic
    fun setMinimumLogLevel(level: Level) {
        minimumLogLevel = level
    }
}
