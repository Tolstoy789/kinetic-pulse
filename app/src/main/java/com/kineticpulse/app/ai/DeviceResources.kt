package com.kineticpulse.app.ai

import java.io.File

/** Dónde viven los modelos y cuánta memoria hay. Interfaz para poder simularlo en tests. */
interface DeviceResources {
    val modelsDir: File
    val cacheDir: File
    fun availableRamMb(): Long
    fun isLowRamDevice(): Boolean
}
