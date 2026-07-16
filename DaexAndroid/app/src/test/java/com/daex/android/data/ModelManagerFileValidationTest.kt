package com.daex.android.data

import android.content.Context
import com.daex.android.domain.Model
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelManagerFileValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var manager: ModelManager
    private lateinit var model: Model

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.filesDir } returns tempFolder.root
        manager = ModelManager(context)
        model = Model(
            id = "test-model",
            name = "Test Model",
            size = 1000L,
            description = "d",
            requiredRAM = 512L,
            downloadUrl = "https://example.com/model.bin",
            extension = "bin",
            provider = "test",
            familyId = "fam",
            familyName = "Fam",
            sizeName = "S",
            variantName = "base"
        )
    }

    private fun modelFile(): File = File(manager.getModelPath(model))

    @Test
    fun `isModelDownloaded is false when the file does not exist`() = runBlocking {
        assertFalse(manager.isModelDownloaded(model))
    }

    @Test
    fun `isModelDownloaded is true when the size sidecar matches the real file length`() = runBlocking {
        val file = modelFile()
        file.writeBytes(ByteArray(1000))
        File(file.parentFile, "${file.name}.size").writeText("1000")

        assertTrue(manager.isModelDownloaded(model))
    }

    @Test
    fun `isModelDownloaded is false when the size sidecar does not match a truncated file`() = runBlocking {
        val file = modelFile()
        file.writeBytes(ByteArray(400)) // truncated - a real download would have recorded 1000
        File(file.parentFile, "${file.name}.size").writeText("1000")

        assertFalse(manager.isModelDownloaded(model))
    }

    @Test
    fun `isModelDownloaded uses a 90 percent heuristic and backfills the sidecar when none exists`() = runBlocking {
        // Simulates a file downloaded before size-sidecar tracking shipped.
        val file = modelFile()
        file.writeBytes(ByteArray(950)) // >= 90% of the declared 1000-byte size

        assertTrue(manager.isModelDownloaded(model))
        assertEquals("950", File(file.parentFile, "${file.name}.size").readText().trim())
    }

    @Test
    fun `isModelDownloaded heuristic rejects a file well under 90 percent of expected size`() = runBlocking {
        val file = modelFile()
        file.writeBytes(ByteArray(100)) // well under 90% of 1000

        assertFalse(manager.isModelDownloaded(model))
    }

    @Test
    fun `deleteModel removes the model file, its part file, and its size sidecar`() = runBlocking {
        val file = modelFile()
        file.writeBytes(ByteArray(1000))
        val partFile = File(file.parentFile, "${file.name}.part")
        val sizeFile = File(file.parentFile, "${file.name}.size")
        partFile.writeBytes(ByteArray(10))
        sizeFile.writeText("1000")

        manager.deleteModel(model)

        assertFalse(file.exists())
        assertFalse(partFile.exists())
        assertFalse(sizeFile.exists())
    }

    @Test
    fun `formatBytes renders human-readable sizes`() {
        assertEquals("0 B", manager.formatBytes(0L))
        assertEquals("1.5 KB", manager.formatBytes(1536L))
        assertEquals("2.0 MB", manager.formatBytes(2L * 1024 * 1024))
        assertEquals("3.7 GB", manager.formatBytes((3.7 * 1024 * 1024 * 1024).toLong()))
    }
}
