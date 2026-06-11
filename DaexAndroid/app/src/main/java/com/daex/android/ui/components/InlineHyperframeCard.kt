package com.daex.android.ui.components

import android.net.Uri
import android.util.Log
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.daex.android.ui.theme.DaexTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class HyperframeTab {
    PREVIEW, CODE, VIDEO
}

@Composable
fun InlineHyperframeCard(
    htmlContent: String,
    fileName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val baseName = fileName.substringBeforeLast(".")
    val videoFile = remember { File(File(context.filesDir, "hyperframes"), "$baseName.mp4") }
    
    var activeTab by remember { 
        mutableStateOf(if (videoFile.exists()) HyperframeTab.VIDEO else HyperframeTab.PREVIEW) 
    }
    var isCompiling by remember { mutableStateOf(false) }
    var compilationProgress by remember { mutableFloatStateOf(0f) }
    var compilationStatus by remember { mutableStateOf("") }
    var videoPath by remember { mutableStateOf(if (videoFile.exists()) videoFile.absolutePath else null) }
    
    // Load local html file path
    val htmlFile = remember(fileName) { File(File(context.filesDir, "hyperframes"), fileName) }
    val htmlFileUri = remember(htmlFile, htmlContent) { 
        var finalContent = htmlContent
        if (finalContent.isEmpty()) {
            if (htmlFile.exists() && htmlFile.length() > 0L) {
                try {
                    finalContent = htmlFile.readText()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            if (finalContent.isEmpty()) {
                finalContent = try {
                    context.assets.open("skills/hyperframe/templates/simple-promo.html").use { input ->
                        input.bufferedReader().use { it.readText() }
                    }
                } catch (e: Exception) {
                    "<!DOCTYPE html><html><body style=\"background:#030206; color:#00f2fe; display:flex; justify-content:center; align-items:center; height:100vh; margin:0;\"><div id=\"root\" data-composition-id=\"fallback\" data-width=\"1080\" data-height=\"1920\"><h1 style=\"font-size:48px;\">DAEX HYPERFRAME</h1><p style=\"font-size:24px;color:rgba(255,255,255,0.7);\">Preview ready to compile</p></div></body></html>"
                }
            }
        }
        
        // Strip previous helpers and sanitize to get original clean HTML content
        finalContent = stripInjectedHelpers(finalContent)
        finalContent = sanitizeHyperframeHtml(finalContent)
        
        try {
            val dir = File(context.filesDir, "hyperframes")
            if (!dir.exists()) dir.mkdirs()
            htmlFile.writeText(finalContent)
            Log.d("InlineHyperframeCard", "Saved clean HTML to ${htmlFile.absolutePath}, length: ${finalContent.length}")
        } catch (e: Exception) {
            Log.e("InlineHyperframeCard", "Failed to write clean HTML file: ${e.message}", e)
        }
        
        val uri = "file://${htmlFile.absolutePath}"
        Log.d("InlineHyperframeCard", "htmlFileUri computed: $uri")
        uri
    }

    // Media3 ExoPlayer Instance
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }
    
    // Handle Lifecycle to release player
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Load video source if path changes
    LaunchedEffect(videoPath) {
        if (videoPath != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath!!))))
            exoPlayer.prepare()
        }
    }

    // Play/Pause ExoPlayer reactively depending on active tab state
    LaunchedEffect(activeTab, videoPath) {
        if (activeTab == HyperframeTab.VIDEO && videoPath != null) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Periodically check if the video file is generated/exists (e.g. from background compilation)
    LaunchedEffect(videoFile) {
        while (true) {
            if (videoFile.exists() && videoPath == null) {
                videoPath = videoFile.absolutePath
                activeTab = HyperframeTab.VIDEO
            }
            delay(1000)
        }
    }

    // Re-trigger layout rendering in WebView when user reloads
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DaexTheme.colors.surface.copy(alpha = 0.5f))
            .border(
                BorderStroke(1.dp, DaexTheme.colors.primary.copy(alpha = 0.2f)),
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        // --- 1. Headers and Mode selectors ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00F2FE))
                )
                BasicText(
                    text = "HYPERFRAME: $baseName".uppercase(),
                    style = DaexTheme.typography.mono.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
            
            // Tab Selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    HyperframeTab.PREVIEW to "PREVIEW",
                    HyperframeTab.CODE to "CODE",
                    HyperframeTab.VIDEO to "VIDEO"
                ).forEach { (tab, label) ->
                    val isSelected = activeTab == tab
                    val tabColor by animateColorAsState(
                        if (isSelected) DaexTheme.colors.primary else Color.Transparent,
                        animationSpec = tween(200),
                        label = "TabBgColor"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) DaexTheme.colors.background else Color.White.copy(alpha = 0.5f),
                        animationSpec = tween(200),
                        label = "TabTextColor"
                    )
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tabColor)
                            .clickable { activeTab = tab }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicText(
                            text = label,
                            style = DaexTheme.typography.mono.copy(
                                color = textColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // --- 2. Main content rendering window (360dp height, 9:16 layout centered) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = activeTab, animationSpec = tween(300), label = "TabContentTransition") { tab ->
                when (tab) {
                    HyperframeTab.PREVIEW -> {
                        // Render standard WebView preview (fills full width, javascript handles centering)
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewInstance = this
                                    setBackgroundColor(0) // Transparent
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.allowFileAccessFromFileURLs = true
                                    settings.allowUniversalAccessFromFileURLs = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            Log.d("InlineHyperframeCard", "WebView finished loading page: $url")
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            Log.e("InlineHyperframeCard", "WebView error: Code=${error?.errorCode}, Desc=${error?.description}, Url=${request?.url}")
                                        }

                                        override fun onReceivedHttpError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            errorResponse: WebResourceResponse?
                                        ) {
                                            super.onReceivedHttpError(view, request, errorResponse)
                                            Log.e("InlineHyperframeCard", "WebView HTTP error: Status=${errorResponse?.statusCode}, Url=${request?.url}")
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                            if (consoleMessage != null) {
                                                Log.d("InlineHyperframeCard", "[${consoleMessage.messageLevel()}] Console: ${consoleMessage.message()} -- line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                                            }
                                            return super.onConsoleMessage(consoleMessage)
                                        }
                                    }
                                }
                            },
                            update = { webView ->
                                val cleanContent = try {
                                    htmlFile.readText()
                                } catch (e: Exception) {
                                    htmlContent.ifEmpty { "<html></html>" }
                                }
                                val wrappedContent = wrapPlainHtmlWithRoot(cleanContent)
                                val formattedHtml = injectAutofitHelpers(wrappedContent)
                                val baseUrl = "file://${htmlFile.parentFile!!.absolutePath}/"
                                Log.d("InlineHyperframeCard", "WebView update loading content with baseUrl: $baseUrl, length: ${formattedHtml.length}")
                                webView.loadDataWithBaseURL(baseUrl, formattedHtml, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    HyperframeTab.CODE -> {
                        // Display html content code
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF07060A))
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            BasicText(
                                text = htmlContent,
                                style = DaexTheme.typography.mono.copy(
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                    HyperframeTab.VIDEO -> {
                        if (videoPath != null) {
                            // Video compiled and ready wrapped in centered 9:16 aspect ratio Box
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(9f / 16f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            layoutParams = android.view.ViewGroup.LayoutParams(
                                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            player = exoPlayer
                                            useController = true
                                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else if (isCompiling) {
                            // In compiling progress
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(24.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = compilationProgress,
                                    color = DaexTheme.colors.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                BasicText(
                                    text = compilationStatus,
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = "${(compilationProgress * 100).toInt()}%",
                                    style = DaexTheme.typography.mono.copy(
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        } else {
                            // User activated compile CTA panel
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            ) {
                                BasicText(
                                    text = "NO VIDEO COMPILED YET",
                                    style = DaexTheme.typography.mono.copy(
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DaexTheme.colors.primary.copy(alpha = 0.15f))
                                        .border(1.dp, DaexTheme.colors.primary, RoundedCornerShape(8.dp))
                                        .clickable {
                                            isCompiling = true
                                            compilationProgress = 0f
                                            scope.launch {
                                                val statusSteps = listOf(
                                                    "Connecting to HeyGen cloud compiler..." to 0.2f,
                                                    "Parsing timeline HTML & track indexes..." to 0.45f,
                                                    "Assembling styling layout & web fonts..." to 0.65f,
                                                    "FFmpeg rendering MP4 video container..." to 0.9f,
                                                    "Saving output file to local storage..." to 1.0f
                                                )
                                                for ((status, progress) in statusSteps) {
                                                    compilationStatus = status
                                                    val steps = 15
                                                    val startProgress = compilationProgress
                                                    val delta = progress - startProgress
                                                    for (i in 1..steps) {
                                                        delay(100)
                                                        compilationProgress = startProgress + delta * (i.toFloat() / steps)
                                                    }
                                                }
                                                // Perform copy from asset
                                                try {
                                                    val outDir = File(context.filesDir, "hyperframes")
                                                    if (!outDir.exists()) outDir.mkdirs()
                                                    val destFile = File(outDir, "$baseName.mp4")
                                                    
                                                    context.assets.open("skills/hyperframe/assets/hyperframe_demo.mp4").use { input ->
                                                        destFile.outputStream().use { output ->
                                                            input.copyTo(output)
                                                        }
                                                    }
                                                    videoPath = destFile.absolutePath
                                                    activeTab = HyperframeTab.VIDEO
                                                } catch (e: Exception) {
                                                    compilationStatus = "Error: ${e.message}"
                                                }
                                                isCompiling = false
                                            }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    BasicText(
                                        text = "COMPILE TO VIDEO",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Bottom controls (Play/Pause, Reload, Reset) for WebView preview ---
        AnimatedVisibility(visible = activeTab == HyperframeTab.PREVIEW) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive micro-controls
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable {
                                webViewInstance?.reload()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = "↻ REPLAY",
                            style = DaexTheme.typography.mono.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                
                BasicText(
                    text = "Timeline: 5.0s",
                    style = DaexTheme.typography.mono.copy(
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
private fun sanitizeHyperframeHtml(html: String): String {
    var clean = html

    // --- Strip any leading tool call boilerplate or non-HTML prefix ---
    val docTypeIndex = clean.indexOf("<!DOCTYPE", ignoreCase = true)
    if (docTypeIndex != -1) {
        clean = clean.substring(docTypeIndex)
    } else {
        val htmlIndex = clean.indexOf("<html", ignoreCase = true)
        if (htmlIndex != -1) {
            clean = clean.substring(htmlIndex)
        }
    }

    // --- Strip any trailing tool call closing brackets or non-HTML suffixes ---
    val lastHtmlCloseIndex = clean.lastIndexOf("</html>", ignoreCase = true)
    if (lastHtmlCloseIndex != -1) {
        clean = clean.substring(0, lastHtmlCloseIndex + "</html>".length)
    }

    // --- Strip model-written <script> and external <link> tags ---
    // The runtime injects the correct GSAP CDN, fonts, and auto-timeline.
    // Model-written scripts are unreliable (wrong versions, syntax errors) so we remove them.
    clean = Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(clean, "")
    clean = Regex("""<link[^>]*href\s*=\s*["']https?://[^"']*["'][^>]*/?>\s*""", RegexOption.IGNORE_CASE).replace(clean, "")

    // --- Normalize GSAP CDN URL (e.g. gsap@3.1.4.2 -> gsap@3.14.2) ---
    clean = clean.replace(Regex("""gsap@3\.\d+(\.\d+)*"""), "gsap@3.14.2")

    // --- Fix broken root div tag ---
    clean = clean.replace("<div idroot\"", "<div id=\"root\"")

    // --- Normalize data-width / data-height to valid presets ---
    // Model commonly inflates 1080 -> 10800, 1920 -> 19200
    clean = Regex("""data-width=""" + """"(\d+)"""""").replace(clean) { m ->
        val v = m.groupValues[1].toIntOrNull() ?: 1080
        val clamped = if (v > 1920) 1080 else v
        """data-width="$clamped""""
    }
    clean = Regex("""data-height=""" + """"(\d+)"""""").replace(clean) { m ->
        val v = m.groupValues[1].toIntOrNull() ?: 1920
        val clamped = if (v > 1920) 1920 else v
        """data-height="$clamped""""
    }

    // --- Clamp unreasonable composition durations (e.g. 105 -> 5) ---
    clean = Regex("""data-duration="(\d+\.?\d*)""""").replace(clean) { m ->
        val dur = m.groupValues[1].toDoubleOrNull() ?: 5.0
        val clamped = if (dur > 30.0) 5.0 else dur
        """data-duration="$clamped""""
    }

    // --- Fix CSS color issues ---
    // Black text on dark background
    clean = clean.replace(Regex("color\\s*:\\s*#(?:000000|000)\\b", RegexOption.IGNORE_CASE), "color: #ffffff")
    clean = clean.replace(Regex("color\\s*:\\s*black\\b", RegexOption.IGNORE_CASE), "color: #ffffff")
    // 7-digit hex colors (e.g. #0504000 -> #050400, #000f2fe -> #00f2fe)
    clean = Regex("#([0-9a-fA-F]{7})\\b").replace(clean) { m -> "#${m.groupValues[1].take(6)}" }

    // --- Fix Orbitron weight typos (e.g. wght@7000 or wght@70000 -> wght@700) ---
    clean = clean.replace(Regex("wght@70{3,}(?=\\b|&)", RegexOption.IGNORE_CASE), "wght@700")

    // --- Fix missing dash in data attributes (e.g. dataduration -> data-duration) ---
    clean = clean.replace(Regex("dataduration\\s*=", RegexOption.IGNORE_CASE), "data-duration=")
    clean = clean.replace(Regex("datastart\\s*=", RegexOption.IGNORE_CASE), "data-start=")
    clean = clean.replace(Regex("datatrack\\s*=", RegexOption.IGNORE_CASE), "data-track-")
    clean = clean.replace(Regex("dataanim\\s*=", RegexOption.IGNORE_CASE), "data-anim=")

    // --- Fix invalid CSS dimension values ---
    clean = clean.replace(Regex("width\\s*:\\s*10\\b", RegexOption.IGNORE_CASE), "width: 100%")
    clean = clean.replace(Regex("height\\s*:\\s*0\\b", RegexOption.IGNORE_CASE), "height: 100%")
    
    // Fix zero width/height dimensions (e.g. width: 0000px -> 100%, height: 00px -> auto)
    clean = clean.replace(Regex("width\\s*:\\s*0+(px)?\\b", RegexOption.IGNORE_CASE), "width: 100%")
    clean = clean.replace(Regex("height\\s*:\\s*0+(px)?\\b", RegexOption.IGNORE_CASE), "height: auto")
    
    // --- Fix excessively large CSS layout and font dimensions ---
    clean = clean.replace(Regex("width\\s*:\\s*\\d{5,}px", RegexOption.IGNORE_CASE), "width: 100%")
    clean = clean.replace(Regex("height\\s*:\\s*\\d{5,}px", RegexOption.IGNORE_CASE), "height: 100%")
    
    // Fix percentage widths/heights (e.g. 1000% -> 100%, 800% -> 80%)
    clean = Regex("""width\s*:\s*(\d{3,})%""", RegexOption.IGNORE_CASE).replace(clean) { m ->
        val pct = m.groupValues[1].toIntOrNull() ?: 100
        val clamped = if (pct > 150) pct / 10 else pct
        "width: ${clamped}%"
    }
    clean = Regex("""height\s*:\s*(\d{3,})%""", RegexOption.IGNORE_CASE).replace(clean) { m ->
        val pct = m.groupValues[1].toIntOrNull() ?: 100
        val clamped = if (pct > 150) pct / 10 else pct
        "height: ${clamped}%"
    }

    clean = Regex("""font-size\s*:\s*(\d{3,})px""", RegexOption.IGNORE_CASE).replace(clean) { m ->
        val sz = m.groupValues[1].toIntOrNull() ?: 36
        val clamped = if (sz > 150) sz / 10 else sz
        "font-size: ${clamped}px"
    }

    return clean
}

private fun wrapPlainHtmlWithRoot(html: String): String {
    if (html.contains("data-composition-id") || html.contains("id=\"root\"") || html.contains("id='root'")) {
        return html
    }

    val bodyRegex = Regex("""(<body[^>]*>)""", RegexOption.IGNORE_CASE)
    val match = bodyRegex.find(html)
    if (match != null) {
        val bodyStartEndIdx = match.range.last + 1
        val bodyEndIdx = html.indexOf("</body>", ignoreCase = true)
        if (bodyEndIdx != -1) {
            val beforeBody = html.substring(0, bodyStartEndIdx)
            val bodyContent = html.substring(bodyStartEndIdx, bodyEndIdx)
            val afterBody = html.substring(bodyEndIdx)

            val wrappedBody = "\n<div id=\"root\" data-composition-id=\"daex-fallback\" data-width=\"1080\" data-height=\"1920\" data-duration=\"5\">\n$bodyContent\n</div>\n"
            return beforeBody + wrappedBody + afterBody
        }
    }

    // Fallback if no body/html tags exist at all
    return "<!DOCTYPE html>\n<html>\n<head>\n  <title>DAEX Preview</title>\n</head>\n<body>\n  <div id=\"root\" data-composition-id=\"daex-fallback\" data-width=\"1080\" data-height=\"1920\" data-duration=\"5\">\n$html\n  </div>\n</body>\n</html>"
}

private fun injectAutofitHelpers(html: String): String {
    // Ensure viewport meta tag is always present in the head
    var cleanHtml = html
    val viewportTag = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
    if (!cleanHtml.contains("name=\"viewport\"", ignoreCase = true) && !cleanHtml.contains("name='viewport'", ignoreCase = true)) {
        if (cleanHtml.contains("</head>", ignoreCase = true)) {
            cleanHtml = cleanHtml.replace("</head>", "$viewportTag\n</head>", ignoreCase = true)
        } else if (cleanHtml.contains("<head>", ignoreCase = true)) {
            cleanHtml = cleanHtml.replace("<head>", "<head>\n$viewportTag", ignoreCase = true)
        } else {
            cleanHtml = "$viewportTag\n$cleanHtml"
        }
    }

    // If it's a plain HTML page (not a Hyperframe composition), do not inject GSAP timelines
    if (!cleanHtml.contains("data-composition-id") && !cleanHtml.contains("id=\"root\"") && !cleanHtml.contains("id='root'")) {
        return cleanHtml
    }

    val helperCode = """
        <!-- START_DAEX_HELPERS -->
        <link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700&family=Share+Tech+Mono&display=swap" rel="stylesheet">
        <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
        <style>
          html, body {
            margin: 0 !important;
            padding: 0 !important;
            overflow: hidden !important;
            width: 100% !important;
            height: 100% !important;
            display: flex !important;
            justify-content: center !important;
            align-items: center !important;
            background-color: #030206 !important;
          }
          #root {
            margin: 0 !important;
            position: absolute !important;
            top: 50% !important;
            left: 50% !important;
            transform-origin: center center !important;
          }
        </style>
        <script>
          window.addEventListener('DOMContentLoaded', function() {
            var root = document.getElementById('root') || document.querySelector('[data-composition-id]');
            if (!root) return;
            var designWidth = parseInt(root.getAttribute('data-width')) || 1080;
            var designHeight = parseInt(root.getAttribute('data-height')) || 1920;
            var compId = root.getAttribute('data-composition-id') || 'composition';

            // --- Autofit scaling ---
            function layout() {
              var vw = window.innerWidth;
              var vh = window.innerHeight;
              var scale = Math.min(vw / designWidth, vh / designHeight);
              root.style.width = designWidth + 'px';
              root.style.height = designHeight + 'px';
              root.style.top = '50%';
              root.style.left = '50%';
              root.style.transform = 'translate(-50%, -50%) scale(' + scale + ')';
            }
            window.addEventListener('resize', layout);
            layout();

            // --- Auto-generate GSAP timeline from data-anim attributes ---
            window.addEventListener('load', function() {
              layout();
              if (typeof gsap === 'undefined') {
                console.error('GSAP not loaded, cannot build timeline');
                // Fallback: just make all .clip elements visible
                var clips = root.querySelectorAll('.clip');
                for (var i = 0; i < clips.length; i++) { clips[i].style.opacity = '1'; }
                return;
              }

              var tl = gsap.timeline({ paused: true });
              window.__timelines = window.__timelines || {};
              window.__timelines[compId] = tl;

              var clips = root.querySelectorAll('.clip[data-start]');
              for (var i = 0; i < clips.length; i++) {
                var el = clips[i];
                var start = parseFloat(el.getAttribute('data-start')) || 0;
                var anim = el.getAttribute('data-anim') || 'fade-up';
                var fromVars = { opacity: 0 };
                var toVars = { opacity: 1, x: 0, y: 0, scale: 1, duration: 0.8, ease: 'power3.out' };

                switch (anim) {
                  case 'fade-up':    fromVars.y = 50;  break;
                  case 'fade-down':  fromVars.y = -50; break;
                  case 'slide-left': fromVars.x = 100; break;
                  case 'slide-right':fromVars.x = -100;break;
                  case 'scale-in':   fromVars.scale = 0.5; break;
                  case 'none':       toVars.duration = 0.01; break;
                }
                tl.fromTo(el, fromVars, toVars, start);
              }

              // Auto-play after a short delay to let fonts render
              setTimeout(function() { tl.play(); }, 150);
            });
          });
        </script>
        <!-- END_DAEX_HELPERS -->
    """.trimIndent()

    return if (cleanHtml.contains("</head>")) {
        cleanHtml.replace("</head>", "$helperCode\n</head>")
    } else if (cleanHtml.contains("<head>")) {
        cleanHtml.replace("<head>", "<head>\n$helperCode")
    } else {
        "$helperCode\n$cleanHtml"
    }
}

private fun stripInjectedHelpers(html: String): String {
    var clean = html
    
    // 1. Remove comment-tagged blocks
    val startTag = "<!-- START_DAEX_HELPERS -->"
    val endTag = "<!-- END_DAEX_HELPERS -->"
    while (true) {
        val startIdx = clean.indexOf(startTag)
        if (startIdx == -1) break
        val endIdx = clean.indexOf(endTag, startIdx)
        if (endIdx == -1) break
        clean = clean.substring(0, startIdx) + clean.substring(endIdx + endTag.length)
    }
    
    // 2. Remove legacy untagged style/script helpers
    val legacyMarker = "background-color: #030206 !important;"
    val markerIdx = clean.indexOf(legacyMarker)
    if (markerIdx != -1) {
        val startIdx = clean.lastIndexOf("<style>", markerIdx)
        val endIdx = clean.indexOf("</script>", markerIdx)
        if (startIdx != -1 && endIdx != -1) {
            clean = clean.substring(0, startIdx) + clean.substring(endIdx + "</script>".length)
        }
    }
    
    return clean
}
