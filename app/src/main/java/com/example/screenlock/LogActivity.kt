package com.example.screenlock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    private lateinit var txtLogs: TextView
    private lateinit var btnBackLog: Button
    private lateinit var btnCopyLogs: Button
    private lateinit var btnClearLogsScreen: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_log)

        txtLogs = findViewById(R.id.txtLogs)
        btnBackLog = findViewById(R.id.btnBackLog)
        btnCopyLogs = findViewById(R.id.btnCopyLogs)
        btnClearLogsScreen = findViewById(R.id.btnClearLogsScreen)

        btnBackLog.setOnClickListener { finish() }
        btnCopyLogs.setOnClickListener { copyLogs() }
        btnClearLogsScreen.setOnClickListener {
            LogStore.clear(this)
            refreshLogs()
            Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT).show()
        }

        refreshLogs()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun refreshLogs() {
        val logs = LogStore.read(this)
        txtLogs.text = logs.ifBlank { "Журнал пуст" }
    }

    private fun copyLogs() {
        val logs = LogStore.read(this)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("IronLink logs", logs))
        Toast.makeText(this, "Журнал скопирован", Toast.LENGTH_SHORT).show()
    }
}
