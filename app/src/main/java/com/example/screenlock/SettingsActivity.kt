package com.example.screenlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBackSettings: ImageButton
    private lateinit var btnEnableAdmin: Button
    private lateinit var btnExportLogs: Button
    private lateinit var btnClearLogs: Button
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnBackSettings = findViewById(R.id.btnBackSettings)
        btnEnableAdmin = findViewById(R.id.btnEnableAdmin)
        btnExportLogs = findViewById(R.id.btnExportLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)

        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, LockAdminReceiver::class.java)

        btnBackSettings.setOnClickListener {
            finish()
        }

        btnEnableAdmin.setOnClickListener {
            LogStore.append(this, "Запрошено включение Device Admin из GuardLink SettingsActivity")

            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "GuardLink использует это разрешение для блокировки экрана при потере доверенного Bluetooth-устройства"
                )
            }

            startActivity(intent)
        }

        btnExportLogs.setOnClickListener {
            exportLogs()
        }

        btnClearLogs.setOnClickListener {
            LogStore.clear(this)
            Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportLogs() {
        val logs = LogStore.read(this)
        val file = File(cacheDir, "guardlink_logs.txt")
        file.writeText(logs.ifBlank { "Журнал GuardLink пуст" })

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "guardlink_logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Выгрузить журнал"))
    }
}
