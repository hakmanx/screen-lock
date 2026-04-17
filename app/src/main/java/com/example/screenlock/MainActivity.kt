package com.example.screenlock

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private lateinit var listView: ListView
    private lateinit var btnEnableAdmin: Button
    private lateinit var btnStart: Button
    private lateinit var btnExportLogs: Button
    private lateinit var btnClearLogs: Button
    private lateinit var txtSelected: TextView

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
        btnStart = findViewById(R.id.btnStart)
        btnExportLogs = findViewById(R.id.btnExportLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        txtSelected = findViewById(R.id.txtSelected)

        prefs = getSharedPreferences("bt_lock_prefs", Context.MODE_PRIVATE)
        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        btnEnableAdmin.setOnClickListener {
            requestDeviceAdmin()
        }

        btnStart.setOnClickListener {
            startMonitorService()
        }

        btnExportLogs.setOnClickListener {
            exportLogs()
        }

        btnClearLogs.setOnClickListener {
            clearLogs()
        }

        checkPermissionAndLoad()
        updateSelected()
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
        val devices = adapter?.bondedDevices?.toList().orEmpty()

        val items = devices.map { "${it.name ?: "Unknown"}\n${it.address}" }

        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = devices[position]
            prefs.edit()
                .putString("target_mac", device.address)
                .putString("target_name", device.name ?: "Unknown")
                .apply()

            updateSelected()
            LogStore.append(this, "Выбрано устройство: ${device.name ?: "Unknown"} ${device.address}")
            Toast.makeText(this, "Выбрано: ${device.name} ${device.address}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelected() {
        val name = prefs.getString("target_name", "не выбрано")
        val mac = prefs.getString("target_mac", "-")
        txtSelected.text = "Выбранное устройство: $name ($mac)"
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

    private fun startMonitorService() {
        val mac = prefs.getString("target_mac", null)

        if (mac == null) {
            LogStore.append(this, "Старт мониторинга отклонен: устройство не выбрано")
            Toast.makeText(this, "Сначала выберите Bluetooth-устройство", Toast.LENGTH_LONG).show()
            return
        }

        if (!dpm.isAdminActive(adminComponent)) {
            LogStore.append(this, "Старт мониторинга отклонен: Device Admin не активен")
            Toast.makeText(this, "Сначала включите Device Admin", Toast.LENGTH_LONG).show()
            return
        }

        LogStore.append(this, "Запуск мониторинга для ${prefs.getString("target_name", "Unknown")} ($mac)")
        val intent = Intent(this, BtMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Мониторинг запущен", Toast.LENGTH_SHORT).show()
    }

    private fun clearLogs() {
        LogStore.clear(this)
        Toast.makeText(this, "Логи очищены", Toast.LENGTH_SHORT).show()
    }

    private fun exportLogs() {
        val logs = LogStore.read(this)
        val file = File(cacheDir, "screen_lock_logs.txt")
        file.writeText(logs.ifBlank { "Логи пусты" })

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "screen_lock_logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Выгрузить логи"))
    }
}
