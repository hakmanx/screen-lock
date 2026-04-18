package com.example.screenlock

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    private lateinit var btnBackLogs: ImageButton
    private lateinit var txtLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        btnBackLogs = findViewById(R.id.btnBackLogs)
        txtLogs = findViewById(R.id.txtLogs)

        btnBackLogs.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val logs = LogStore.read(this).ifBlank { "Событий пока нет." }
        txtLogs.text = logs
    }
}
