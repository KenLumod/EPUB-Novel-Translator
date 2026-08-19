package com.example.epubnoveltranslator.util

import android.app.ActivityManager
import android.content.Context

data class RamInfo(
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val isLowMemory: Boolean,
    val totalRamGb: Float,
    val availRamGb: Float,
    val isBelowRecommended: Boolean
)

object RamDiagnostics {

    fun getRamInfo(context: Context): RamInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalBytes = memoryInfo.totalMem
        val availBytes = memoryInfo.availMem
        val totalGb = totalBytes / (1024f * 1024f * 1024f)
        val availGb = availBytes / (1024f * 1024f * 1024f)

        // Recommended RAM for Gemma 3n is 4 GB+
        val isBelowRecommended = totalGb < 3.8f

        return RamInfo(
            totalRamBytes = totalBytes,
            availRamBytes = availBytes,
            isLowMemory = memoryInfo.lowMemory,
            totalRamGb = totalGb,
            availRamGb = availGb,
            isBelowRecommended = isBelowRecommended
        )
    }
}
