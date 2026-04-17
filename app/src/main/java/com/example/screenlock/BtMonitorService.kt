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

            val device: BluetoothDevice? =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

            val targetMac = prefs.getString("target_mac", null) ?: return
            val targetName = prefs.getString("target_name", "Unknown") ?: "Unknown"

            updateNotification("Получено событие: $action")

            if (device == null) {
                updateNotification("Событие без устройства: $action")
                return
            }

            updateNotification("Событие: $action / ${device.name ?: "Unknown"} / ${device.address}")

            if (device.address != targetMac) {
                updateNotification("Игнор: событие не от выбранного устройства ($targetName)")
                return
            }

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    pendingLockRunnable?.let { handler.removeCallbacks(it) }
                    pendingLockRunnable = null
                    updateNotification("Выбранное устройство подключено: $targetName")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    pendingLockRunnable?.let { handler.removeCallbacks(it) }

                    updateNotification("Выбранное устройство отключено: $targetName. Блокировка через 5 сек")

                    val runnable = Runnable {
                        updateNotification("Пытаюсь заблокировать экран: $targetName")
                        if (dpm.isAdminActive(adminComponent)) {
                            dpm.lockNow()
                        } else {
                            updateNotification("Ошибка: Device Admin не активен")
                        }
                    }

                    pendingLockRunnable = runnable
                    handler.postDelayed(runnable, 5000)
                }

                else -> {
                    updateNotification("Другое событие выбранного устройства: $action")
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

        createChannel()
        startForeground(1001, buildNotification("Мониторинг Bluetooth-устройства активен"))

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        registerReceiver(receiver, filter)
        updateNotification("Сервис запущен")
    }

    override fun onDestroy() {
        pendingLockRunnable?.let { handler.removeCallbacks(it) }
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bt_lock_channel",
                "Bluetooth monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "bt_lock_channel")
            .setContentTitle("Screen Lock BT")
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
