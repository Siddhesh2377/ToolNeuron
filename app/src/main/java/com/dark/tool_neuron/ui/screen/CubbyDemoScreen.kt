package com.dark.tool_neuron.ui.screen

import android.opengl.GLSurfaceView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cubby.engine.CubbyEngine
import com.cubby.engine.SampleGenerator
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.InfoBadge
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.theme.rDp
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CubbyDemoScreen() {
    val engine = remember { CubbyEngine() }
    val isPlaying by engine.isPlaying.collectAsState()
    val progress by engine.progress.collectAsState()
    val isLoaded by engine.isLoaded.collectAsState()
    var currentAnim by remember { mutableStateOf("idle") }
    var selectedColor by remember { mutableStateOf(0) }

    val bodyColors = listOf(
        0xC8A2E8FF.toInt() to "Lavender",
        0xFF9AA2FF.toInt() to "Coral",
        0xA2E8C8FF.toInt() to "Mint",
        0xFFE0A2FF.toInt() to "Peach",
        0xA2C8E8FF.toInt() to "Sky",
        0xE8D4A2FF.toInt() to "Sand"
    )

    // Load sample data into engine once GL is ready
    val characterData = remember { SampleGenerator.generateCharacter() }
    val idleData = remember { SampleGenerator.generateIdleAnimation() }
    val smileData = remember { SampleGenerator.generateSmileAnimation() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = rDp(Standards.SpacingLg)),
        verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
    ) {
        Spacer(Modifier.height(rDp(Standards.SpacingXl)))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Cubby Engine",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Character Animation Runtime",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                )
            }
            StatusBadge(
                text = if (isLoaded) "Engine Ready" else "Initializing",
                isActive = isLoaded
            )
        }

        // GL Viewport
        StandardCard(
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(rDp(Standards.CardSmallCornerRadius)))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        GLSurfaceView(ctx).apply {
                            setEGLContextClientVersion(3)
                            setEGLConfigChooser(8, 8, 8, 8, 0, 0)
                            holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                            setZOrderOnTop(false)
                            preserveEGLContextOnPause = true
                            setRenderer(object : GLSurfaceView.Renderer {
                                var lastFrame = 0L

                                override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                                    engine.initRenderer()
                                    engine.loadCharacter(characterData)
                                    engine.loadAnimation("idle", idleData)
                                    engine.loadAnimation("smile", smileData)
                                    engine.play("idle")
                                    lastFrame = System.nanoTime()
                                }

                                override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {}

                                override fun onDrawFrame(gl: GL10?) {
                                    val now = System.nanoTime()
                                    val delta = ((now - lastFrame) / 1_000_000f).coerceAtMost(100f)
                                    lastFrame = now
                                    engine.render(delta, width, height)
                                }
                            })
                            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        }
                    }
                )

                // Overlay badges
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(rDp(Standards.SpacingSm)),
                    horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
                ) {
                    InfoBadge(text = "OpenGL ES 3.0")
                    InfoBadge(
                        text = currentAnim,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.6f),
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Progress
        if (isPlaying) {
            val animProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "progress"
            )
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDp(4.dp))
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(0.1f)
            )
        }

        // Playback Controls
        SectionHeader(title = "Playback")
        StandardCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Idle",
                    isSelected = currentAnim == "idle" && isPlaying,
                    onClick = {
                        engine.play("idle", transitionMs = 300)
                        currentAnim = "idle"
                    }
                )
                AnimButton(
                    icon = Icons.Default.Animation,
                    label = "Smile",
                    isSelected = currentAnim == "smile" && isPlaying,
                    onClick = {
                        engine.play("smile", transitionMs = 200)
                        currentAnim = "smile"
                    }
                )
                AnimButton(
                    icon = Icons.Default.TouchApp,
                    label = "Bounce",
                    isSelected = false,
                    onClick = {
                        engine.impulse("body", 0f, -80f)
                    }
                )
                AnimButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.Stop,
                    label = if (isPlaying) "Pause" else "Stopped",
                    isSelected = !isPlaying,
                    onClick = {
                        if (isPlaying) engine.pause() else engine.resume()
                    }
                )
            }
        }

        // Color Palette
        SectionHeader(title = "Body Color")
        StandardCard(icon = Icons.Outlined.Palette, title = "Palette") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm)),
                verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
            ) {
                bodyColors.forEachIndexed { idx, (color, name) ->
                    val isSelected = selectedColor == idx
                    val scale by animateFloatAsState(
                        if (isSelected) 1.1f else 1f,
                        spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "colorScale$idx"
                    )
                    val borderColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        label = "border$idx"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedColor = idx
                            engine.setPartColor("body", color)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(rDp(36.dp))
                                .scale(scale)
                                .border(rDp(2.dp), borderColor, CircleShape)
                                .padding(rDp(2.dp))
                                .background(
                                    Color(
                                        red = (color ushr 24 and 0xFF) / 255f,
                                        green = (color ushr 16 and 0xFF) / 255f,
                                        blue = (color ushr 8 and 0xFF) / 255f,
                                        alpha = 1f
                                    ),
                                    CircleShape
                                )
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                        )
                    }
                }
            }
        }

        // Engine Info
        SectionHeader(title = "Engine Info")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            InfoCard(
                title = "Format",
                value = ".cubby",
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                title = "Renderer",
                value = "GL ES 3.0",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            InfoCard(
                title = "Physics",
                value = "Spring",
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                title = "Parts",
                value = "8",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(rDp(Standards.SpacingXl)))
    }

    DisposableEffect(Unit) {
        onDispose { engine.destroy() }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = rDp(Standards.SpacingMd),
                vertical = rDp(Standards.SpacingSm)
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        if (isSelected) 1.05f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "btnScale"
    )
    val containerColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary.copy(0.15f)
        else MaterialTheme.colorScheme.primary.copy(0.06f),
        label = "btnColor"
    )
    val contentColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "btnContent"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
    ) {
        FilledIconButton(
            onClick = onClick,
            shape = MaterialShapes.Square.toShape(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = Modifier
                .size(rDp(44.dp))
                .scale(scale)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(rDp(20.dp)))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}
