package com.daex.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daex.android.ui.theme.DaexTheme

@Composable
fun DaexLogo(
    size: Dp = 24.dp,
    ambient: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_transition")
    
    // Blade data: Paths are simplified for Canvas drawing
    val blades = listOf(
        BladeData(
            body = listOf(Offset(20f, 80f), Offset(40f, 40f), Offset(46f, 40f), Offset(26f, 80f)),
            tip = listOf(Offset(40f, 40f), Offset(70f, 16f), Offset(72f, 18f), Offset(46f, 40f)),
            color = DaexTheme.colors.primary
        ),
        BladeData(
            body = listOf(Offset(34f, 80f), Offset(49f, 50f), Offset(55f, 50f), Offset(40f, 80f)),
            tip = listOf(Offset(49f, 50f), Offset(74f, 30f), Offset(76f, 32f), Offset(55f, 50f)),
            color = DaexTheme.colors.primary.copy(alpha = 0.7f)
        ),
        BladeData(
            body = listOf(Offset(48f, 80f), Offset(58f, 60f), Offset(64f, 60f), Offset(54f, 80f)),
            tip = listOf(Offset(58f, 60f), Offset(78f, 44f), Offset(80f, 46f), Offset(64f, 60f)),
            color = DaexTheme.colors.primary.copy(alpha = 0.4f)
        ),
        BladeData(
            body = listOf(Offset(62f, 80f), Offset(67f, 70f), Offset(73f, 70f), Offset(68f, 80f)),
            tip = listOf(Offset(67f, 70f), Offset(82f, 58f), Offset(84f, 60f), Offset(73f, 70f)),
            color = DaexTheme.colors.primary.copy(alpha = 0.2f)
        )
    )

    val tipOffsets = blades.mapIndexed { index, _ ->
        if (ambient) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 5000 // Increased to accommodate stagger
                        val start = index * 300
                        0f at start
                        1f at (start + 1000)
                        1f at (start + 3000)
                        0f at (start + 4000)
                        0f at 5000 // End at 0
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "tip_offset_$index"
            )
        } else {
            remember { mutableStateOf(0f) }
        }
    }

    Canvas(modifier = Modifier.size(size)) {
        val scale = this.size.width / 100f
        
        blades.forEachIndexed { index, blade ->
            // Draw Body
            val bodyPath = Path().apply {
                moveTo(blade.body[0].x * scale, blade.body[0].y * scale)
                lineTo(blade.body[1].x * scale, blade.body[1].y * scale)
                lineTo(blade.body[2].x * scale, blade.body[2].y * scale)
                lineTo(blade.body[3].x * scale, blade.body[3].y * scale)
                close()
            }
            drawPath(bodyPath, blade.color)

            // Draw Tip
            val tipP = Path().apply {
                moveTo(blade.tip[0].x * scale, blade.tip[0].y * scale)
                lineTo(blade.tip[1].x * scale, blade.tip[1].y * scale)
                lineTo(blade.tip[2].x * scale, blade.tip[2].y * scale)
                lineTo(blade.tip[3].x * scale, blade.tip[3].y * scale)
                close()
            }
            
            val translateX = tipOffsets[index].value * 2.5f * scale
            withTransform({
                translate(left = translateX)
            }) {
                drawPath(tipP, blade.color)
            }
        }
    }
}

private data class BladeData(
    val body: List<Offset>,
    val tip: List<Offset>,
    val color: Color
)

@Composable
fun DaexLoader(
    size: Dp = 40.dp
) {
    val colors = DaexTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "loader_transition")
    
    val ring1Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1_rotation"
    )
    
    val ring2Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2_rotation"
    )
    
    val coreOpacity by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_opacity"
    )

    Canvas(modifier = Modifier.size(size)) {
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val canvasSize = this.size.width
        
        // Core
        drawCircle(
            color = colors.primary.copy(alpha = coreOpacity),
            radius = canvasSize * 0.08f,
            center = Offset(cx, cy)
        )
        
        // Ring 1
        withTransform({
            rotate(ring1Rotation, Offset(cx, cy))
        }) {
            drawCircle(
                color = colors.primary,
                radius = canvasSize * 0.18f,
                center = Offset(cx, cy),
                style = Stroke(
                    width = canvasSize * 0.02f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        intervals = floatArrayOf(
                            canvasSize * 0.18f * 0.6f,
                            canvasSize * 0.18f * 2.4f,
                            canvasSize * 0.18f * 1.2f,
                            canvasSize * 0.18f * 3f
                        ),
                        phase = 0f
                    )
                )
            )
        }
        
        // Ring 2
        withTransform({
            rotate(ring2Rotation, Offset(cx, cy))
        }) {
            drawCircle(
                color = colors.primary.copy(alpha = 0.4f),
                radius = canvasSize * 0.26f,
                center = Offset(cx, cy),
                style = Stroke(
                    width = canvasSize * 0.015f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        intervals = floatArrayOf(
                            canvasSize * 0.26f * 1.2f,
                            canvasSize * 0.26f * 2.4f,
                            canvasSize * 0.26f * 0.6f,
                            canvasSize * 0.26f * 1.5f
                        ),
                        phase = 0f
                    )
                )
            )
        }
    }
}

/**
 * The workspace's ambient background aura, reusable outside ExecutionScreen (e.g. onboarding).
 * Same visual language as the ExecutionScreen aura: two radial-gradient circles with a slow
 * breathing pulse and accelerometer tilt-parallax. `intensity` (0..1) scales both circles'
 * alphas so the effect can fade in progressively.
 */
@Composable
fun AmbientAura(
    intensity: Float,
    modifier: Modifier = Modifier,
    color: Color = DaexTheme.colors.primary,
    tiltEnabled: Boolean = true
) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
    }
    val accelerometer = remember(sensorManager) {
        sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
    }

    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(tiltEnabled, accelerometer) {
        if (!tiltEnabled || accelerometer == null) return@DisposableEffect onDispose {}

        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                event?.let {
                    val rawX = it.values[0]
                    val rawY = it.values[1]
                    tiltX = tiltX + 0.1f * (rawX - tiltX)
                    tiltY = tiltY + 0.1f * (rawY - tiltY)
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "AmbientAuraPulse")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientAuraScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (intensity <= 0f) return@drawBehind

                val maxShiftX = 35.dp.toPx()
                val maxShiftY = 35.dp.toPx()
                val shiftX = (-tiltX * 4.5f.dp.toPx()).coerceIn(-maxShiftX, maxShiftX)
                val shiftY = (tiltY * 4.5f.dp.toPx()).coerceIn(-maxShiftY, maxShiftY)

                val rightCenter = Offset(
                    x = size.width * 0.85f + shiftX,
                    y = size.height * 0.35f + shiftY
                )
                val rightRadius = size.width * 0.8f * auraScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.16f * intensity),
                            color.copy(alpha = 0.16f * 0.4f * intensity),
                            Color.Transparent
                        ),
                        center = rightCenter,
                        radius = rightRadius
                    ),
                    center = rightCenter,
                    radius = rightRadius
                )

                val leftCenter = Offset(
                    x = size.width * 0.15f + shiftX,
                    y = size.height * 0.75f + shiftY
                )
                val leftRadius = size.width * 0.6f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.08f * intensity),
                            Color.Transparent
                        ),
                        center = leftCenter,
                        radius = leftRadius
                    ),
                    center = leftCenter,
                    radius = leftRadius
                )
            }
    )
}
