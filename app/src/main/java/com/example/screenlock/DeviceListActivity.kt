package com.example.screenlock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class DeviceListActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var btnBackDevice: ImageButton
    private lateinit var txtCurrentDevice: TextView
    private lateinit var listViewDevices: ListView

    private var currentDevices: List<BluetoothDevice> = emptyList()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadBondedDevices()
            } else {
                Toast.makeText(
                    this,
                    "Нужно разрешение BLUETOOTH_CONNECT",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        prefs = getSharedPreferences("bt_lock_prefs", Context.MODE_PRIVATE)

        btnBackDevice = findViewById(R.id.btnBackDevice)
        txtCurrentDevice = findViewById(R.id.txtCurrentDevice)
        listViewDevices = findViewById(R.id.listViewDevices)

        btnBackDevice.setOnClickListener {
            finish()
        }

        listViewDevices.setOnItemClickListener { _, _, position, _ ->
            val device = currentDevices[position]

            prefs.edit()
                .putString("target_mac", device.address)
                .putString("target_name", device.name ?: "")
                .apply()

            LogStore.append(
                this,
                "Выбрано устройство: ${device.name ?: "Без имени"} ${device.address}"
            )

            Toast.makeText(this, "Устройство выбрано", Toast.LENGTH_SHORT).show()
            finish()
        }

        updateCurrentDevice()
        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        updateCurrentDevice()
        checkPermissionAndLoad()
    }

    private fun checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
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
                "✓  $name"
            } else {
                name
            }
        }

        listViewDevices.adapter = ArrayAdapter(
            this,
            R.layout.item_device,
            R.id.txtDeviceName,
            items
        )
    }

    private fun updateCurrentDevice() {
        val name = prefs.getString("target_name", "")?.trim().orEmpty()
        val mac = prefs.getString("target_mac", null)

        txtCurrentDevice.text = when {
            name.isNotEmpty() -> name
            mac != null -> mac
            else -> "Нет выбранного устройства"
        }
    }

    private fun displayName(device: BluetoothDevice): String {
        val name = device.name?.trim().orEmpty()
        return if (name.isNotEmpty()) name else device.address
    }
}
