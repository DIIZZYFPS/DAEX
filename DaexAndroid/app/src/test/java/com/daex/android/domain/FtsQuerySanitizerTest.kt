package com.daex.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsQuerySanitizerTest {

    @Test
    fun `keeps ordinary terms and formats as OR'd prefix matches`() {
        assertEquals("\"hello\"* OR \"world\"*", FtsQuerySanitizer.sanitize("hello world"))
    }

    @Test
    fun `drops stop words when at least one real term remains`() {
        assertEquals("\"quick\"* OR \"fox\"*", FtsQuerySanitizer.sanitize("the quick fox"))
    }

    @Test
    fun `falls back to original terms when every term is a stop word`() {
        assertEquals("\"the\"* OR \"a\"* OR \"an\"*", FtsQuerySanitizer.sanitize("the a an"))
    }

    @Test
    fun `blank query sanitizes to empty string`() {
        assertEquals("", FtsQuerySanitizer.sanitize(""))
        assertEquals("", FtsQuerySanitizer.sanitize("   "))
    }

    @Test
    fun `strips punctuation and FTS operator characters`() {
        assertEquals(
            "\"select\"* OR \"users\"* OR \"drop\"* OR \"table\"*",
            FtsQuerySanitizer.sanitize("SELECT * FROM users; DROP TABLE")
        )
    }

    @Test
    fun `strips quotes so a crafted query cannot break out of the FTS match syntax`() {
        assertEquals("\"foo\"* OR \"bar\"*", FtsQuerySanitizer.sanitize("\"foo\" OR \"bar\""))
    }

    @Test
    fun `normalizes case`() {
        assertEquals("\"hello\"*", FtsQuerySanitizer.sanitize("HELLO"))
    }

    @Test
    fun `strips non-ASCII characters`() {
        assertEquals("\"caf\"*", FtsQuerySanitizer.sanitize("café"))
    }

    @Test
    fun `collapses repeated whitespace between terms`() {
        assertEquals("\"hello\"* OR \"world\"*", FtsQuerySanitizer.sanitize("hello    world"))
    }
}
