package com.example.screenlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private lateinit var btnToggleMonitoring: Button
    private lateinit var btnChooseDevice: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var txtSelected: TextView
    private lateinit var txtMonitorState: TextView
    private lateinit var txtDelayValue: TextView
    private lateinit var navHome: TextView
    private lateinit var navDevices: TextView
    private lateinit var navJournal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(TrustedDevices.PREFS_NAME, Context.MODE_PRIVATE)
        TrustedDevices.migrateLegacyIfNeeded(this)

        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        btnToggleMonitoring = findViewById(R.id.btnToggleMonitoring)
        btnChooseDevice = findViewById(R.id.btnChooseDevice)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        txtSelected = findViewById(R.id.txtSelected)
        txtMonitorState = findViewById(R.id.txtMonitorState)
        txtDelayValue = findViewById(R.id.txtDelayValue)
        navHome = findViewById(R.id.navHome)
        navDevices = findViewById(R.id.navDevices)
        navJournal = findViewById(R.id.navJournal)

        btnToggleMonitoring.setOnClickListener {
            toggleMonitoring()
        }

        btnChooseDevice.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        txtSelected.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        navHome.setOnClickListener {
            // Уже на главной.
        }

        navDevices.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        navJournal.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        updateAllUi()
    }

    override fun onResume() {
        super.onResume()

        TrustedDevices.migrateLegacyIfNeeded(this)

        updateAllUi()
    }

    private fun updateAllUi() {
        updateSelected()
        updateDelayInfo()
        updateMonitoringUi()
    }

    private fun updateSelected() {
        val count = TrustedDevices.selectedMacs(this).size

        txtSelected.text = when (count) {
            0 -> "Нет выбранных устройств"
            1 -> "Выбрано 1 устройство:\n${TrustedDevices.summary(this)}"
            in 2..4 -> "Выбрано $count устройства:\n${TrustedDevices.summary(this)}"
            else -> "Выбрано $count устройств:\n${TrustedDevices.summary(this)}"
        }
    }

    private fun updateDelayInfo() {
        val delay = prefs.getInt("lock_delay_seconds", 0).coerceIn(0, 300)
        txtDelayValue.text = "Задержка: $delay сек"
    }

    private fun toggleMonitoring() {
        if (!TrustedDevices.hasSelection(this)) {
            LogStore.append(this, "Включение защиты отклонено: доверенные устройства не выбраны")

            Toast.makeText(
                this,
                "Сначала выберите одно или несколько Bluetooth-устройств",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (!dpm.isAdminActive(adminComponent)) {
            LogStore.append(this, "Включение защиты отклонено: Device Admin не активен")

            Toast.makeText(
                this,
                "Сначала включите Device Admin в настройках",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val enabled = prefs.getBoolean("monitoring_enabled", false)

        if (enabled) {
            stopService(Intent(this, BtMonitorService::class.java))
            prefs.edit().putBoolean("monitoring_enabled", false).apply()

            LogStore.append(this, "Защита выключена пользователем")
            Toast.makeText(this, "Защита выключена", Toast.LENGTH_SHORT).show()
        } else {
            ContextCompat.startForegroundService(this, Intent(this, BtMonitorService::class.java))
            prefs.edit().putBoolean("monitoring_enabled", true).apply()

            LogStore.append(this, "Защита включена пользователем")
            Toast.makeText(this, "Защита включена", Toast.LENGTH_SHORT).show()
        }

        updateMonitoringUi()
    }

    private fun updateMonitoringUi() {
        val enabled = prefs.getBoolean("monitoring_enabled", false)

        if (enabled) {
            txtMonitorState.text = "Защита активна"
            txtMonitorState.setTextColor(
                ContextCompat.getColor(this, R.color.guard_red_light)
            )
            btnToggleMonitoring.text = "Отключить защиту"
        } else {
            txtMonitorState.text = "Защита неактивна"
            txtMonitorState.setTextColor(
                ContextCompat.getColor(this, R.color.guard_text_muted)
            )
            btnToggleMonitoring.text = "Включить защиту"
        }
    }
}
