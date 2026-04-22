package com.example.screenlock

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    private lateinit var btnBackLogs: ImageButton
    private lateinit var txtLogs: TextView

    private lateinit var navHome: TextView
    private lateinit var navDevices: TextView
    private lateinit var navJournal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        btnBackLogs = findViewById(R.id.btnBackLogs)
        txtLogs = findViewById(R.id.txtLogs)

        navHome = findViewById(R.id.navHome)
        navDevices = findViewById(R.id.navDevices)
        navJournal = findViewById(R.id.navJournal)

        btnBackLogs.setOnClickListener {
            finish()
        }

        navHome.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        }

        navDevices.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        navJournal.setOnClickListener {
            // уже на журнале
        }
    }

    override fun onResume() {
        super.onResume()
        val logs = LogStore.read(this).ifBlank { "Событий пока нет." }
        txtLogs.text = logs
    }
}
