# ToolNeuron Monetization Design

**Date:** 2026-02-28
**Author:** Siddhesh2377 + Claude (maintainer)
**Status:** Approved

## Context

ToolNeuron is a privacy-first, offline AI assistant for Android with 20+ Play Store reviews (mostly 4-5 stars), 100 Discord members, and zero monetization. Competitor Layla AI charges $14.99 one-time. ToolNeuron has more features AND a custom llama.cpp fork with a proprietary Character Intelligence Engine (7 neural intervention layers) that no competitor offers.

## Goal

Reach $1K-5K/month revenue, scaling to full-time income. No ads. Clean UX. Everything legal and ethical.

## Revenue Architecture

### Stream 1: Pro Unlock ($9.99 one-time)

Core premium features gated behind a single purchase via Google Play Billing Library v7.

**Free Tier:**
- Unlimited chat with any GGUF model
- Basic character cards (system prompt only, max 3)
- 2 TTS voices (1 female, 1 male)
- 3 basic plugins (calculator, clipboard, date/time)
- Basic image gen (512x512, txt2img only)
- 1 RAG knowledge base
- 5 AI memories
- 2 built-in themes

**Pro Tier ($9.99):**
- Everything in Free +
- Character Intelligence Engine (7-layer neural personality control)
- Adaptive Learning (SPSA forward-only learning)
- Save/load personality profiles (.lint files)
- Thinking model visualization (collapsible `<think>` blocks)
- All 10 TTS voices + speed control
- Unlimited character cards + TavernAI v2 import/export
- All 7 plugins (web search, file manager, dev utils, device info)
- Full image gen (all resolutions + inpainting + LoRA)
- Unlimited RAG knowledge bases + encrypted NeuronPackets
- Unlimited AI memory + knowledge graph
- Multimodal (vision models)

### Stream 2: Theme Store ($0.99-2.99 per theme, future)

JSON/binary-driven theme engine that modifies all Compose UI via config files.

**Launch themes (free, built-in):**
1. Current compact theme (Material 3)
2. Industrial Label theme (Blender-inspired dark/amber)

**Premium themes (future, after engine is built):**
- Sold individually via Google Play Billing consumable/non-consumable purchases
- Each theme = a JSON config defining: color palette, border radius, typography, accent decorations, scrollbar style, status indicators, decorative elements

### Stream 3: Neuron+ Subscription ($1.99/mo or $14.99/yr, future)

Added after user base grows. Benefits:
- Early access to new features
- 1 free premium theme per month
- Priority Discord support
- Exclusive character personality profiles

## Technical Architecture

### BillingManager
- Google Play Billing Library v7 integration
- Handles purchase flow, verification, and restoration
- Singleton, injected via Hilt

### FeatureGateManager
- Central authority for "is this feature available?"
- Checks BillingManager for purchase state
- All premium features check FeatureGateManager before executing
- Graceful degradation: shows upgrade prompt when free user hits limit

### ProUpgradeScreen
- Clean, non-intrusive upgrade UI
- Shows when user taps a gated feature
- Feature comparison table
- Single "Unlock Pro" button
- No dark patterns, no nagging

### ThemeEngine
- Reads theme config from JSON file or binary format
- Applies to all Compose UI via CompositionLocal providers
- Theme config defines: ColorScheme, Typography, Shapes, custom decorative tokens
- Theme.kt becomes a thin wrapper that delegates to ThemeEngine
- Themes stored in app assets (built-in) or internal storage (purchased/downloaded)

## Implementation Phases

### Phase 1: Bug Fixes (Priority - before any monetization)
- #66 Cannot create new chat
- #69 Response stops incomplete
- #71 Memory vault timeout
- #77 Cancelling gen loses messages
- #73/#72/#78 Thinking model support
- #58 RAG issues

### Phase 2: Google Play Billing + Feature Gating
- Add Google Play Billing Library v7
- Implement BillingManager singleton
- Implement FeatureGateManager
- Gate premium features (soft gates - show upgrade prompt)
- Implement ProUpgradeScreen
- Test purchase flow end-to-end

### Phase 3: Theme Engine
- Design theme JSON schema
- Implement ThemeEngine (reads JSON, provides CompositionLocal values)
- Refactor Theme.kt to delegate to ThemeEngine
- Create Industrial Label theme config
- Apply ThemeEngine across all screens

### Phase 4: Premium Features Polish
- Character Intelligence Engine integration in UI
- Thinking model `<think>` block renderer
- Personality profile save/load UI
- Multimodal model loading UI improvements

### Phase 5: Growth & Marketing
- Update Play Store listing (highlight Pro features, comparisons)
- Website update (tool-neuron.vercel.app)
- F-Droid distribution (free tier only)
- Reddit/community marketing
- YouTube demo videos

## Open Source Strategy

- GitHub repo remains open source (Apache 2.0)
- All code including billing is visible (transparency builds trust)
- Free tier on GitHub build = same as Play Store free tier
- Play Store = convenience (auto-updates, Play Billing, support)
- Pro features are in the code but gated by BillingManager
- This is standard practice (Signal, Bitwarden, etc.)

## Revenue Projections (Conservative)

| Month | Downloads | Conv. Rate | Pro Sales | Theme Sales | Monthly Rev | Net (after Google 15%) |
|-------|-----------|------------|-----------|-------------|-------------|----------------------|
| 1     | 500       | 3%         | 15        | 0           | $150        | $127                 |
| 3     | 2,000     | 5%         | 100       | 20          | $1,040      | $884                 |
| 6     | 5,000     | 5%         | 250       | 80          | $2,620      | $2,227               |
| 12    | 10,000    | 6%         | 600       | 200         | $6,400      | $5,440               |

## Competitive Positioning

| Feature | ToolNeuron Pro | Layla AI ($14.99) |
|---------|---------------|-------------------|
| Price | $9.99 | $14.99 |
| Neural personality control | 7 intervention layers | System prompt only |
| Adaptive learning | SPSA forward-only | None |
| Image generation | SD 1.5 + inpainting | None |
| RAG/documents | Yes, encrypted | None |
| AI memory | Persistent, categorized | Basic memory |
| Plugins | 7 extensible | None |
| TTS | 10 voices, 5 languages | 100+ voices |
| Custom themes | JSON-driven engine | Limited |
| Open source | Yes (Apache 2.0) | No |
