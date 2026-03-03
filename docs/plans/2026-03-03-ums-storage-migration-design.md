# UMS Storage Migration Design

**Date:** 2026-03-03
**Status:** Approved
**Scope:** Replace Room DB + VaultHelper/MemoryVault with UMS (Universal Message Store)

---

## 1. Goal

Migrate ToolNeuron from a triple-storage architecture (Room DB + MemoryVault + DataStore) to a dual-storage architecture (UMS + DataStore), with optional encryption. Preserve all existing user data with zero data loss.

## 2. Current Architecture (Before)

```
Room DB v12          — models, configs, rags, personas, memories, KG (7 entities, 12 migrations)
MemoryVault          — encrypted chat messages (AES-256, VaultHelper wrapper)
DataStore            — preferences (theme, active model/persona, flags)
NeuronPacket         — encrypted RAG graph payloads (.neuron files)
```

## 3. Target Architecture (After)

```
UMS                  — all structured data (8 collections, WAL, LZ4, optional AES-256-GCM)
DataStore            — quick key-value preferences only (theme, flags, active refs)
NeuronPacket         — encrypted RAG graph payloads (.neuron files, unchanged)
```

**Eliminated:**
- Room DB (all entities, DAOs, migrations, converters)
- VaultHelper / MemoryVault library
- InstalledRag entity (RAG metadata lives in NeuronPacket files)

**Kept:**
- DataStore for simple prefs
- NeuronPacket for .neuron RAG files

## 4. New Modules

Three source modules ported from ToolNeuron2:

| Module | Purpose | Native Lib | Dependencies |
|--------|---------|-----------|-------------|
| `system_encryptor` | AES-256-GCM, PBKDF2, HKDF via BoringSSL | `libsystem_encryptor.so` | BoringSSL (FetchContent) |
| `file_ops` | Secure file I/O, path guard, fsync | `libfile_ops.so` | None |
| `ums` | Universal Message Store (records, collections, WAL, indexes) | `libums.so` | system_encryptor, file_ops, LZ4, BoringSSL |

## 5. Encryption Modes

Users choose on first launch / upgrade:

### Regular Mode
- No encryption, no passphrase
- UMS stores records as plaintext + LZ4 compression
- Instant app startup
- WAL and indexes still active
- UMS manifest has `FLAG_PLAINTEXT_MODE = 0x0002`

### Protected Mode
- AES-256-GCM encryption per record
- Double-key wrapping: AppKey (Android Keystore) + UserKey (PBKDF2 from passphrase, 600k iterations)
- Passphrase required every app open
- For sensitive environments (medical, legal, military)

### C++ Changes Required
Add plaintext mode to UMS native layer:
- New manifest flag `FLAG_PLAINTEXT_MODE`
- Skip CryptoEngine encrypt/decrypt when flag is set
- Skip PBKDF2 key derivation on open
- LZ4 compression still runs
- WAL and indexes still run
- `create()` and `open()` accept mode parameter

## 6. UMS Collections & Record Schemas

### 6.1 `"models"` (replaces Room `models` table)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | BYTES | modelName |
| 2 | BYTES | modelPath |
| 3 | VARINT | pathType (0=Local, 1=SAF) |
| 4 | VARINT | providerType (0=GGUF, 1=Diffusion, 2=TTS) |
| 5 | FIXED64 | fileSize |
| 6 | VARINT | isActive (0/1) |

### 6.2 `"model_config"` (replaces Room `model_config` table)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | VARINT | modelId (foreign key) |
| 2 | BYTES | loadingParamsJson |
| 3 | BYTES | inferenceParamsJson |

### 6.3 `"personas"` (replaces Room `personas` table)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | BYTES | name |
| 2 | BYTES | systemPrompt |
| 3 | BYTES | description |
| 4 | BYTES | personality |
| 5 | BYTES | scenario |
| 6 | BYTES | exampleMessages |
| 7 | BYTES | alternateGreetingsJson |
| 8 | BYTES | tagsJson |
| 9 | BYTES | avatarUri |
| 10 | BYTES | creatorNotes |
| 11 | BYTES | samplingProfileJson |
| 12 | BYTES | controlVectorsJson |
| 13 | FIXED64 | createdAt |

