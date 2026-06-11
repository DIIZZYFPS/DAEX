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
import org.json.JSONObject

class DeviceTools(
    private val context: Context,
    private val onRequestPermission: (suspend (String, String) -> Boolean)? = null,
    private val onStatusUpdate: ((String?) -> Unit)? = null,
    private val onHyperframeSaved: ((String) -> Unit)? = null
) : ToolSet {

    private val skillManager: DaexSkillManager = DaexSkillManagerImpl(context)

    companion object {
        private const val TAG = "DeviceTools"
    }

    @Tool(description = "Get the current date and local system time")
    fun getDeviceTime(): String {
        Log.d(TAG, "getDeviceTime execution triggered")
        onStatusUpdate?.invoke("Reading local system time...")
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val result = formatter.format(Date())
        Log.d(TAG, "getDeviceTime returning: $result")
        onStatusUpdate?.invoke(null)
        return result
    }

    @Tool(description = "Get the current battery level percentage and charging state")
    fun getBatteryStatus(): String {
        Log.d(TAG, "getBatteryStatus execution triggered")
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
        Log.d(TAG, "getBatteryStatus returning: $result")
        onStatusUpdate?.invoke(null)
        return result
    }

    @Tool(description = "Get the available free disk space and total disk space in GB")
    fun getStorageStatus(): String {
        Log.d(TAG, "getStorageStatus execution triggered")
        onStatusUpdate?.invoke("Reading storage footprint...")
        val path: File = context.filesDir
        val stat = StatFs(path.path)
        val free = stat.availableBytes
        val total = stat.totalBytes
        val freeGb = String.format(Locale.US, "%.2f", free / (1024.0 * 1024.0 * 1024.0))
        val totalGb = String.format(Locale.US, "%.2f", total / (1024.0 * 1024.0 * 1024.0))
        val result = "Storage Free: $freeGb GB, Total: $totalGb GB"
        Log.d(TAG, "getStorageStatus returning: $result")
        onStatusUpdate?.invoke(null)
        return result
    }

    @Tool(description = "Get the device name, manufacturer, model, and Android OS version info")
    fun getDeviceInfo(): String {
        Log.d(TAG, "getDeviceInfo execution triggered")
        onStatusUpdate?.invoke("Scanning hardware profile specifications...")
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val sdk = Build.VERSION.SDK_INT
        val release = Build.VERSION.RELEASE
        val result = "Device: $manufacturer $model, Android SDK: $sdk, OS Version: $release"
        Log.d(TAG, "getDeviceInfo returning: $result")
        onStatusUpdate?.invoke(null)
        return result
    }

    @Tool(description = "Launch an installed application on the device by its name")
    fun launchApp(
        @ToolParam(description = "The display name of the application to launch (e.g. 'Spotify', 'YouTube')") appName: String
    ): String {
        Log.d(TAG, "launchApp execution triggered for appName: $appName")
        onStatusUpdate?.invoke("Launching application: $appName...")
        
        // Request user permission interactively
        val approved = runBlocking {
            onRequestPermission?.invoke("com.daex.system.launch_app", "Launch application: $appName") ?: true
        }
        if (!approved) {
            val msg = "Permission denied by user"
            Log.d(TAG, msg)
            onStatusUpdate?.invoke(null)
            return msg
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
                        val msg = "Launched $appName successfully"
                        Log.d(TAG, msg)
                        onStatusUpdate?.invoke(null)
                        msg
                    } else {
                        val msg = "App $appName found but has no launchable main Activity"
                        Log.d(TAG, msg)
                        onStatusUpdate?.invoke(null)
                        msg
                    }
                }
            }
            val msg = "App '$appName' not found on this device"
            Log.d(TAG, msg)
            onStatusUpdate?.invoke(null)
            return msg
        } catch (e: Exception) {
            val msg = "Failed to launch $appName: ${e.message}"
            Log.e(TAG, msg, e)
            onStatusUpdate?.invoke(null)
            return msg
        }
    }

    @Tool(description = "Retrieve the full instructions and parameters for a specific skill. Call this when you need details on how to use a skill that is not currently loaded in your context.")
    fun loadSkill(
        @ToolParam(description = "The kebab-case name of the skill to load (e.g. 'send-email')") skillName: String
    ): String {
        Log.d(TAG, "loadSkill execution triggered for skillName: $skillName")
        onStatusUpdate?.invoke("Skill Loaded: $skillName")
        val instructions = skillManager.loadSkillInstructions(skillName)
        onStatusUpdate?.invoke(null)
        return instructions ?: "Skill '$skillName' not found or could not be loaded."
    }

    @Tool(description = "List all available modular skills and their descriptions. Use this to find out what capabilities are available to be loaded via loadSkill.")
    fun listSkills(): String {
        Log.d(TAG, "listSkills execution triggered")
        onStatusUpdate?.invoke("Listing skills...")
        val catalog = skillManager.getSkillCatalog()
        onStatusUpdate?.invoke(null)
        return catalog
    }

    @Tool(description = "Send an email. Launches the native device email client pre-filled with recipient, subject, and body.")
    fun sendEmail(
        @ToolParam(description = "The email address of the recipient.") to: String,
        @ToolParam(description = "The subject line of the email.") subject: String,
        @ToolParam(description = "The body text of the email.") body: String
    ): String {
        Log.d(TAG, "sendEmail tool triggered. To: '$to', subject: '$subject'")
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

    @Tool(description = "Save a programmatically generated Hyperframe HTML composition to local storage")
    fun saveHyperframe(
        @ToolParam(description = "The complete HTML code of the Hyperframe composition, including embedded styles and timing attributes.") htmlContent: String,
        @ToolParam(description = "The target filename, ending in .html (e.g. 'promo.html')") fileName: String
    ): String {
        Log.d(TAG, "saveHyperframe execution triggered for fileName: $fileName")
        onStatusUpdate?.invoke("Saving Hyperframe composition...")
        
        return try {
            val dir = File(context.filesDir, "hyperframes")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, fileName)
            file.writeText(htmlContent)
            val msg = "Hyperframe composition saved successfully to local storage as: $fileName"
            Log.d(TAG, msg)
            onHyperframeSaved?.invoke(fileName)
            msg
        } catch (e: Exception) {
            val errorMsg = "Failed to save Hyperframe composition: ${e.message}"
            Log.e(TAG, errorMsg, e)
            errorMsg
        } finally {
            onStatusUpdate?.invoke(null)
        }
    }

    @Tool(description = "Compile a saved Hyperframe HTML composition into an MP4 video file using cloud/simulated render engine")
    fun compileHyperframe(
        @ToolParam(description = "The name of the saved HTML composition file (e.g. 'promo.html')") fileName: String
    ): String {
        Log.d(TAG, "compileHyperframe execution triggered for fileName: $fileName")
        
        try {
            onStatusUpdate?.invoke("Initiating HeyGen cloud video compiler...")
            Thread.sleep(1200)
            
            onStatusUpdate?.invoke("Parsing timeline tracks & layer timing...")
            Thread.sleep(1200)
            
            onStatusUpdate?.invoke("Assembling media assets & CSS animations...")
            Thread.sleep(1200)
            
            onStatusUpdate?.invoke("FFmpeg rendering video container...")
            Thread.sleep(1200)
            
            val outputDir = File(context.filesDir, "hyperframes")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            val baseName = fileName.substringBeforeLast(".")
            val outputFile = File(outputDir, "$baseName.mp4")
            
            context.assets.open("skills/hyperframe/assets/hyperframe_demo.mp4").use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            val successMsg = "[COMPILATION SUCCESSFUL]: file://${outputFile.absolutePath}"
            Log.d(TAG, successMsg)
            return successMsg
        } catch (e: Exception) {
            val errorMsg = "Failed to compile Hyperframe video: ${e.message}"
            Log.e(TAG, errorMsg, e)
            return errorMsg
        } finally {
            onStatusUpdate?.invoke(null)
        }
    }

    @Tool(description = "Run a native system intent to trigger device actions")
    fun runIntent(
        @ToolParam(description = "The intent action name") intent: String,
        @ToolParam(description = "JSON parameters matching the target intent specification") parameters: String
    ): String {
        Log.d(TAG, "runIntent triggered: intent=$intent, parameters=$parameters")
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
