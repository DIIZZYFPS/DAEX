package com.daex.android.services

import org.junit.Assert.*
import org.junit.Test

class ModelBankTest {

    @Test
    fun embeddingModel_existsAndConfigured() {
        val model = ModelBank.embeddingModel
        assertEquals("nomic-embed-text-v1.5-q4_k_m", model.id)
        assertTrue(model.isEmbedding)
        assertEquals(85_000_000L, model.size)
        assertEquals(500_000_000L, model.requiredRAM)
    }

    @Test
    fun generativeModels_excludesEmbedding() {
        val allModels = ModelBank.models
        val generativeModels = ModelBank.generativeModels
        
        assertEquals(allModels.size, generativeModels.size + 1)
        assertTrue(generativeModels.none { it.isEmbedding })
    }

    @Test
    fun allModels_haveValidIds() {
        for (model in ModelBank.models) {
            assertFalse("Model ${model.name} has empty id", model.id.isBlank())
            assertFalse("Model ${model.name} has empty name", model.name.isBlank())
            assertFalse("Model ${model.name} has empty url", model.downloadUrl.isBlank())
            assertTrue("Model ${model.name} size must be positive", model.size > 0)
            assertTrue("Model ${model.name} RAM must be positive", model.requiredRAM > 0)
        }
    }

    @Test
    fun allModels_haveReasonableSizes() {
        for (model in ModelBank.models) {
            assertTrue(
                "Model ${model.name} size (${model.size}) should be < 10GB",
                model.size < 10_000_000_000L
            )
            assertTrue(
                "Model ${model.name} RAM (${model.requiredRAM}) should be < 16GB",
                model.requiredRAM < 16_000_000_000L
            )
        }
    }

    @Test
    fun gemma4E4B_model() {
        val e4b = ModelBank.models.find { it.id == "gemma-4-E4B-it-Q4_K_M" }
        assertNotNull("Gemma 4-E4B model should exist", e4b)
        assertEquals(2_500_000_000L, e4b!!.size)
        assertEquals(6_000_000_000L, e4b.requiredRAM)
        assertFalse(e4b.isEmbedding)
    }

    @Test
    fun gemma4E2B_model() {
        val e2b = ModelBank.models.find { it.id == "gemma-4-E2B-it-Q4_K_M" }
        assertNotNull("Gemma 4-E2B model should exist", e2b)
        assertEquals(1_500_000_000L, e2b!!.size)
        assertEquals(3_500_000_000L, e2b.requiredRAM)
        assertFalse(e2b.isEmbedding)
    }

    @Test
    fun model_dataClass_copyWorks() {
        val original = Model(
            id = "test", name = "Test", size = 1_000_000L,
            description = "desc", requiredRAM = 500_000_000L,
            downloadUrl = "https://example.com/test.gguf"
        )
        val copy = original.copy(name = "Test v2")
        assertEquals("test", copy.id)
        assertEquals("Test v2", copy.name)
        assertEquals(original.size, copy.size)
        assertNotEquals(original.name, copy.name)
    }

    @Test
    fun model_dataClass_equality() {
        val m1 = Model(
            id = "test", name = "Test", size = 1_000_000L,
            description = "desc", requiredRAM = 500_000_000L,
            downloadUrl = "https://example.com/test.gguf"
        )
        val m2 = Model(
            id = "test", name = "Test", size = 1_000_000L,
            description = "desc", requiredRAM = 500_000_000L,
            downloadUrl = "https://example.com/test.gguf"
        )
        assertEquals(m1, m2)
        assertEquals(m1.hashCode(), m2.hashCode())
    }
}