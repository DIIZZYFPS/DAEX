package com.daex.android.services

import android.content.Context
import android.util.Log
import java.io.File

interface DaexSkillManager {
    fun getSkillCatalog(): String
    fun loadSkillInstructions(skillName: String): String?
}

class DaexSkillManagerImpl(private val context: Context) : DaexSkillManager {

    companion object {
        private const val TAG = "DaexSkillManager"
    }

    override fun getSkillCatalog(): String {
        val catalog = StringBuilder()
        catalog.append("<available_skills>\n")

        // 1. Scan assets
        try {
            val skillDirs = context.assets.list("skills") ?: emptyArray()
            for (dir in skillDirs) {
                try {
                    val skillText = context.assets.open("skills/$dir/SKILL.md").bufferedReader().use { it.readText() }
                    val info = parseFrontmatter(skillText)
                    if (info != null) {
                        catalog.append("  - ${info.first}: ${info.second}\n")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read built-in skill in assets: $dir", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list built-in skills in assets", e)
        }

        // 2. Scan internal storage
        try {
            val localSkillsDir = File(context.filesDir, "skills")
            if (localSkillsDir.exists() && localSkillsDir.isDirectory) {
                localSkillsDir.listFiles()?.forEach { dir ->
                    if (dir.isDirectory) {
                        val skillFile = File(dir, "SKILL.md")
                        if (skillFile.exists()) {
                            try {
                                val skillText = skillFile.readText()
                                val info = parseFrontmatter(skillText)
                                if (info != null) {
                                    catalog.append("  - ${info.first}: ${info.second}\n")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to read local skill: ${dir.name}", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list local skills from storage", e)
        }

        catalog.append("</available_skills>")
        return catalog.toString()
    }

    override fun loadSkillInstructions(skillName: String): String? {
        // Try assets first
        try {
            val hasInAssets = context.assets.list("skills")?.contains(skillName) == true
            if (hasInAssets) {
                val assetPath = "skills/$skillName/SKILL.md"
                return context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Skill not in assets or read failed: $skillName", e)
        }

        // Try local files
        try {
            val localFile = File(context.filesDir, "skills/$skillName/SKILL.md")
            if (localFile.exists()) {
                return localFile.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read local skill file: $skillName", e)
        }

        return null
    }

    private fun parseFrontmatter(text: String): Pair<String, String>? {
        if (!text.startsWith("---")) return null
        val parts = text.split("---", limit = 3)
        if (parts.size < 3) return null
        val frontmatter = parts[1]
        
        var name = ""
        var description = ""
        
        for (line in frontmatter.lines()) {
            val cleanLine = line.trim()
            if (cleanLine.startsWith("name:")) {
                name = cleanLine.substringAfter("name:").trim()
            } else if (cleanLine.startsWith("description:")) {
                description = cleanLine.substringAfter("description:").trim()
            }
        }
        
        if (name.isNotEmpty() && description.isNotEmpty()) {
            return Pair(name, description)
        }
        return null
    }
}