### 6.4 `"memories"` (replaces Room `ai_memories` table)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | BYTES | fact |
| 2 | BYTES | category |
| 3 | FIXED32 | importance |
| 4 | FIXED64 | createdAt |
| 5 | FIXED64 | lastAccessedAt |
| 6 | VARINT | accessCount |
| 7 | BYTES | sourceContext |
| 8 | BYTES | embeddingJson |
| 9 | VARINT | isSummarized (0/1) |
| 10 | VARINT | summaryGroupId |
| 11 | BYTES | personaId |

### 6.5 `"knowledge_entities"` (replaces Room `knowledge_entities` table)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | BYTES | name |
| 2 | BYTES | type |
| 3 | BYTES | description |
| 4 | BYTES | attributes |
| 5 | FIXED64 | createdAt |
| 6 | FIXED64 | lastUpdatedAt |
| 7 | VARINT | mentionCount |

### 6.6 `"knowledge_relations"` (replaces Room `knowledge_relations` table)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | VARINT | sourceEntityId |
| 2 | VARINT | targetEntityId |
| 3 | BYTES | relationType |
| 4 | FIXED32 | weight |
| 5 | BYTES | context |
| 6 | FIXED64 | createdAt |
| 7 | BYTES | personaId |

### 6.7 `"chats"` (replaces VaultHelper ChatData)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | BYTES | chatId (UUID) |
| 2 | FIXED64 | createdAt |
| 3 | BYTES | title |
| 4 | BYTES | primaryModelId |
| 5 | BYTES | primaryPersonaId |
| 6 | FIXED64 | lastMessageAt |
| 7 | VARINT | messageCount |

**Indexes:** tag 6 (lastMessageAt, WIRE_FIXED64) for sort-by-recent

### 6.8 `"messages"` (replaces VaultHelper messages)

| Tag | WireType | Field |
|-----|----------|-------|
| 1 | BYTES | msgId (UUID) |
| 2 | BYTES | chatId |
| 3 | VARINT | role (0=User, 1=Assistant) |
| 4 | VARINT | contentType (0=None, 1=Text, 2=Image, 3=TextWithImage, 4=PluginResult) |
| 5 | BYTES | content |
| 6 | BYTES | imageData |
| 7 | BYTES | imagePrompt |
| 8 | FIXED64 | imageSeed |
| 9 | FIXED64 | timestamp |
| 10 | BYTES | modelId (NEW - null for legacy messages) |
| 11 | BYTES | personaId (NEW - null for legacy messages) |
| 12 | BYTES | decodingMetricsJson |
| 13 | BYTES | imageMetricsJson |
| 14 | BYTES | memoryMetricsJson |
| 15 | BYTES | ragResultsJson |
| 16 | BYTES | pluginMetricsJson |
| 17 | BYTES | toolChainStepsJson |
| 18 | BYTES | agentPlan |
| 19 | BYTES | agentSummary |
| 20 | BYTES | pluginResultDataJson |

**Indexes:**
- tag 2 (chatId, WIRE_BYTES) — O(log n) lookup by chat
- tag 9 (timestamp, WIRE_FIXED64) — range queries for paging

## 7. Repository Adapter Layer

Each Room DAO / VaultHelper is replaced by a UMS-backed repository:

| New Repository | Replaces | UMS Collection |
|---------------|---------|---------------|
| `UmsModelRepository` | `ModelDao` | `"models"` |
| `UmsConfigRepository` | `ModelConfigDao` | `"model_config"` |
| `UmsPersonaRepository` | `PersonaDao` | `"personas"` |
| `UmsMemoryRepository` | `AiMemoryDao` | `"memories"` |
| `UmsKnowledgeRepository` | `KnowledgeEntityDao` + `KnowledgeRelationDao` | `"knowledge_*"` |
| `UmsChatRepository` | `VaultHelper` | `"chats"` + `"messages"` |

