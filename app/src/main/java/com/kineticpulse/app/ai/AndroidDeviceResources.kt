package com.kineticpulse.app.ai

import android.app.ActivityManager
import android.content.Context
import java.io.File

class AndroidDeviceResources(private val context: Context) : DeviceResources {

    // Directorio app-specific externo: accesible por `adb push` sin root ni permisos extra.
    override val modelsDir: File
        get() = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    override val cacheDir: File
        get() = File(context.cacheDir, "llm").apply { mkdirs() }

    private val activityManager = context.getSystemService(ActivityManager::class.java)

    override fun availableRamMb(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    override fun isLowRamDevice(): Boolean = activityManager.isLowRamDevice
}
