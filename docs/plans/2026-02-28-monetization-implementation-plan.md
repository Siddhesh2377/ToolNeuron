# ToolNeuron Monetization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add freemium monetization to ToolNeuron via Google Play Billing, a JSON-driven theme engine, and feature gating - turning the app from fully free to a $9.99 Pro unlock model with future theme store.

**Architecture:** Google Play Billing Library v7 integrated via Hilt singleton → FeatureGateManager checks purchase state → all premium features soft-gated (show upgrade prompt, never crash). Theme engine reads JSON configs and provides CompositionLocal values to all Compose screens.

**Tech Stack:** Google Play Billing v7, Jetpack Compose, Hilt DI, Kotlin Serialization, DataStore, Room

**Dependency Chain:** `llama.cpp-custom` (C++) → `AiSystems/ai_gguf` (JNI SDK) → `ToolNeuron/libs/ai_gguf-release.aar` → ToolNeuron app

---

## Phase 1: Google Play Billing Infrastructure

### Task 1: Add Billing Library Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Step 1: Add billing version to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
playBilling = "7.1.1"
```

Add to `[libraries]`:
```toml
play-billing = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "playBilling" }
```

**Step 2: Add dependency to app module**

In `app/build.gradle.kts`, add to `dependencies {}`:
```kotlin
implementation(libs.play.billing)
```

**Step 3: Sync and verify build**

Run: `cd /home/home/AndroidStudioProjects/ToolNeuron && ./gradlew app:dependencies --configuration releaseRuntimeClasspath | grep billing`
Expected: billing-ktx:7.1.1 in dependency tree

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Google Play Billing Library v7.1.1"
```

---

