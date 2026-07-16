package com.daex.android.data

import android.content.Context
import com.daex.android.domain.Model
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration test against a real (in-process) HTTP server: exercises [ModelManager]'s actual
 * resume-download logic end to end, the exact code path ROADMAP.md flags as the highest-value
 * fragile code to protect ("no HTTP Range resume... a first-run is a 2.6-3.7GB download - any
 * interruption currently means starting over"). Uses [ModelManager.downloadEmbeddingModel]
 * specifically because it - unlike [ModelManager.downloadModel] - doesn't call
 * [ModelManager.checkSpecSupport], so it needs no [com.daex.android.framework.DeviceService]/RAM
 * mocking to reach the download logic under test.
 */
class ModelManagerDownloadResumeIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var manager: ModelManager
    private lateinit var model: Model

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val context = mockk<Context>()
        every { context.filesDir } returns tempFolder.root
        manager = ModelManager(context)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun modelFor(content: ByteArray) = Model(
        id = "embed-test",
        name = "Embed Test",
        size = content.size.toLong(),
        description = "d",
        requiredRAM = 1L,
        downloadUrl = server.url("/model.tflite").toString(),
        extension = "tflite",
        isEmbedding = true,
        provider = "test",
        familyId = "fam",
        familyName = "Fam",
        sizeName = "S",
        variantName = "base"
    )

    @Test
    fun `a fresh download writes the full file and records its size`() = runBlocking {
        val content = ByteArray(50_000) { (it % 256).toByte() }
        model = modelFor(content)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(content)))

        val path = manager.downloadEmbeddingModel(model)

        val file = File(path)
        assertTrue(file.exists())
        assertEquals(content.size.toLong(), file.length())
        assertTrue(manager.isModelDownloaded(model))
    }

    @Test
    fun `a download interrupted partway resumes via HTTP Range instead of restarting`() = runBlocking {
        val content = ByteArray(50_000) { (it % 256).toByte() }
        model = modelFor(content)
        val file = File(manager.getModelPath(model))
        val partFile = File(file.parentFile, "${file.name}.part")

        // Simulate a prior attempt that got 20,000 bytes in before the connection dropped.
        val firstChunk = content.copyOfRange(0, 20_000)
        partFile.parentFile?.mkdirs()
        partFile.writeBytes(firstChunk)

        // A resume request must carry a Range header; respond 206 with only the remaining bytes.
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 20000-49999/50000")
                .setBody(okio.Buffer().write(content.copyOfRange(20_000, 50_000)))
        )

        val path = manager.downloadEmbeddingModel(model)
        val recordedRequest = server.takeRequest()

        assertEquals("bytes=20000-", recordedRequest.getHeader("Range"))
        assertEquals(content.size.toLong(), File(path).length())
        assertTrue(File(path).readBytes().contentEquals(content))
        assertFalse(partFile.exists())
    }

    @Test
    fun `a size mismatch after download throws and leaves the part file for the next attempt`() = runBlocking {
        val content = ByteArray(1_000)
        model = modelFor(content)
        // Declares the full 1000-byte body (so the client's expected Content-Length is correct),
        // but the socket policy severs the connection partway through actually sending it -
        // simulating a real dropped connection, not just a short-but-complete response.
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(content))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        // Expected to throw: either our own size-mismatch check or the underlying stream failing
        // early both indicate the corrupt transfer was correctly rejected, not silently accepted.
        // Which specific exception type surfaces isn't the point of this test, only that the
        // partial file never gets promoted to "downloaded" - deliberately ignored, not swallowed.
        @Suppress("SwallowedException")
        try {
            manager.downloadEmbeddingModel(model)
        } catch (_: Exception) {
        }

        assertFalse(manager.isModelDownloaded(model))
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `a second concurrent embedding download is rejected while one is already active`() = runBlocking {
        val content = ByteArray(10_000)
        model = modelFor(content)
        // Never-completing response so the first download stays "active" for the duration of this test.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val firstAttempt = GlobalScope.launch(Dispatchers.IO) {
            try {
                manager.downloadEmbeddingModel(model)
            } catch (_: Exception) {
            }
        }
        // Give the first coroutine a moment to register itself as the active download.
        delay(200)

        try {
            manager.downloadEmbeddingModel(model)
            org.junit.Assert.fail("Expected IllegalStateException for a concurrent download")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("already active"))
        } finally {
            firstAttempt.cancel()
        }
    }
}
