package com.example.screenlock

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    private lateinit var btnBackLogs: ImageButton
    private lateinit var logContainer: LinearLayout

    private data class LogEvent(
        val title: String,
        val description: String,
        val time: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        btnBackLogs = findViewById(R.id.btnBackLogs)
        logContainer = findViewById(R.id.logContainer)

        btnBackLogs.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        renderLogs()
    }

    private fun renderLogs() {
        logContainer.removeAllViews()

        val rawLogs = LogStore.read(this).trim()
        val events = parseLogs(rawLogs)

        if (events.isEmpty()) {
            addEventCard(
                LogEvent(
                    title = "Событий пока нет",
                    description = "После выбора устройства и запуска защиты здесь появится история соединения и блокировки.",
                    time = "--:--"
                )
            )
            return
        }

        events.forEach { addEventCard(it) }
    }

    private fun addEventCard(event: LogEvent) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_log_event, logContainer, false)
        view.findViewById<TextView>(R.id.txtEventTitle).text = event.title
        view.findViewById<TextView>(R.id.txtEventDesc).text = event.description
        view.findViewById<TextView>(R.id.txtEventTime).text = event.time
        logContainer.addView(view)
    }

    private fun parseLogs(rawLogs: String): List<LogEvent> {
        if (rawLogs.isBlank()) return emptyList()

        return rawLogs
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .asReversed()
            .map { line ->
                val endBracket = line.indexOf("] ")
                val timestamp = if (line.startsWith("[") && endBracket > 1) {
                    line.substring(1, endBracket)
                } else {
                    ""
                }

                val message = if (endBracket > 1) {
                    line.substring(endBracket + 2).trim()
                } else {
                    line.trim()
                }

                val time = if (timestamp.length >= 16) {
                    timestamp.substring(11, 16)
                } else {
                    "--:--"
                }

                val title = when {
                    message.contains("Срабатывание блокировки", ignoreCase = true) ||
                        message.contains("Экран заблокирован", ignoreCase = true) -> {
                        "Экран заблокирован"
                    }

                    message.contains("Выбранное устройство отключено", ignoreCase = true) ||
                        message.contains("Отключено:", ignoreCase = true) -> {
                        "Потеряна связь"
                    }

                    message.contains("Выбранное устройство подключено", ignoreCase = true) ||
                        message.contains("Подключено:", ignoreCase = true) -> {
                        "Связь восстановлена"
                    }

                    message.contains("Выбрано устройство:", ignoreCase = true) -> {
                        "Устройство выбрано"
                    }

                    message.contains("Защита включена", ignoreCase = true) -> {
                        "Защита включена"
                    }

                    message.contains("Защита выключена", ignoreCase = true) -> {
                        "Защита выключена"
                    }

                    else -> "Системное событие"
                }

                LogEvent(
                    title = title,
                    description = message,
                    time = time
                )
            }
    }
}
