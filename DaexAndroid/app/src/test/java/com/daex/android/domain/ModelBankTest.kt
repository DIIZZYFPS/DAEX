package com.daex.android.domain

import com.daex.android.framework.BackendType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Data-integrity checks on the static model catalog. Cheap insurance against copy-paste
 * mistakes (duplicate ids, a pasted-in size of 0) when a new model entry is added.
 */
class ModelBankTest {

    private val allModels = ModelBank.models + listOf(
        ModelBank.kokoroModel,
        ModelBank.kokoroVoices,
        ModelBank.kokoroTokens
    )

    @Test
    fun `every model id is unique`() {
        val ids = allModels.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `generativeModels excludes the embedding model`() {
        assertFalse(ModelBank.generativeModels.any { it.isEmbedding })
        assertTrue(ModelBank.generativeModels.contains(ModelBank.models.first { it.id == "gemma-4-E2B-it-litert-lm" }))
    }

    @Test
    fun `the embedding model is present in the full catalog and marked as such`() {
        assertTrue(ModelBank.models.contains(ModelBank.embeddingModel))
        assertTrue(ModelBank.embeddingModel.isEmbedding)
    }

    @Test
    fun `every model has a plausible download url`() {
        for (model in allModels) {
            assertTrue("${model.id} download url should be https", model.downloadUrl.startsWith("https://"))
        }
    }

    @Test
    fun `every model declares a positive size`() {
        for (model in allModels) {
            assertTrue("${model.id} size should be positive", model.size > 0)
        }
    }

    @Test
    fun `every model declares at least one supported backend`() {
        for (model in allModels) {
            assertTrue("${model.id} should support at least one backend", model.supportedBackends.isNotEmpty())
        }
    }

    @Test
    fun `NPU-targeted models declare a target hardware string`() {
        for (model in allModels) {
            if (model.supportedBackends == listOf(BackendType.NPU)) {
                assertTrue("${model.id} is NPU-only but has no targetHardware", !model.targetHardware.isNullOrBlank())
            }
        }
    }
}