Each repository has:
- `toRecord()` extension: Entity → UmsRecord (wire format)
- `fromRecord()` extension: UmsRecord → Entity
- Same public interface as current DAO/VaultHelper

ViewModels and workers don't change — only the Hilt bindings swap.

## 8. Migration Engine

### Pre-conditions
- Auto-backup (.tnbackup) created before migration starts
- Disk space check: require current data size x 1.5 available
- Security mode already selected (Regular or Protected)
- UMS already initialized (plaintext or encrypted)

### Migration Phases

**Phase 1: Room DB → UMS**
```
For each table (models, model_config, personas, ai_memories, knowledge_entities, knowledge_relations):
  1. Query all rows from Room DAO
  2. Convert each row to UmsRecord via toRecord()
  3. UMS put(collection, record) — WAL-protected
  4. Count verification: UMS count == Room count
```

**Phase 2: VaultHelper → UMS (chats + messages)**
```
For each chat in VaultHelper:
  1. Read ChatData (chatId, createdAt)
  2. Create UMS chat record with:
     - chatId, createdAt
     - primaryModelId = null (unknown for legacy)
     - primaryPersonaId = null
  3. Read all messages for chat (VaultHelper.getMessagesForChat)
  4. For each message:
     - Convert to UmsRecord
     - Add modelId = null (legacy)
     - Add personaId = null (legacy)
     - UMS put("messages", record)
  5. Update chat record with messageCount + lastMessageAt
```

**Phase 3: Verification**
```
For each collection:
  - Compare UMS record count vs source count
  - If mismatch: log warning, mark collection for retry
If all pass: set FLAG_MIGRATION_COMPLETE
If partial: set FLAG_MIGRATION_PARTIAL, retry failed collections next launch
```

**Phase 4: Cleanup (deferred)**
```
After successful verification + 1 successful session:
  - Schedule old Room DB file deletion (7 day grace period)
  - Schedule old MemoryVault file deletion (7 day grace period)
  - Files only deleted after FLAG_MIGRATION_COMPLETE is confirmed
```

### Error Handling
- Crash during migration: old data untouched, WAL rolls back UMS writes, retry on next launch
- VaultHelper decryption failure: skip corrupted message, log it, continue
- Disk full: abort migration gracefully, old data intact

## 9. Onboarding UI (from ToolNeuron2)

Copy these files from ToolNeuron2:

| File | Purpose |
|------|---------|
| `IntroScreen.kt` | Runtime init splash + ActionCelebrationProgress |
| `ShowcaseScreen.kt` | 4-page feature carousel (first launch only) |
| `VaultGateScreen.kt` | Security mode selection + passphrase setup + migration progress |
| `VaultGateViewModel.kt` | State machine: Setup → Deriving → Migrating → Complete |
| `ActionCelebrationProgress` | Particle burst progress bar (from ActionComponents.kt) |

### Navigation Flow

**Fresh install:**
```
IntroScreen → ShowcaseScreen → VaultGateScreen(Setup) → Home
```

**Upgrade (existing user):**
```
IntroScreen → VaultGateScreen(Setup+Migration) → Home
```

**Returning user (Protected mode):**
```
IntroScreen → VaultGateScreen(Unlock) → Home
```

**Returning user (Regular mode):**
```
IntroScreen → Home (skip VaultGate)
```

## 10. Backward Compatibility

### Messages Without modelId
- Legacy messages migrated with `modelId = null` and `personaId = null`
- UI treats null modelId as "unknown model" (no model badge displayed)
- New messages always include modelId + personaId from current active selections

### Serialization
- UMS wire format is tag-based: unknown tags are preserved (forward-compatible)
- Missing tags return null from getters (backward-compatible)
- No schema version needed — presence/absence of tags is the version

