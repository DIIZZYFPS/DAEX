package com.daex.android.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * One entry per individually-toggleable tool (Settings UI + the disabled-tool-id preference).
 * `defaultEnabled = false` marks tools that take an external action or expose personal data
 * (launching apps, sending email, running intents, recalling past conversations) - everything
 * else (read-only device status) defaults on, matching today's behavior.
 */
data class ToolRegistryEntry(
    val id: String,
    val label: String,
    val description: String,
    val defaultEnabled: Boolean
)

object ToolRegistry {
    val ALL = listOf(
        ToolRegistryEntry("deviceTime", "Date & Time", "Current date and local system time", defaultEnabled = true),
        ToolRegistryEntry("batteryStatus", "Battery Status", "Battery level and charging state", defaultEnabled = true),
        ToolRegistryEntry("storageStatus", "Storage Status", "Free and total disk space", defaultEnabled = true),
        ToolRegistryEntry("deviceInfo", "Device Info", "Manufacturer, model, and OS version", defaultEnabled = true),
        ToolRegistryEntry("skills", "Modular Skills", "Discover and load domain-specific skill instructions", defaultEnabled = true),
        ToolRegistryEntry("launchApp", "Launch Apps", "Launch other installed applications", defaultEnabled = false),
        ToolRegistryEntry("sendEmail", "Send Email", "Open the email client with a prefilled message", defaultEnabled = false),
        ToolRegistryEntry("runIntent", "Run System Intents", "Trigger native system actions", defaultEnabled = false),
        ToolRegistryEntry("recallMemory", "Recall Past Conversations", "Search conversation history for relevant context", defaultEnabled = true)
    )

    /** Short capability hint for the tool-aware system prompt line - only mentions tools that
     * are actually enabled, so it never drifts from what the model can really call. */
    fun capabilitySummary(disabledToolIds: Set<String>): String =
        ALL.filter { it.id !in disabledToolIds }.joinToString(", ") { it.label }
}

class DeviceTimeTool(private val onStatusUpdate: ((String?) -> Unit)? = null) : ToolSet {
    @Tool(description = "Get the current date and local system time")
    fun getDeviceTime(): String {
        onStatusUpdate?.invoke("Reading local system time...")
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val result = formatter.format(Date())
        onStatusUpdate?.invoke(null)
        return result
    }
}

class BatteryStatusTool(
    private val context: Context,
    private val onStatusUpdate: ((String?) -> Unit)? = null
) : ToolSet {
    @Tool(description = "Get the current battery level percentage and charging state")
    fun getBatteryStatus(): String {
        onStatusUpdate?.invoke("Reading battery status...")
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val result = "Battery Level: $batteryPct%, Charging: $isCharging"
        onStatusUpdate?.invoke(null)
        return result
    }
}

class StorageStatusTool(
    private val context: Context,
    private val onStatusUpdate: ((String?) -> Unit)? = null
) : ToolSet {
    @Tool(description = "Get the available free disk space and total disk space in GB")
    fun getStorageStatus(): String {
        onStatusUpdate?.invoke("Reading storage footprint...")
        val path: File = context.filesDir
        val stat = StatFs(path.path)
        val free = stat.availableBytes
        val total = stat.totalBytes
        val freeGb = String.format(Locale.US, "%.2f", free / (1024.0 * 1024.0 * 1024.0))
        val totalGb = String.format(Locale.US, "%.2f", total / (1024.0 * 1024.0 * 1024.0))
        val result = "Storage Free: $freeGb GB, Total: $totalGb GB"
        onStatusUpdate?.invoke(null)
        return result
    }
}

class DeviceInfoTool(private val onStatusUpdate: ((String?) -> Unit)? = null) : ToolSet {
    @Tool(description = "Get the device name, manufacturer, model, and Android OS version info")
    fun getDeviceInfo(): String {
        onStatusUpdate?.invoke("Scanning hardware profile specifications...")
        val result = "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android SDK: ${Build.VERSION.SDK_INT}, OS Version: ${Build.VERSION.RELEASE}"
        onStatusUpdate?.invoke(null)
        return result
    }
}

