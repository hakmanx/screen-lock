package com.example.screenlock

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences

object TrustedDevices {
    const val PREFS_NAME = "bt_lock_prefs"

    private const val KEY_TARGET_MACS = "target_macs"
    private const val KEY_TARGET_NAMES = "target_names"

    private const val LEGACY_TARGET_MAC = "target_mac"
    private const val LEGACY_TARGET_NAME = "target_name"

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun migrateLegacyIfNeeded(context: Context) {
        val prefs = prefs(context)

        val current = prefs.getStringSet(KEY_TARGET_MACS, emptySet()).orEmpty()
        if (current.isNotEmpty()) return

        val legacyMac = prefs.getString(LEGACY_TARGET_MAC, null)?.trim().orEmpty()
        if (legacyMac.isEmpty()) return

        val legacyName = prefs.getString(LEGACY_TARGET_NAME, "")?.trim().orEmpty()
        val names = if (legacyName.isNotEmpty()) {
            mapOf(legacyMac to legacyName)
        } else {
            emptyMap()
        }

        save(context, setOf(legacyMac), names)
    }

    fun selectedMacs(context: Context): Set<String> {
        migrateLegacyIfNeeded(context)

        return prefs(context)
            .getStringSet(KEY_TARGET_MACS, emptySet())
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun hasSelection(context: Context): Boolean {
        return selectedMacs(context).isNotEmpty()
    }

    fun namesByMac(context: Context): Map<String, String> {
        return decodeNames(prefs(context).getString(KEY_TARGET_NAMES, "") ?: "")
    }

    fun save(context: Context, macs: Set<String>, namesByMac: Map<String, String>) {
        val cleanMacs = macs.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val cleanNames = namesByMac.filterKeys { it in cleanMacs }

        val firstMac = cleanMacs.firstOrNull()
        val firstName = if (firstMac != null) cleanNames[firstMac].orEmpty() else ""

        prefs(context).edit()
            .putStringSet(KEY_TARGET_MACS, cleanMacs)
            .putString(KEY_TARGET_NAMES, encodeNames(cleanNames))
            .putString(LEGACY_TARGET_MAC, firstMac)
            .putString(LEGACY_TARGET_NAME, firstName)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_TARGET_MACS)
            .remove(KEY_TARGET_NAMES)
            .remove(LEGACY_TARGET_MAC)
            .remove(LEGACY_TARGET_NAME)
            .apply()
    }

    fun summary(context: Context): String {
        val macs = selectedMacs(context).toList()
        if (macs.isEmpty()) return "Нет выбранных устройств"

        val names = namesByMac(context)

        return macs.joinToString("\n") { mac ->
            val name = names[mac]?.trim().orEmpty()
            if (name.isNotEmpty() && name != mac) name else mac
        }
    }

    fun displayName(device: BluetoothDevice): String {
        val name = safeName(device)
        val mac = safeAddress(device).orEmpty()

        return when {
            name.isNotEmpty() -> name
            mac.isNotEmpty() -> mac
            else -> "Bluetooth-устройство"
        }
    }

    fun displayNameSingleLine(device: BluetoothDevice): String {
        return displayName(device).replace("\n", " / ")
    }

    fun safeName(device: BluetoothDevice?): String {
        if (device == null) return ""

        return try {
            device.name?.trim().orEmpty()
        } catch (_: SecurityException) {
            ""
        }
    }

    fun safeAddress(device: BluetoothDevice?): String? {
        if (device == null) return null

        return try {
            device.address?.trim()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun encodeNames(namesByMac: Map<String, String>): String {
        return namesByMac.entries.joinToString("\n") { entry ->
            val mac = entry.key.replace("\t", " ").replace("\n", " ").trim()
            val name = entry.value.replace("\t", " ").replace("\n", " ").trim()
            "$mac\t$name"
        }
    }

    private fun decodeNames(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("\t", limit = 2)
                val mac = parts.getOrNull(0)?.trim().orEmpty()
                val name = parts.getOrNull(1)?.trim().orEmpty()

                if (mac.isEmpty()) null else mac to name
            }
            .toMap()
    }
}
