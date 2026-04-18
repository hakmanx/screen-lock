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
    private lateinit var btnOpenLogs: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var txtSelected: TextView
    private lateinit var txtMonitorState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggleMonitoring = findViewById(R.id.btnToggleMonitoring)
        btnChooseDevice = findViewById(R.id.btnChooseDevice)
        btnOpenLogs = findViewById(R.id.btnOpenLogs)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        txtSelected = findViewById(R.id.txtSelected)
        txtMonitorState = findViewById(R.id.txtMonitorState)

        prefs = getSharedPreferences("bt_lock_prefs", Context.MODE_PRIVATE)
        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        btnToggleMonitoring.setOnClickListener {
            toggleMonitoring()
        }

        btnChooseDevice.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        btnOpenLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        txtSelected.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }

        updateSelected()
        updateMonitoringUi()
    }

    override fun onResume() {
        super.onResume()
        updateSelected()
        updateMonitoringUi()
    }

    private fun updateSelected() {
        val name = prefs.getString("target_name", "")?.trim().orEmpty()
        val mac = prefs.getString("target_mac", null)

        txtSelected.text = when {
            name.isNotEmpty() -> name
            mac != null -> mac
            else -> "Нет выбранного устройства"
        }
    }

    private fun toggleMonitoring() {
        val mac = prefs.getString("target_mac", null)
        if (mac == null) {
            LogStore.append(this, "Включение защиты отклонено: устройство не выбрано")
            Toast.makeText(
                this,
                "Сначала выберите Bluetooth-устройство",
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
