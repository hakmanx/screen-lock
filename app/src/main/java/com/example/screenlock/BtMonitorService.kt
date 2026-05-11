package com.example.screenlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class BtMonitorService : Service() {
    private lateinit var prefs: SharedPreferences
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var notificationManager: NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private val pendingLockRunnablesByMac = mutableMapOf<String, Runnable>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val action = intent.action ?: return
            val selectedMacs = TrustedDevices.selectedMacs(this@BtMonitorService)

            if (selectedMacs.isEmpty()) {
                LogStore.append(
                    this@BtMonitorService,
                    "Событие $action проигнорировано: доверенные устройства не выбраны"
                )
                updateNotification("Доверенные устройства не выбраны")
                return
            }

            val device = bluetoothDeviceFrom(intent)

            LogStore.append(
                this@BtMonitorService,
                "Получено событие: $action; device=${TrustedDevices.safeName(device).ifBlank { "null" }}; mac=${TrustedDevices.safeAddress(device) ?: "null"}"
            )

            if (device == null) {
                LogStore.append(this@BtMonitorService, "Событие $action без BluetoothDevice")
                updateNotification("Событие без устройства: $action")
                return
            }

            val deviceMac = TrustedDevices.safeAddress(device)

            if (deviceMac == null || deviceMac !in selectedMacs) {
                LogStore.append(
                    this@BtMonitorService,
                    "Игнор события: устройство не входит в доверенные. Пришло=${TrustedDevices.displayNameSingleLine(device)}"
                )
                updateNotification("Игнор: не доверенное устройство")
                return
            }

            val deviceTitle = TrustedDevices.displayNameSingleLine(device)

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    pendingLockRunnablesByMac.remove(deviceMac)?.let {
                        handler.removeCallbacks(it)
                    }

                    LogStore.append(
                        this@BtMonitorService,
                        "Доверенное устройство подключено: $deviceTitle"
                    )
                    updateNotification("Подключено: $deviceTitle")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    pendingLockRunnablesByMac.remove(deviceMac)?.let {
                        handler.removeCallbacks(it)
                    }

                    val delaySeconds = prefs.getInt("lock_delay_seconds", 0).coerceIn(0, 300)

                    LogStore.append(
                        this@BtMonitorService,
                        "Доверенное устройство отключено: $deviceTitle"
                    )

                    updateNotification(
                        if (delaySeconds == 0) {
                            "Отключено: $deviceTitle, блокировка без задержки"
                        } else {
                            "Отключено: $deviceTitle, блокировка через $delaySeconds сек"
                        }
                    )

                    val runnable = Runnable {
                        pendingLockRunnablesByMac.remove(deviceMac)
                        lockNowForDevice(deviceTitle, deviceMac)
                    }

                    pendingLockRunnablesByMac[deviceMac] = runnable

                    if (delaySeconds == 0) {
                        runnable.run()
                    } else {
                        handler.postDelayed(runnable, delaySeconds * 1000L)
                    }
                }

                else -> {
                    LogStore.append(
                        this@BtMonitorService,
                        "Иное событие доверенного устройства: $action; $deviceTitle"
                    )
                    updateNotification("Другое событие: $action")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences(TrustedDevices.PREFS_NAME, Context.MODE_PRIVATE)
        TrustedDevices.migrateLegacyIfNeeded(this)

        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)

        prefs.edit().putBoolean("monitoring_enabled", true).apply()

        LogStore.append(
            this,
            "BtMonitorService.onCreate(); доверенных устройств: ${TrustedDevices.selectedMacs(this).size}"
        )

        createChannel()
        startForeground(1001, buildNotification("Мониторинг доверенных Bluetooth-устройств активен"))

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        registerReceiver(receiver, filter)

        LogStore.append(
            this,
            "Receiver зарегистрирован на ACTION_ACL_CONNECTED/ACTION_ACL_DISCONNECTED"
        )
        updateNotification("Сервис запущен")
    }

    override fun onDestroy() {
        pendingLockRunnablesByMac.values.forEach {
            handler.removeCallbacks(it)
        }
        pendingLockRunnablesByMac.clear()

        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }

        prefs.edit().putBoolean("monitoring_enabled", false).apply()
        LogStore.append(this, "BtMonitorService.onDestroy()")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun lockNowForDevice(deviceTitle: String, deviceMac: String) {
        val adminActive = dpm.isAdminActive(adminComponent)

        LogStore.append(
            this,
            "Попытка lockNow(); adminActive=$adminActive; target=$deviceTitle ($deviceMac)"
        )

        updateNotification("Пытаюсь заблокировать экран")

        if (!adminActive) {
            LogStore.append(this, "Device Admin не активен в момент блокировки")
            updateNotification("Device Admin не активен")
            return
        }

        try {
            dpm.lockNow()
            LogStore.append(this, "Срабатывание блокировки: lockNow() выполнен")
            updateNotification("Экран заблокирован")
        } catch (e: Exception) {
            LogStore.append(
                this,
                "Ошибка lockNow(): ${e.javaClass.simpleName}: ${e.message}"
            )
            updateNotification("Ошибка lockNow()")
        }
    }

    private fun bluetoothDeviceFrom(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bt_lock_channel",
                "GuardLink Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "bt_lock_channel")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(1001, buildNotification(text))
    }
}
