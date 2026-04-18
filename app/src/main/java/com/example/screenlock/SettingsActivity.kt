package com.example.screenlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnEnableAdmin: Button
    private lateinit var btnExportLogs: Button
    private lateinit var btnClearLogs: Button

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnEnableAdmin = findViewById(R.id.btnEnableAdmin)
        btnExportLogs = findViewById(R.id.btnExportLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)

        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        btnEnableAdmin.setOnClickListener {
            LogStore.append(this, "Запрошено включение Device Admin из SettingsActivity")
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Нужно для блокировки экрана при отключении выбранного Bluetooth-устройства"
                )
            }
            startActivity(intent)
        }

        btnExportLogs.setOnClickListener {
            exportLogs()
        }

        btnClearLogs.setOnClickListener {
            LogStore.clear(this)
            Toast.makeText(this, "Логи очищены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportLogs() {
        val logs = LogStore.read(this)
        val file = File(cacheDir, "ironlink_logs.txt")
        file.writeText(logs.ifBlank { "Логи пусты" })

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ironlink_logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Выгрузить логи"))
    }
}