### Backup/Restore
- SystemBackupManager updated to backup UMS directory instead of Room DB + VaultHelper
- Old .tnbackup format (v2) still importable via legacy migration path
- New .tnbackup format (v3) uses UMS directory snapshot

### NeuronPacket
- Completely unchanged. RAG .neuron files continue to work as before.
- `InstalledRag` Room entity eliminated — RAG metadata read from NeuronPacket files directly

## 11. Performance Fixes (from audit)

Addressed during migration as applicable:

### Critical
1. **String += in token streaming** → StringBuilder (ChatViewModel)
2. **N+1 queries in getAllChats()** → single UMS getAll("chats") replaces 2N vault queries
3. **O(n) memory dedup** → UMS index on memories makes lookup O(log n)

### High
4. **clear() + addAll() double recomposition** → single state update
5. **Leaked coroutine jobs** → structured cancellation in ViewModels
6. **Repetition detection scanning full string** → sliding window

### Medium (19 issues)
- Eager StateFlows → lazy collection
- Uncached persona queries → UMS index
- Missing image size hints → Coil size() modifier
- Sequential startup checks → parallel with coroutines
- Full table loads for pruning → UMS range query
- Non-batched embedding generation → batch API

## 12. Files to Delete After Migration

```
app/src/main/java/com/dark/tool_neuron/database/AppDatabase.kt
app/src/main/java/com/dark/tool_neuron/database/dao/*.kt (all DAOs)
app/src/main/java/com/dark/tool_neuron/models/converters/Converters.kt
app/src/main/java/com/dark/tool_neuron/vault/VaultHelper.kt
app/src/main/java/com/dark/tool_neuron/vault/VaultExtensions.kt
All Room entity files (Model.kt, ModelConfig.kt, Persona.kt, AiMemory.kt, etc.)
All Room migration classes
MemoryVault library dependency from build.gradle.kts
```

## 13. Implementation Phases

### Phase 1 — Port Modules (no behavior change)
1. Copy system_encryptor → ToolNeuron/system_encryptor/
2. Copy file_ops → ToolNeuron/file_ops/
3. Copy ums → ToolNeuron/ums/
4. Add plaintext mode to UMS C++ layer
5. Wire settings.gradle.kts + app build deps
6. Verify: build compiles, native libs load

### Phase 2 — Onboarding UI (from ToolNeuron2)
1. Copy IntroScreen + ActionCelebrationProgress
2. Copy ShowcaseScreen
3. Copy VaultGateScreen + VaultGateViewModel
4. Integrate security mode selection
5. Wire navigation (Intro → Showcase → VaultGate → Home)

### Phase 3 — UMS Repositories
1. Define tag constants per collection (TagConstants.kt)
2. Build UmsModelRepository + toRecord/fromRecord
3. Build UmsConfigRepository
4. Build UmsPersonaRepository
5. Build UmsMemoryRepository
6. Build UmsKnowledgeRepository
7. Build UmsChatRepository (replaces VaultHelper)

### Phase 4 — Migration Engine
1. Room DB → UMS (models, configs, personas, memories, KG)
2. VaultHelper → UMS (chats + messages, add null modelId)
3. Verification + FLAG_MIGRATION_COMPLETE
4. Auto-backup before migration starts

### Phase 5 — Wire & Cleanup
1. Swap Hilt bindings from Room DAOs → UMS repositories
2. Remove Room DB, DAOs, entities, migrations, converters
3. Remove VaultHelper + MemoryVault dependency
4. Update SystemBackupManager for UMS format
5. Update DataIntegrityManager for UMS
6. Update ViewModels that referenced Room directly

### Phase 6 — Performance Fixes
1. Critical: StringBuilder for token streaming, batch vault queries
2. High: Cancel leaked coroutines, fix double recomposition
3. Medium: Add indexes, cache queries, image size hints
