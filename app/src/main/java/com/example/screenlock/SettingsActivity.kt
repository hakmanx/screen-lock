package com.example.screenlock

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private var btnBackSettings: ImageButton? = null
    private var btnEnableAdmin: Button? = null
    private var btnSetLockDelay: Button? = null
    private var btnExportLogs: Button? = null
    private var btnClearLogs: Button? = null
    private var txtLockDelayValue: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_settings)

            prefs = getSharedPreferences(TrustedDevices.PREFS_NAME, Context.MODE_PRIVATE)
            dpm = getSystemService(DevicePolicyManager::class.java)
            adminComponent = ComponentName(this, LockAdminReceiver::class.java)

            btnBackSettings = findViewByIdOrNull(R.id.btnBackSettings)
            btnEnableAdmin = findViewByIdOrNull(R.id.btnEnableAdmin)
            btnSetLockDelay = findViewByIdOrNull(R.id.btnSetLockDelay)
            btnExportLogs = findViewByIdOrNull(R.id.btnExportLogs)
            btnClearLogs = findViewByIdOrNull(R.id.btnClearLogs)
            txtLockDelayValue = findViewByIdOrNull(R.id.txtLockDelayValue)

            btnBackSettings?.setOnClickListener {
                finish()
            }

            btnEnableAdmin?.setOnClickListener {
                openDeviceAdmin()
            }

            btnSetLockDelay?.setOnClickListener {
                showDelayDialog()
            }

            btnExportLogs?.setOnClickListener {
                exportLogsAsText()
            }

            btnClearLogs?.setOnClickListener {
                LogStore.clear(this)
                Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT).show()
            }

            updateDelaySummary()
            LogStore.append(this, "Открыт экран правил блокировки")
        } catch (e: Exception) {
            LogStore.append(
                this,
                "Ошибка открытия SettingsActivity: ${e.javaClass.simpleName}: ${e.message}"
            )

            Toast.makeText(
                this,
                "Ошибка открытия правил блокировки",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::prefs.isInitialized) {
            updateDelaySummary()
        }
    }

    private fun openDeviceAdmin() {
        try {
            LogStore.append(this, "Запрошено включение Device Admin из SettingsActivity")

            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "GuardLink использует это разрешение для блокировки экрана при потере доверенного Bluetooth-устройства."
                )
            }

            startActivity(intent)
        } catch (e: Exception) {
            LogStore.append(
                this,
                "Ошибка открытия Device Admin: ${e.javaClass.simpleName}: ${e.message}"
            )

            Toast.makeText(
                this,
                "Не удалось открыть настройки Device Admin",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateDelaySummary() {
        val delay = prefs.getInt("lock_delay_seconds", 0).coerceIn(0, 300)
        txtLockDelayValue?.text = "Текущая задержка: $delay сек"
    }

    private fun showDelayDialog() {
        try {
            val picker = NumberPicker(this).apply {
                minValue = 0
                maxValue = 300
                value = prefs.getInt("lock_delay_seconds", 0).coerceIn(0, 300)
                wrapSelectorWheel = false
            }

            AlertDialog.Builder(this)
                .setTitle("Задержка блокировки")
                .setMessage("Выберите интервал от 0 до 300 секунд.")
                .setView(picker)
                .setPositiveButton("Сохранить") { _, _ ->
                    val value = picker.value.coerceIn(0, 300)

                    prefs.edit()
                        .putInt("lock_delay_seconds", value)
                        .apply()

                    LogStore.append(this, "Новая задержка блокировки: $value сек")
                    updateDelaySummary()

                    Toast.makeText(
                        this,
                        "Задержка сохранена: $value сек",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        } catch (e: Exception) {
            LogStore.append(
                this,
                "Ошибка настройки задержки: ${e.javaClass.simpleName}: ${e.message}"
            )

            Toast.makeText(
                this,
                "Не удалось открыть настройку задержки",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportLogsAsText() {
        try {
            val logs = LogStore.read(this).ifBlank {
                "Журнал GuardLink пуст"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "guardlink_logs")
                putExtra(Intent.EXTRA_TEXT, logs)
            }

            startActivity(Intent.createChooser(shareIntent, "Выгрузить журнал"))
        } catch (e: Exception) {
            LogStore.append(
                this,
                "Ошибка выгрузки журнала: ${e.javaClass.simpleName}: ${e.message}"
            )

            Toast.makeText(
                this,
                "Не удалось выгрузить журнал",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private inline fun <reified T : View> findViewByIdOrNull(id: Int): T? {
        return try {
            findViewById<T>(id)
        } catch (_: Exception) {
            null
        }
    }
}