class SkillTools(
    context: Context,
    private val onStatusUpdate: ((String?) -> Unit)? = null
) : ToolSet {
    private val skillManager: DaexSkillManager = DaexSkillManagerImpl(context)

    @Tool(description = "Retrieve the full instructions and parameters for a specific skill. Call this when you need details on how to use a skill that is not currently loaded in your context.")
    fun loadSkill(
        @ToolParam(description = "The kebab-case name of the skill to load (e.g. 'send-email')") skillName: String
    ): String {
        onStatusUpdate?.invoke("Skill Loaded: $skillName")
        val instructions = skillManager.loadSkillInstructions(skillName)
        onStatusUpdate?.invoke(null)
        return instructions ?: "Skill '$skillName' not found or could not be loaded."
    }

    @Tool(description = "List all available modular skills and their descriptions. Use this to find out what capabilities are available to be loaded via loadSkill.")
    fun listSkills(): String {
        onStatusUpdate?.invoke("Listing skills...")
        val catalog = skillManager.getSkillCatalog()
        onStatusUpdate?.invoke(null)
        return catalog
    }
}

class LaunchAppTool(
    private val context: Context,
    private val onRequestPermission: (suspend (String, String) -> Boolean)? = null,
    private val onStatusUpdate: ((String?) -> Unit)? = null
) : ToolSet {
    companion object {
        private const val TAG = "LaunchAppTool"
    }

    @Tool(description = "Launch an installed application on the device by its name")
    fun launchApp(
        @ToolParam(description = "The display name of the application to launch (e.g. 'Spotify', 'YouTube')") appName: String
    ): String {
        onStatusUpdate?.invoke("Launching application: $appName...")

        val approved = runBlocking {
            onRequestPermission?.invoke("com.daex.system.launch_app", "Launch application: $appName") ?: true
        }
        if (!approved) {
            onStatusUpdate?.invoke(null)
            return "Permission denied by user"
        }

        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in packages) {
                val name = pm.getApplicationLabel(appInfo).toString()
                if (name.equals(appName, ignoreCase = true)) {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    return if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        onStatusUpdate?.invoke(null)
                        "Launched $appName successfully"
                    } else {
                        onStatusUpdate?.invoke(null)
                        "App $appName found but has no launchable main Activity"
                    }
                }
            }
            onStatusUpdate?.invoke(null)
            return "App '$appName' not found on this device"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $appName", e)
            onStatusUpdate?.invoke(null)
            return "Failed to launch $appName: ${e.message}"
        }
    }
}

class SendEmailTool(
    private val context: Context,
    private val onStatusUpdate: ((String?) -> Unit)? = null
) : ToolSet {
    @Tool(description = "Send an email. Launches the native device email client pre-filled with recipient, subject, and body.")
    fun sendEmail(
        @ToolParam(description = "The email address of the recipient.") to: String,
        @ToolParam(description = "The subject line of the email.") subject: String,
        @ToolParam(description = "The body text of the email.") body: String
    ): String {
        onStatusUpdate?.invoke("Preparing email...")
        return try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(emailIntent)
            "Launched email client successfully."
        } catch (e: Exception) {
            "Failed to launch email client: ${e.message}"
        } finally {
            onStatusUpdate?.invoke(null)
        }
    }
}

class RunIntentTool(private val onStatusUpdate: ((String?) -> Unit)? = null) : ToolSet {
    @Tool(description = "Run a native system intent to trigger device actions")
    fun runIntent(
        @ToolParam(description = "The intent action name") intent: String,
        @ToolParam(description = "JSON parameters matching the target intent specification") parameters: String
    ): String {
        onStatusUpdate?.invoke(null)
        return try {
            "Unknown intent action: $intent"
        } catch (e: Exception) {
            "Failed to run intent: ${e.message}"
        } finally {
            onStatusUpdate?.invoke(null)
        }
    }
}
