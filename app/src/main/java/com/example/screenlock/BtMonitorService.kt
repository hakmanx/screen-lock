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
    private var pendingLockRunnable: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val action = intent.action ?: return
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val targetMac = prefs.getString("target_mac", null) ?: run {
                LogStore.append(this@BtMonitorService, "Событие $action проигнорировано: target_mac отсутствует")
                return
            }
            val targetName = prefs.getString("target_name", "Unknown") ?: "Unknown"

            LogStore.append(
                this@BtMonitorService,
                "Получено событие: $action; device=${device?.name ?: "null"}; mac=${device?.address ?: "null"}"
            )
            updateNotification("Событие: $action")

            if (device == null) {
                LogStore.append(this@BtMonitorService, "Событие $action без BluetoothDevice")
                updateNotification("Событие без устройства: $action")
                return
            }

            if (device.address != targetMac) {
                LogStore.append(
                    this@BtMonitorService,
                    "Игнор события: устройство не совпало.\nВыбрано=$targetName/$targetMac, пришло=${device.name ?: "Unknown"}/${device.address}"
                )
                updateNotification("Игнор: не выбранное устройство")
                return
            }

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    pendingLockRunnable?.let { handler.removeCallbacks(it) }
                    pendingLockRunnable = null
                    LogStore.append(this@BtMonitorService, "Выбранное устройство подключено: $targetName ($targetMac)")
                    updateNotification("Подключено: $targetName")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    pendingLockRunnable?.let { handler.removeCallbacks(it) }
                    LogStore.append(this@BtMonitorService, "Выбранное устройство отключено: $targetName ($targetMac)")
                    updateNotification("Отключено: $targetName, блокировка через 5 сек")

                    val runnable = Runnable {
                        val adminActive = dpm.isAdminActive(adminComponent)
                        LogStore.append(
                            this@BtMonitorService,
                            "Попытка lockNow(); adminActive=$adminActive; target=$targetName ($targetMac)"
                        )
                        updateNotification("Пытаюсь заблокировать экран")

                        if (adminActive) {
                            try {
                                dpm.lockNow()
                                LogStore.append(this@BtMonitorService, "Срабатывание блокировки: lockNow() выполнен")
                                updateNotification("Экран заблокирован")
                            } catch (e: Exception) {
                                LogStore.append(
                                    this@BtMonitorService,
                                    "Ошибка lockNow(): ${e.javaClass.simpleName}: ${e.message}"
                                )
                                updateNotification("Ошибка lockNow()")
                            }
                        } else {
                            LogStore.append(this@BtMonitorService, "Device Admin не активен в момент блокировки")
                            updateNotification("Device Admin не активен")
                        }
                    }

                    pendingLockRunnable = runnable
                    handler.postDelayed(runnable, 5000)
                }

                else -> {
                    LogStore.append(this@BtMonitorService, "Иное событие выбранного устройства: $action")
                    updateNotification("Другое событие: $action")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("bt_lock_prefs", Context.MODE_PRIVATE)
        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)

        prefs.edit().putBoolean("monitoring_enabled", true).apply()

        LogStore.append(this, "BtMonitorService.onCreate()")
        createChannel()
        startForeground(1001, buildNotification("Мониторинг Bluetooth-устройства активен"))

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        registerReceiver(receiver, filter)
        LogStore.append(this, "Receiver зарегистрирован на ACTION_ACL_CONNECTED/ACTION_ACL_DISCONNECTED")
        updateNotification("Сервис запущен")
    }

    override fun onDestroy() {
        pendingLockRunnable?.let { handler.removeCallbacks(it) }
        unregisterReceiver(receiver)
        prefs.edit().putBoolean("monitoring_enabled", false).apply()
        LogStore.append(this, "BtMonitorService.onDestroy()")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
