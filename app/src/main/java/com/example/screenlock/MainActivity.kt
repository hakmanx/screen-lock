package com.example.screenlock

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private lateinit var listView: ListView
    private lateinit var btnEnableAdmin: Button
    private lateinit var btnToggleMonitoring: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var txtSelected: TextView
    private lateinit var txtMonitorState: TextView

    private var currentDevices: List<BluetoothDevice> = emptyList()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadBondedDevices()
            } else {
                Toast.makeText(this, "Нужно разрешение BLUETOOTH_CONNECT", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listView)
        btnEnableAdmin = findViewById(R.id.btnEnableAdmin)
        btnToggleMonitoring = findViewById(R.id.btnToggleMonitoring)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        txtSelected = findViewById(R.id.txtSelected)
        txtMonitorState = findViewById(R.id.txtMonitorState)

        prefs = getSharedPreferences("bt_lock_prefs", Context.MODE_PRIVATE)
        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        btnEnableAdmin.setOnClickListener {
            requestDeviceAdmin()
        }

        btnToggleMonitoring.setOnClickListener {
            toggleMonitoring()
        }

        btnOpenSettings.setOnClickListener {
            Toast.makeText(
                this,
                "Экран настроек добавим следующим шагом",
                Toast.LENGTH_SHORT
            ).show()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = currentDevices[position]

            prefs.edit()
                .putString("target_mac", device.address)
                .putString("target_name", device.name ?: "")
                .apply()

            LogStore.append(this, "Выбрано устройство: ${device.name ?: "Без имени"} ${device.address}")
            updateSelected()
            loadBondedDevices()
            Toast.makeText(this, "Выбрано устройство", Toast.LENGTH_SHORT).show()
        }

        checkPermissionAndLoad()
        updateSelected()
        updateMonitoringUi()
    }

    override fun onResume() {
        super.onResume()
        updateSelected()
        updateMonitoringUi()
        checkPermissionAndLoad()
    }

    private fun checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                loadBondedDevices()
            } else {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            loadBondedDevices()
        }
    }

    private fun loadBondedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val selectedMac = prefs.getString("target_mac", null)

        val devices = adapter?.bondedDevices
            ?.toList()
            ?.sortedWith(
                compareByDescending<BluetoothDevice> { it.address == selectedMac }
                    .thenBy { displayName(it).lowercase() }
            )
            .orEmpty()

        currentDevices = devices

        val items = devices.map { device ->
            val name = displayName(device)
            if (device.address == selectedMac) {
                "★ $name"
            } else {
                name
            }
        }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items
        )
    }

    private fun displayName(device: BluetoothDevice): String {
        val name = device.name?.trim().orEmpty()
        return if (name.isNotEmpty()) name else device.address
    }

    private fun updateSelected() {
        val name = prefs.getString("target_name", "")?.trim().orEmpty()
        val mac = prefs.getString("target_mac", null)

        txtSelected.text = when {
            name.isNotEmpty() -> name
            mac != null -> mac
            else -> "Не выбрано"
        }
    }

    private fun requestDeviceAdmin() {
        LogStore.append(this, "Запрошено включение Device Admin")
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Нужно для блокировки экрана при отключении выбранного Bluetooth-устройства"
            )
        }
        startActivity(intent)
    }

    private fun toggleMonitoring() {
        val mac = prefs.getString("target_mac", null)

        if (mac == null) {
            LogStore.append(this, "Переключение мониторинга отклонено: устройство не выбрано")
            Toast.makeText(this, "Сначала выберите Bluetooth-устройство", Toast.LENGTH_LONG).show()
            return
        }

        if (!dpm.isAdminActive(adminComponent)) {
            LogStore.append(this, "Переключение мониторинга отклонено: Device Admin не активен")
            Toast.makeText(this, "Сначала включите Device Admin", Toast.LENGTH_LONG).show()
            return
        }

        val enabled = prefs.getBoolean("monitoring_enabled", false)

        if (enabled) {
            stopService(Intent(this, BtMonitorService::class.java))
            prefs.edit().putBoolean("monitoring_enabled", false).apply()
            LogStore.append(this, "Мониторинг выключен пользователем")
            Toast.makeText(this, "Мониторинг выключен", Toast.LENGTH_SHORT).show()
        } else {
            ContextCompat.startForegroundService(this, Intent(this, BtMonitorService::class.java))
            prefs.edit().putBoolean("monitoring_enabled", true).apply()
            LogStore.append(this, "Мониторинг включен пользователем")
            Toast.makeText(this, "Мониторинг включен", Toast.LENGTH_SHORT).show()
        }

        updateMonitoringUi()
    }

    private fun updateMonitoringUi() {
        val enabled = prefs.getBoolean("monitoring_enabled", false)

        if (enabled) {
            txtMonitorState.text = "Мониторинг включен"
            txtMonitorState.setTextColor(0xFF63E283.toInt())
            btnToggleMonitoring.text = "Выключить мониторинг"
        } else {
            txtMonitorState.text = "Мониторинг выключен"
            txtMonitorState.setTextColor(0xFFFF6B6B.toInt())
            btnToggleMonitoring.text = "Включить мониторинг"
        }
    }
}
