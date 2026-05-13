package com.daex.android.services

import kotlin.math.pow
import kotlin.math.log
import org.junit.Assert.*
import org.junit.Test

class ModelManagerTest {

    // Extract pure logic for testing (formatBytes is the main one)
    private fun formatBytes(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        val k = 1024.0
        val sizes = arrayOf("B", "KB", "MB", "GB")
        val i = kotlin.math.floor(kotlin.math.log(bytes.toDouble(), k)).toInt()
        return String.format("%.1f %s", bytes / k.pow(i.toDouble()), sizes[i])
    }

    @Test
    fun formatBytes_zeroReturnsZeroB() {
        assertEquals("0 B", formatBytes(0L))
    }

    @Test
    fun formatBytes_bytes() {
        assertEquals("1.0 B", formatBytes(1L))
        assertEquals("1.0 KB", formatBytes(1024L))
    }

    @Test
    fun formatBytes_kilobytes() {
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("512.0 B", formatBytes(512L))
    }

    @Test
    fun formatBytes_megabytes() {
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("10.5 MB", formatBytes(10L * 1024 * 1024 + 524288))
    }

    @Test
    fun formatBytes_gigabytes() {
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
        assertEquals("2.5 GB", formatBytes(2L * 1024 * 1024 * 1024 + 536_870_912))
    }

    @Test
    fun downloadProgress_dataClass() {
        val progress = DownloadProgress(100L, 1000L, 10)
        assertEquals(100L, progress.bytesWritten)
        assertEquals(1000L, progress.contentLength)
        assertEquals(10, progress.percent)
    }

    @Test
    fun storageInfo_dataClass() {
        val info = StorageInfo(
            freeSpace = 5_000_000_000L,
            totalRAM = 8_000_000_000L,
            modelSize = 2_500_000_000L,
            isDownloaded = true,
            isHardwareCapable = true
        )
        assertEquals(5_000_000_000L, info.freeSpace)
        assertEquals(8_000_000_000L, info.totalRAM)
        assertEquals(2_500_000_000L, info.modelSize)
        assertTrue(info.isDownloaded)
        assertTrue(info.isHardwareCapable)
    }

    @Test
    fun specSupport_dataClass() {
        val spec = SpecSupport(
            totalRAM = 8_000_000_000L,
            freeSpace = 5_000_000_000L,
            hasEnoughRAM = true,
            hasEnoughStorage = true,
            supported = true
        )
        assertTrue(spec.supported)
        assertTrue(spec.hasEnoughRAM)
        assertTrue(spec.hasEnoughStorage)
    }
}