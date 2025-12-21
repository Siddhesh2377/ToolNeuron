package com.dark.tool_neuron.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.R
import com.dark.tool_neuron.activity.GgufPickerActivity
import com.dark.tool_neuron.ui.screens.modelScreen.GGUFModelScreen
import com.dark.tool_neuron.ui.screens.modelScreen.InstalledModelsTab
import com.dark.tool_neuron.ui.screens.modelScreen.OpenRouterTab
import com.dark.tool_neuron.ui.screens.modelScreen.SherpaONNXTab
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.ModelScreenViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ============================================================================
// PROVIDER CONFIGURATION - Easy to extend!
// ============================================================================

private sealed class ModelProviderTab(
    val id: String, val label: String, val icon: Int, val description: String, val isOnline: Boolean
) {
    object LocalGGUF : ModelProviderTab(
        "local_gguf", "Local Models", R.drawable.text_ai_models, "On-device GGUF models", false
    )

    object OpenRouter : ModelProviderTab(
        "openrouter", "OpenRouter", R.drawable.open_router, "Cloud-based AI models", true
    )

    object SherpaONNX : ModelProviderTab(
        "sherpa", "Sherpa ONNX", R.drawable.stt_models, "Speech & Audio processing", false
    )

    object Installed : ModelProviderTab(
        "installed", "Installed", R.drawable.installed_models, "All configured models", false
    )

    companion object {
        fun all() = listOf(LocalGGUF, OpenRouter, SherpaONNX, Installed)
    }
}

// ============================================================================
// MAIN SCREEN
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: ModelScreenViewModel = viewModel()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val providers = remember { ModelProviderTab.all() }
    val pagerState = rememberPagerState(pageCount = { providers.size })

    // Derive current provider from pager state
    val currentProvider by remember {
        derivedStateOf { providers[pagerState.currentPage] }
    }

    // Haptic feedback on page change
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    Scaffold(topBar = {
        ModernTopBar(
            title = currentProvider.label,
            subtitle = currentProvider.description,
            isOnline = currentProvider.isOnline,
            onBack = onBack
        )
    }, floatingActionButton = {
        AnimatedVisibility(
            visible = currentProvider == ModelProviderTab.LocalGGUF,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    context.startActivity(Intent(context, GgufPickerActivity::class.java))
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(rDP(16.dp))
            ) {
                Icon(
                    Icons.TwoTone.FileOpen, "Import Model", modifier = Modifier.size(rDP(24.dp))
                )
            }
        }
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Enhanced Provider Tab Row with indicator
            ProviderTabRow(
                providers = providers, pagerState = pagerState, onTabClick = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                })

            // Horizontal Pager for swipeable content
            HorizontalPager(
                state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Parallax effect
                            val pageOffset =
                                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            alpha = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f) * 0.3f
                        }) {
                    when (providers[page]) {
                        ModelProviderTab.LocalGGUF -> GGUFModelScreen()
                        ModelProviderTab.OpenRouter -> OpenRouterTab(viewModel)
                        ModelProviderTab.SherpaONNX -> SherpaONNXTab(viewModel)
                        ModelProviderTab.Installed -> InstalledModelsTab(viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTopBar(
    title: String, subtitle: String, isOnline: Boolean, onBack: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold, fontSize = rSp(20.sp, maxSp = 22.sp)
                    )
                )
                if (isOnline) {
                    Surface(
                        shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = rDP(8.dp), vertical = rDP(4.dp)
                            ),
                            horizontalArrangement = Arrangement.spacedBy(rDP(4.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(rDP(6.dp))
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                "Online", style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = rSp(10.sp, maxSp = 12.sp)
                                ), color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = rSp(12.sp, maxSp = 14.sp)
                ), color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
        }
    }, navigationIcon = {
        IconButton(
            onClick = onBack, modifier = Modifier.size(rDP(48.dp))
        ) {
            Icon(
                painter = painterResource(R.drawable.next),
                contentDescription = "Back",
                modifier = Modifier
                    .size(rDP(24.dp))
                    .rotate(-180f)
            )
        }
    }, colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderTabRow(
    providers: List<ModelProviderTab>, pagerState: PagerState, onTabClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = rDP(12.dp)),
            contentPadding = PaddingValues(horizontal = rDP(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            itemsIndexed(providers, key = { _, it -> it.id }) { index, provider ->
                ProviderTab(
                    provider = provider,
                    isSelected = pagerState.currentPage == index,
                    onClick = { onTabClick(index) })
            }
        }

        // Animated indicator
        TabIndicator(
            providers = providers, pagerState = pagerState
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabIndicator(
    providers: List<ModelProviderTab>, pagerState: PagerState
) {
    val indicatorWidth = rDP(32.dp)
    val spacing = rDP(8.dp)
    val tabWidth = rDP(120.dp) // Approximate tab width
    val horizontalPadding = rDP(16.dp)

    val indicatorOffset by animateDpAsState(
        targetValue = horizontalPadding + (tabWidth + spacing) * pagerState.currentPage + (tabWidth - indicatorWidth) / 2,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "indicator_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rDP(3.dp))
            .padding(bottom = rDP(8.dp))
    ) {
        Box(modifier = Modifier
            .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
            .width(indicatorWidth)
            .height(rDP(3.dp))
            .clip(RoundedCornerShape(rDP(2.dp)))
            .background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun ProviderTab(
    provider: ModelProviderTab, isSelected: Boolean, onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant, animationSpec = tween(200), label = "tab_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "tab_content"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(rDP(16.dp)),
        color = backgroundColor,
        modifier = Modifier.height(rDP(48.dp))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = rDP(16.dp), vertical = rDP(12.dp)
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(10.dp))
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else contentColor.copy(alpha = 0.1f),
                modifier = Modifier.size(rDP(32.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(provider.icon),
                        contentDescription = null,
                        modifier = Modifier.size(rDP(18.dp)),
                        tint = contentColor
                    )
                }
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    provider.label, style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = rSp(14.sp, maxSp = 16.sp)
                    ), fontWeight = FontWeight.Bold, color = contentColor
                )
            }
        }
    }
}