### Task 2: Create BillingManager Singleton

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/billing/BillingManager.kt`

**Step 1: Implement BillingManager**

```kotlin
package com.dark.tool_neuron.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_PRO = "toolneuron_pro"
    }

    private val _isProUnlocked = MutableStateFlow(false)
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    private var billingClient: BillingClient? = null
    private var proProductDetails: ProductDetails? = null

    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingReady.value = true
                    queryPurchases()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingReady.value = false
            }
        })
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_PRO)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                proProductDetails = detailsList.firstOrNull()
            }
        }
    }

    fun queryPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchases.any {
                    it.products.contains(PRODUCT_PRO) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                _isProUnlocked.value = hasPro
                // Acknowledge unacknowledged purchases
                purchases.filter { !it.isAcknowledged }.forEach { purchase ->
                    acknowledgePurchase(purchase)
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(params) { /* logged */ }
    }

    fun launchPurchaseFlow(activity: Activity): BillingResult? {
        val details = proProductDetails ?: return null
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        return billingClient?.launchBillingFlow(activity, flowParams)
    }

    fun getProPrice(): String? {
        return proProductDetails?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (purchase.products.contains(PRODUCT_PRO)) {
                        _isProUnlocked.value = true
                    }
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}
```

**Step 2: Register in Hilt**

Add to `di/HiltModules.kt` - BillingManager is already `@Singleton @Inject`, so Hilt auto-provides it. No module needed.

**Step 3: Initialize in NVApplication**

In `NVApplication.kt`, inject and call `billingManager.initialize()` in `onCreate()`.

**Step 4: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/billing/BillingManager.kt
git commit -m "feat: add BillingManager for Google Play Billing v7"
```

---

### Task 3: Create FeatureGateManager

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/billing/FeatureGateManager.kt`

**Step 1: Implement FeatureGateManager**

```kotlin
package com.dark.tool_neuron.billing

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureGateManager @Inject constructor(
    private val billingManager: BillingManager
) {
    val isProUnlocked: StateFlow<Boolean> = billingManager.isProUnlocked

    // Free tier limits
    object FreeLimits {
        const val MAX_PERSONA_CARDS = 3
        const val MAX_AI_MEMORIES = 5
        const val MAX_RAG_BASES = 1
        const val MAX_IMAGE_SIZE = 512
        val FREE_TTS_VOICES = setOf("F1", "M1")
        val FREE_PLUGINS = setOf("Calculator", "Clipboard", "Date & Time")
    }

    // Feature checks
    fun canUseCharacterIntelligence(): Boolean = isProUnlocked.value
    fun canUseAdaptiveLearning(): Boolean = isProUnlocked.value
    fun canUseThinkingVisualization(): Boolean = isProUnlocked.value
    fun canUseMultimodal(): Boolean = isProUnlocked.value

    fun canCreatePersona(currentCount: Int): Boolean =
        isProUnlocked.value || currentCount < FreeLimits.MAX_PERSONA_CARDS

    fun canAddMemory(currentCount: Int): Boolean =
        isProUnlocked.value || currentCount < FreeLimits.MAX_AI_MEMORIES

    fun canAddRag(currentCount: Int): Boolean =
        isProUnlocked.value || currentCount < FreeLimits.MAX_RAG_BASES

    fun canUseTtsVoice(voiceId: String): Boolean =
        isProUnlocked.value || voiceId in FreeLimits.FREE_TTS_VOICES

    fun canUsePlugin(pluginName: String): Boolean =
        isProUnlocked.value || pluginName in FreeLimits.FREE_PLUGINS

    fun canUseImageSize(size: Int): Boolean =
        isProUnlocked.value || size <= FreeLimits.MAX_IMAGE_SIZE

    fun canUseInpainting(): Boolean = isProUnlocked.value

    fun canImportExportPersonas(): Boolean = isProUnlocked.value

    fun canUseKnowledgeGraph(): Boolean = isProUnlocked.value

    fun canUseEncryptedRag(): Boolean = isProUnlocked.value
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/billing/FeatureGateManager.kt
git commit -m "feat: add FeatureGateManager with free/pro tier limits"
```

---

### Task 4: Create ProUpgradeScreen

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/screen/ProUpgradeScreen.kt`

**Step 1: Implement upgrade screen**

A clean, non-intrusive full-screen upgrade prompt. Shows feature comparison, price, and single "Unlock Pro" button. Material 3 styling matching existing app design. Key composables:

- `ProUpgradeScreen(onDismiss, billingManager, featureGateManager)` - Full screen
- `ProUpgradeBottomSheet(onDismiss, billingManager, triggerFeature)` - Bottom sheet for inline prompts
- `FeatureComparisonTable()` - Side-by-side free vs pro
- `ProBadge()` - Small "PRO" chip to mark premium features in settings

Design principles:
- Show value, not nag
- One tap to purchase
- Easy to dismiss
- Shows localized price from Google Play

**Step 2: Add navigation route**

Add `object ProUpgrade : Screen("pro_upgrade")` to navigation in `MainActivity.kt`.

**Step 3: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/ProUpgradeScreen.kt
git commit -m "feat: add ProUpgradeScreen with feature comparison"
```

---

### Task 5: Integrate Feature Gates Across the App

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/SettingsScreen.kt` (TTS voice picker, plugin toggles)
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/PersonaScreen.kt` (persona card limit)
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/PersonaEditorScreen.kt` (character intelligence gate)
- Modify: `app/src/main/java/com/dark/tool_neuron/plugins/PluginManager.kt` (plugin enable gate)
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt` (memory limit, thinking viz)
- Modify: `app/src/main/java/com/dark/tool_neuron/activity/RagActivity.kt` (RAG limit)

**Approach:** At each gate point:
1. Check `featureGateManager.canUse*()`
2. If false, show `ProUpgradeBottomSheet` with the feature name that triggered it
3. Never crash or hide features - show them with a "PRO" badge and prompt on tap

**Gate Points (specific):**

| Location | Gate | Behavior when free |
|----------|------|--------------------|
| PersonaScreen: "Add Persona" button | `canCreatePersona(count)` | Show upgrade sheet |
| PersonaEditorScreen: personality sliders | `canUseCharacterIntelligence()` | Sliders disabled + PRO badge |
| PersonaEditorScreen: export button | `canImportExportPersonas()` | Show upgrade sheet |
| SettingsScreen: TTS voice picker | `canUseTtsVoice(voiceId)` | Lock icon on premium voices |
| PluginManager: enablePlugin() | `canUsePlugin(name)` | Show upgrade sheet |
| ChatViewModel: memory extraction | `canAddMemory(count)` | Skip extraction silently when at limit |
| ChatViewModel: thinking block render | `canUseThinkingVisualization()` | Show raw text instead of collapsible |
| RagActivity: add RAG | `canAddRag(count)` | Show upgrade sheet |
| Image gen params: resolution picker | `canUseImageSize(size)` | Lock 768/1024 options |
| Image gen: inpainting toggle | `canUseInpainting()` | Show upgrade sheet |

**Step 1: Add FeatureGateManager to ViewModels via Hilt injection**

**Step 2: Add gate checks at each point listed above**

**Step 3: Add PRO badges next to premium features in Settings**

**Step 4: Test all gate points manually**

**Step 5: Commit**

```bash
git commit -m "feat: integrate feature gates across all screens"
```

---

## Phase 2: Theme Engine

### Task 6: Design Theme JSON Schema

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/theme/ThemeConfig.kt`

**Step 1: Define serializable theme config**

```kotlin
package com.dark.tool_neuron.ui.theme

import kotlinx.serialization.Serializable

@Serializable
data class ThemeConfig(
    val id: String,
    val name: String,
    val author: String = "ToolNeuron",
    val version: Int = 1,

    // Colors
    val colors: ThemeColors,

    // Typography
    val fontFamily: String = "manrope",          // "manrope", "jetbrains_mono", "system"
    val useUppercaseLabels: Boolean = false,
    val letterSpacing: Float = 0f,               // in sp

    // Shapes
    val cornerRadius: Float = 12f,               // in dp, 0 = sharp corners

    // Decorative
    val accentBorderWidth: Float = 0f,           // 0 = no accent borders
    val showCornerBrackets: Boolean = false,      // Industrial-style corner accents
    val showGridBackground: Boolean = false,      // Dot-grid background
    val scrollbarWidth: Float = 4f,              // in dp

    // Status indicators
    val squareStatusDots: Boolean = false         // false = round, true = square
)

@Serializable
data class ThemeColors(
    // Surface colors
    val background: String,           // hex "#0b0b0b"
    val surface: String,              // hex "#111111"
    val surfaceVariant: String,       // hex "#1a1a1a"

    // Content colors
    val onBackground: String,         // hex "#d4d4d4"
    val onSurface: String,            // hex "#d4d4d4"
    val onSurfaceVariant: String,     // hex "#888888"

    // Accent
    val primary: String,              // hex "#f59e0b"  (amber)
    val onPrimary: String,            // hex "#000000"

    // State colors
    val error: String = "#ef4444",
    val success: String = "#22c55e",
    val info: String = "#3b82f6",

    // If null, derives from above
    val primaryContainer: String? = null,
    val secondaryContainer: String? = null
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/theme/ThemeConfig.kt
git commit -m "feat: add ThemeConfig serializable schema for JSON themes"
```

---

### Task 7: Create ThemeEngine

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/theme/ThemeEngine.kt`
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/theme/Theme.kt`
- Create: `app/src/main/assets/themes/default.json`
- Create: `app/src/main/assets/themes/industrial-label.json`

**Step 1: Implement ThemeEngine singleton**

```kotlin
package com.dark.tool_neuron.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

val LocalThemeConfig = compositionLocalOf { ThemeConfig.DEFAULT }

object ThemeEngine {
    private val json = Json { ignoreUnknownKeys = true }

    private val _activeTheme = MutableStateFlow(ThemeConfig.DEFAULT)
    val activeTheme: StateFlow<ThemeConfig> = _activeTheme.asStateFlow()

    private val _availableThemes = MutableStateFlow<List<ThemeConfig>>(emptyList())
    val availableThemes: StateFlow<List<ThemeConfig>> = _availableThemes.asStateFlow()

    fun init(context: Context) {
        loadBuiltInThemes(context)
        // Load saved preference and apply
    }

    private fun loadBuiltInThemes(context: Context) {
        val themes = mutableListOf<ThemeConfig>()
        context.assets.list("themes")?.forEach { filename ->
            if (filename.endsWith(".json")) {
                val text = context.assets.open("themes/$filename").bufferedReader().readText()
                themes.add(json.decodeFromString<ThemeConfig>(text))
            }
        }
        _availableThemes.value = themes
    }

    fun applyTheme(themeId: String) {
        val theme = _availableThemes.value.find { it.id == themeId } ?: return
        _activeTheme.value = theme
    }

    fun buildColorScheme(config: ThemeConfig, isDark: Boolean): ColorScheme { /* parse hex → Color */ }
    fun buildTypography(config: ThemeConfig): Typography { /* map fontFamily string */ }
    fun buildShapes(config: ThemeConfig): Shapes { /* RoundedCornerShape from cornerRadius */ }
}
```

**Step 2: Create default.json theme (current Material 3 look)**

Matches current `NeuroVerseTheme` colors/shapes.

**Step 3: Create industrial-label.json theme**

```json
{
    "id": "industrial-label",
    "name": "Industrial Label",
    "author": "ToolNeuron",
    "colors": {
        "background": "#0b0b0b",
        "surface": "#111111",
        "surfaceVariant": "#1a1a1a",
        "onBackground": "#d4d4d4",
        "onSurface": "#d4d4d4",
        "onSurfaceVariant": "#666666",
        "primary": "#f59e0b",
        "onPrimary": "#000000",
        "error": "#ef4444",
        "success": "#22c55e",
        "info": "#3b82f6"
    },
    "fontFamily": "jetbrains_mono",
    "useUppercaseLabels": true,
    "letterSpacing": 1.2,
    "cornerRadius": 0,
    "accentBorderWidth": 1.5,
    "showCornerBrackets": true,
    "showGridBackground": true,
    "scrollbarWidth": 6,
    "squareStatusDots": true
}
```

**Step 4: Refactor Theme.kt**

Modify `NeuroVerseTheme` to read from `ThemeEngine.activeTheme` and provide `LocalThemeConfig`. Keep existing `rDp()` and `rSp()` helpers untouched. The theme composable delegates color/shape/typography to ThemeEngine but keeps dynamic color as fallback for default theme.

**Step 5: Add theme picker to SettingsScreen**

Add a "Theme" section in SettingsScreen with a row of theme cards (thumbnail + name). Tapping applies the theme.

**Step 6: Persist selected theme in DataStore**

Add `SELECTED_THEME_ID` key to `AppSettingsDataStore`.

**Step 7: Commit**

```bash
git commit -m "feat: add JSON-driven ThemeEngine with default + industrial-label themes"
```

---

## Phase 3: Bug Fixes (Critical Stability)

### Task 8: Fix #77 - Cancel Loses Messages

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt`

**Root cause:** In `handleTextStop()` (~line 2205), `resetStreamingState()` is called immediately after `viewModelScope.launch {}`, not awaiting the DB save. The launched coroutine clears `currentGeneratedContent` before the save completes.

**Fix:** Capture content before launch, await the save inside the coroutine, THEN reset state.

```kotlin
private fun handleTextStop() {
    val chatId = _currentChatId.value
    val userMsg = currentUserMessage
    val content = currentGeneratedContent  // capture before reset
    val metrics = currentMetrics

    if (chatId != null && userMsg != null && content.isNotEmpty()) {
        viewModelScope.launch {
            if (!userMessageAdded) { _messages.add(userMsg); userMessageAdded = true }
            val assistantMessage = Messages(/* ... use captured content ... */)
            _messages.add(assistantMessage)
            chatManager.addAssistantMessage(chatId, "$content [stopped]", /* ... */)
            resetStreamingState()  // AFTER save completes
        }
    } else {
        if (userMsg != null && !userMessageAdded) {
            _messages.add(userMsg)
            userMessageAdded = true
        }
        resetStreamingState()
    }
}
```

**Commit:** `fix: prevent message loss when cancelling generation (#77)`

---

### Task 9: Fix #66 - Can't Create New Chat

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt`

**Root cause:** Nested `onSuccess` callbacks in `createChatWithMessages()` (~line 2050). If any inner step fails, error isn't propagated.

**Fix:** Flatten the chain using coroutine sequential calls with explicit error handling.

```kotlin
private suspend fun createChatWithMessages(prompt: String, ...) {
    val newChatId = chatManager.createNewChat().getOrElse {
        showError("Failed to create chat: ${it.message}")
        return
    }
    _currentChatId.value = newChatId

    chatManager.addUserMessage(newChatId, prompt).onFailure {
        showError("Failed to save message: ${it.message}")
        return
    }

    // ... continue sequentially
}
```

**Commit:** `fix: flatten chat creation chain to prevent silent failures (#66)`

---

### Task 10: Fix #69 - Response Stops Incomplete

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt`

**Root cause:** `detectRepetitionTrimIndex()` (~line 1328) triggers false positives. Checking every 80 characters is too aggressive.

**Fix:** Increase check interval to 200 chars, require 3+ repetitions (not 2), and add a minimum pattern length of 20 chars.

**Commit:** `fix: reduce false positive repetition detection (#69)`

---

### Task 11: Fix #72/#73/#78 - Thinking Model Support

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/components/ThinkingBlock.kt`
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/home_screen/BodyContent.kt`
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt`

**Implementation:**
1. Parse `<think>...</think>` tags from streamed output
2. Create `ThinkingBlockComposable` - collapsible card showing thinking text in muted style
3. In free tier: show raw text (no collapsible). In pro: show collapsible visualization.
4. Handle streaming: buffer `<think>` content separately, render inline

```kotlin
@Composable
fun ThinkingBlock(
    thinkingText: String,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    isPro: Boolean
) {
    if (!isPro) {
        // Show raw text with <think> tags visible
        Text(thinkingText, style = MaterialTheme.typography.bodySmall)
    } else {
        // Collapsible card
        Card(onClick = onToggle) {
            Row { Text("Thinking..."); Icon(if (isCollapsed) expandMore else expandLess) }
            AnimatedVisibility(visible = !isCollapsed) {
                Text(thinkingText, color = onSurfaceVariant)
            }
        }
    }
}
```

**Commit:** `feat: add thinking model visualization with collapsible blocks (#72 #73 #78)`

---

## Phase 4: Pro Plugins & Tool Calling Enhancements

### Task 12: Add Premium Plugin Infrastructure

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/plugins/PluginManager.kt`
- Modify: `app/src/main/java/com/dark/tool_neuron/plugins/api/SuperPlugin.kt`

**Step 1: Add tier field to PluginInfo**

Add `val isPremium: Boolean = false` to `PluginInfo` data class (or wherever plugin metadata is defined).

**Step 2: Mark premium plugins**

- WebSearchPlugin: `isPremium = true`
- FileManagerPlugin: `isPremium = true`
- DevUtilsPlugin: `isPremium = true`
- DeviceInfoPlugin: `isPremium = true`
- Calculator: `isPremium = false` (free)
- Clipboard: `isPremium = false` (free)
- DateTime: `isPremium = false` (free)

**Step 3: Gate in PluginManager.enablePlugin()**

```kotlin
fun enablePlugin(name: String): Boolean {
    if (!featureGateManager.canUsePlugin(name)) {
        // Emit event for UI to show upgrade sheet
        _upgradePromptEvent.emit(name)
        return false
    }
    // existing enable logic...
}
```

**Commit:** `feat: gate premium plugins behind Pro tier`

---

### Task 13: Build New Premium Plugins (Future Revenue Drivers)

These are new plugins that add real value to Pro tier. Build 2-3 to start:

**Plugin ideas leveraging your tool calling + grammar constraints:**

1. **Code Runner Plugin** - Execute Python/JS snippets in a sandboxed interpreter (WebAssembly). LLM writes code, plugin runs it, returns output. Huge for coding assistance.

2. **Smart Summarizer Plugin** - Summarize web pages, PDFs, long texts. Uses the existing DocumentParser + LLM pipeline but as a callable tool.

3. **System Automation Plugin** - Create Android intents, set alarms, compose emails, create calendar events via tool calling. LLM generates structured intent JSON, plugin executes.

4. **Translation Plugin** - Leverage multilingual models. LLM tool-calls with source/target language, plugin manages prompt engineering for high-quality translation.

Each plugin follows existing `SuperPlugin` interface. Implementation details per plugin in separate tasks.

**Commit per plugin:** `feat: add [plugin name] premium plugin`

---

## Phase 5: Polish & Launch Prep

### Task 14: Update Play Store Listing

**Files:**
- Create: `docs/play-store/description.md` (reference for Play Store Console)

**Key changes:**
- Title: "ToolNeuron - Private AI Assistant" (keep concise)
- Short description: "Run AI offline. Chat, create images, speak - all on your device. No cloud, no subscriptions."
- Feature graphic highlighting: Privacy, Character Intelligence, Plugins, Image Gen
- Screenshots showing: Chat, Image Gen, Character Intelligence, Theme switching
- Mention Pro features in "What's New"

### Task 15: Set Up Google Play In-App Products

**In Google Play Console (manual steps - not code):**

1. Go to Monetize → Products → In-app products
2. Create product:
   - Product ID: `toolneuron_pro`
   - Name: "ToolNeuron Pro"
   - Description: "Unlock all premium features: Character Intelligence Engine, all TTS voices, all plugins, unlimited RAG, full image generation, and more."
   - Price: $9.99 USD (Google auto-converts to local currencies)
3. Activate the product
4. Test with license testers before publishing

### Task 16: Create Release Build & Test

**Steps:**
1. Bump version: `versionCode = 27`, `versionName = "2.1.0"`
2. Build release APK: `./gradlew assembleRelease`
3. Test billing flow with Google Play test environment
4. Verify all feature gates work correctly
5. Verify theme switching works
6. Test on low-end device (min SDK 31)
7. Upload to Play Store internal testing track
8. Graduate to production after 3-day soak

---

## Execution Order (Dependencies)

```
Task 1 (billing deps) ──→ Task 2 (BillingManager) ──→ Task 3 (FeatureGateManager) ──→ Task 4 (ProUpgradeScreen)
                                                                                           ↓
Task 6 (theme schema) ──→ Task 7 (ThemeEngine) ──────────────────────────────────→ Task 5 (integrate gates)
                                                                                           ↓
Task 8 (fix #77) ─┐                                                                       ↓
Task 9 (fix #66) ─┤──→ Task 11 (thinking viz) ──→ Task 12 (plugin gates) ──→ Task 13 (new plugins)
Task 10 (fix #69) ┘                                                                       ↓
                                                                              Task 14-16 (launch)
```

**Parallelizable groups:**
- Tasks 8, 9, 10 (bug fixes) can be done in parallel
- Tasks 1-4 (billing) and 6-7 (theme) can be done in parallel
- Task 5 depends on both billing AND theme being done

---

## Google Play Console Setup (Pre-requisites - Manual)

Before Task 15 can work, you need to:
1. Have a Google Play Developer account ($25 one-time fee - you already have this since the app is on Play Store)
2. Create the in-app product `toolneuron_pro` in the Play Console
3. Add license testers (your Google account) for testing purchases
4. Upload at least one APK/AAB with billing permission to internal testing

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Users angry about features being gated | NEVER remove existing free features. Only gate NEW features. Grandfather existing users. |
| Billing integration bugs | Test exhaustively with Google's test environment. Handle all error codes gracefully. |
| Open source users bypass gates | Expected and fine. They contribute code, file issues, build community. Play Store users pay for convenience. |
| Low conversion rate | Start conservative. Monitor metrics for 2 weeks. Adjust free/pro split if needed. |
| Theme engine performance | ThemeConfig is tiny JSON. Parse once on startup, cache in memory. Zero runtime overhead. |
