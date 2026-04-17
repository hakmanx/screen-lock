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
        val oldLogs = prefs.getString(KEY_LOGS, "") ?: ""
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newEntry = "[$time] $message\n"
        var result = oldLogs + newEntry

        if (result.length > MAX_LEN) {
            result = result.takeLast(MAX_LEN)
        }

        prefs.edit().putString(KEY_LOGS, result).apply()
    }

    fun read(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGS, "") ?: ""
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LOGS).apply()
    }
}
