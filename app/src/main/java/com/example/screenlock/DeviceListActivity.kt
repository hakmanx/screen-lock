package com.example.screenlock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
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
    private lateinit var btnBackDevice: ImageButton
    private lateinit var txtCurrentDevice: TextView
    private lateinit var listViewDevices: ListView
    private lateinit var navHome: TextView
    private lateinit var navDevices: TextView
    private lateinit var navJournal: TextView

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

        TrustedDevices.migrateLegacyIfNeeded(this)

        btnBackDevice = findViewById(R.id.btnBackDevice)
        txtCurrentDevice = findViewById(R.id.txtCurrentDevice)
        listViewDevices = findViewById(R.id.listViewDevices)
        navHome = findViewById(R.id.navHome)
        navDevices = findViewById(R.id.navDevices)
        navJournal = findViewById(R.id.navJournal)

        listViewDevices.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        btnBackDevice.setOnClickListener {
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
            // Уже на экране устройств.
        }

        navJournal.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        listViewDevices.setOnItemClickListener { _, _, position, _ ->
            val device = currentDevices[position]
            val mac = TrustedDevices.safeAddress(device)

            if (mac.isNullOrBlank()) {
                Toast.makeText(this, "Не удалось получить MAC устройства", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }

            val selectedMacs = TrustedDevices.selectedMacs(this).toMutableSet()
            val namesByMac = TrustedDevices.namesByMac(this).toMutableMap()
            val title = TrustedDevices.displayNameSingleLine(device)

            if (selectedMacs.contains(mac)) {
                selectedMacs.remove(mac)
                namesByMac.remove(mac)
                LogStore.append(this, "Устройство удалено из доверенных: $title")
                Toast.makeText(this, "Убрано из доверенных", Toast.LENGTH_SHORT).show()
            } else {
                selectedMacs.add(mac)
                namesByMac[mac] = TrustedDevices.safeName(device)
                LogStore.append(this, "Устройство добавлено в доверенные: $title")
                Toast.makeText(this, "Добавлено в доверенные", Toast.LENGTH_SHORT).show()
            }

            TrustedDevices.save(this, selectedMacs, namesByMac)

            updateCurrentDevice()
            loadBondedDevices()
        }

        updateCurrentDevice()
        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()

        TrustedDevices.migrateLegacyIfNeeded(this)

        updateCurrentDevice()
        checkPermissionAndLoad()
    }

    private fun checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
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
        val selectedMacs = TrustedDevices.selectedMacs(this)

        val devices = try {
            adapter?.bondedDevices
                ?.toList()
                ?.sortedWith(
                    compareByDescending<BluetoothDevice> {
                        TrustedDevices.safeAddress(it) in selectedMacs
                    }.thenBy {
                        TrustedDevices.displayName(it).lowercase()
                    }
                )
                .orEmpty()
        } catch (e: SecurityException) {
            LogStore.append(this, "Ошибка чтения Bluetooth-устройств: ${e.javaClass.simpleName}: ${e.message}")
            Toast.makeText(this, "Нет доступа к Bluetooth-устройствам", Toast.LENGTH_LONG).show()
            emptyList()
        }

        currentDevices = devices

        val items = devices.map { device ->
            val mac = TrustedDevices.safeAddress(device)
            val name = TrustedDevices.displayName(device)

            if (mac != null && mac in selectedMacs) {
                "☑ $name"
            } else {
                "☐ $name"
            }
        }

        listViewDevices.adapter = ArrayAdapter(
            this,
            R.layout.item_device,
            R.id.txtDeviceName,
            items
        )

        devices.forEachIndexed { index, device ->
            val mac = TrustedDevices.safeAddress(device)
            listViewDevices.setItemChecked(index, mac != null && mac in selectedMacs)
        }

        if (devices.isEmpty()) {
            Toast.makeText(this, "Сопряжённых Bluetooth-устройств не найдено", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateCurrentDevice() {
        val count = TrustedDevices.selectedMacs(this).size

        txtCurrentDevice.text = when (count) {
            0 -> "Нет выбранных устройств"
            1 -> "Выбрано 1 устройство:\n${TrustedDevices.summary(this)}"
            in 2..4 -> "Выбрано $count устройства:\n${TrustedDevices.summary(this)}"
            else -> "Выбрано $count устройств:\n${TrustedDevices.summary(this)}"
        }
    }
}
