package com.daex.android.data

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DaexSkillManagerImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var assets: AssetManager
    private lateinit var manager: DaexSkillManagerImpl

    private fun skillMarkdown(name: String, description: String) = """
        ---
        name: $name
        description: $description
        ---
        # $name instructions
    """.trimIndent()

    @Before
    fun setup() {
        assets = mockk<AssetManager>()
        every { assets.list("skills") } returns emptyArray()
        val context = mockk<Context>()
        every { context.filesDir } returns tempFolder.root
        every { context.assets } returns assets
        manager = DaexSkillManagerImpl(context)
    }

    @Test
    fun `getSkillCatalog lists a bundled asset skill with valid frontmatter`() {
        every { assets.list("skills") } returns arrayOf("weather")
        every { assets.open("skills/weather/SKILL.md") } returns
            skillMarkdown("weather", "Check the local forecast").byteInputStream()

        val catalog = manager.getSkillCatalog()

        assertTrue(catalog.contains("weather: Check the local forecast"))
    }

    @Test
    fun `getSkillCatalog skips a skill missing required frontmatter fields`() {
        every { assets.list("skills") } returns arrayOf("broken")
        every { assets.open("skills/broken/SKILL.md") } returns "# No frontmatter here".byteInputStream()

        val catalog = manager.getSkillCatalog()

        assertEquals("<available_skills>\n</available_skills>", catalog)
    }

    @Test
    fun `getSkillCatalog lists a locally installed skill from internal storage`() {
        val localSkillDir = tempFolder.newFolder("skills", "journaling")
        java.io.File(localSkillDir, "SKILL.md")
            .writeText(skillMarkdown("journaling", "Guided daily journaling prompts"))

        val catalog = manager.getSkillCatalog()

        assertTrue(catalog.contains("journaling: Guided daily journaling prompts"))
    }

    @Test
    fun `loadSkillInstructions returns bundled asset content when present`() {
        every { assets.list("skills") } returns arrayOf("weather")
        every { assets.open("skills/weather/SKILL.md") } returns
            skillMarkdown("weather", "Check the local forecast").byteInputStream()

        val instructions = manager.loadSkillInstructions("weather")

        assertEquals(skillMarkdown("weather", "Check the local forecast"), instructions)
    }

    @Test
    fun `loadSkillInstructions returns local file content when not in assets`() {
        val localSkillDir = tempFolder.newFolder("skills", "journaling")
        val content = skillMarkdown("journaling", "Guided daily journaling prompts")
        java.io.File(localSkillDir, "SKILL.md").writeText(content)

        val instructions = manager.loadSkillInstructions("journaling")

        assertEquals(content, instructions)
    }

    @Test
    fun `loadSkillInstructions returns null when the skill doesn't exist anywhere`() {
        assertNull(manager.loadSkillInstructions("nonexistent"))
    }
}
