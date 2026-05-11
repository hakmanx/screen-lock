package com.example.screenlock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private const val PREFS_NAME = "bt_lock_logs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LEN = 50000

    fun append(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val oldLogs = normalizeNewestFirst(prefs.getString(KEY_LOGS, "") ?: "")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newEntry = "[$time] $message\n"

        var result = newEntry + oldLogs

        if (result.length > MAX_LEN) {
            result = result.take(MAX_LEN)
        }

        prefs.edit().putString(KEY_LOGS, result).apply()
    }

    fun read(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalizeNewestFirst(prefs.getString(KEY_LOGS, "") ?: "")
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun normalizeNewestFirst(raw: String): String {
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return raw

        val firstTime = extractTimestamp(lines.first())
        val lastTime = extractTimestamp(lines.last())

        return if (firstTime != null && lastTime != null && firstTime < lastTime) {
            lines.asReversed().joinToString("\n") + "\n"
        } else {
            raw
        }
    }

    private fun extractTimestamp(line: String): String? {
        if (!line.startsWith("[") || line.length < 21) return null
        return line.substring(1, 20)
    }
}
