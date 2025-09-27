package com.example.lilspeaker.core.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AppLogger {
    private const val TAG = "LilSpeaker"
    private val timestampFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun i(
        sourceClass: String,
        function: String,
        systemSection: String,
        message: String,
        lineNumber: Int = -1,
        method: String = "NONE",
        dbPhase: String = "none",
        error: String? = null
    ) {
        log(
            level = Log.INFO,
            sourceClass = sourceClass,
            function = function,
            systemSection = systemSection,
            message = message,
            lineNumber = lineNumber,
            method = method,
            dbPhase = dbPhase,
            error = error
        )
    }

    fun e(
        sourceClass: String,
        function: String,
        systemSection: String,
        message: String,
        throwable: Throwable,
        lineNumber: Int = -1,
        method: String = "NONE",
        dbPhase: String = "none"
    ) {
        log(
            level = Log.ERROR,
            sourceClass = sourceClass,
            function = function,
            systemSection = systemSection,
            message = message,
            lineNumber = lineNumber,
            method = method,
            dbPhase = dbPhase,
            error = throwable.message
        )
    }

    private fun log(
        level: Int,
        sourceClass: String,
        function: String,
        systemSection: String,
        message: String,
        lineNumber: Int,
        method: String,
        dbPhase: String,
        error: String?
    ) {
        val payload = mapOf(
            "filename" to sourceClass,
            "timestamp" to timestampFormatter.get().format(Date()),
            "classname" to sourceClass,
            "function" to function,
            "system_section" to systemSection,
            "line_num" to lineNumber,
            "error" to (error ?: ""),
            "db_phase" to dbPhase,
            "method" to method,
            "message" to message
        )
        val jsonLine = payload.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"$key\":\"${value.toString().replace("\"", "\\\"")}\""
        }
        Log.println(level, TAG, jsonLine)
        Log.println(level, TAG, "[Continuous skepticism (Sherlock Protocol)] $message")
    }
}
