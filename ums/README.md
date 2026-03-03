# UMS — Unified Memory System

Custom binary storage engine for ToolNeuron2. All app data (chats, messages, personas, memories, settings) lives in UMS. Built in C++ with JNI bridge. Kotlin layer is a thin wrapper.

## Architecture

```
┌──────────────────────────────────────────────────┐
│  Kotlin Layer                                    │
│  UnifiedMemorySystem.kt — thin public API        │
│  UmsCollection.kt — per-collection operations    │
└─────────────────────┬────────────────────────────┘
                      │ JNI
┌─────────────────────▼────────────────────────────┐
│  C++ Native Engine                               │
│                                                   │
│  ┌─────────────┐ ┌─────────────┐ ┌────────────┐ │
│  │ Collection   │ │ Wire Format │ │ Index      │ │
│  │ Manager      │ │ (tagged bin)│ │ Engine     │ │
│  └──────┬───────┘ └──────┬──────┘ └─────┬──────┘ │
│         └────────┬───────┘──────────────┘        │
│         ┌────────▼───────────────────────────┐   │
│         │  Storage Layer (WAL → Block I/O)   │   │
│         └────────┬───────────────────────────┘   │
│                  │                                │
│  ┌───────────────▼─────────────┐                 │
│  │   2-Stage Encryption        │                 │
│  │   uses: system_encryptor    │                 │
│  └───────────────┬─────────────┘                 │
│                  │                                │
│  ┌───────────────▼─────────────┐                 │
│  │   Disk I/O                  │                 │
│  │   uses: file_ops            │                 │
│  └─────────────────────────────┘                 │
└──────────────────────────────────────────────────┘
```

## On-Disk Layout

```
/data/data/com.dark.tool_neuron/files/ums/
├── manifest.ums        Schema version, collection registry, wrapped DEK, key check
├── chats.ums           Chat records (tagged binary, encrypted)
├── messages.ums        Message records (tagged binary, encrypted)
├── personas.ums        Persona records (tagged binary, encrypted)
├── memories.ums        AI memory records (tagged binary, encrypted)
├── settings.ums        App settings (tagged binary, encrypted)
├── wal.ums             Write-ahead log (crash recovery)
└── index/
    ├── chats.idx       B-tree index on chat_id
    └── messages.idx    B-tree index on message_id + chat_id
```

Each collection is a separate file. WAL ensures crash safety.

## Wire Format (Tagged Binary)

Protobuf-inspired. Each record is a sequence of tagged fields:

```
Record Header (16 bytes fixed):
  [4] magic:        0x554D5352 ("UMSR")
  [4] record_size:  total bytes excluding header
  [4] record_id:    unique within collection
  [2] field_count:  number of fields
  [1] flags:        bit 0 = deleted, bit 1 = compressed
  [1] reserved

Field (repeated):
  [2] field_tag:    1-65535, identifies the field
  [1] wire_type:    0 = varint, 1 = fixed64, 2 = bytes, 3 = fixed32
  [4] data_length:  (wire_type 2 only, otherwise implicit)
  [N] data

Record Footer:
  [16] AES-GCM authentication tag (per-record integrity)
```

### Wire Types

| Type | ID | Size | Use |
|---|---|---|---|
| varint | 0 | 1-10 bytes | int32, int64, bool, enum |
| fixed64 | 1 | 8 bytes | double, timestamp (millis) |
| bytes | 2 | length-prefixed | string (UTF-8), blob, nested record |
| fixed32 | 3 | 4 bytes | float |

### Forward/Backward Compatibility

- **Unknown tags** are read into an `unknown_fields` buffer and preserved on write-back. Old app version reading data written by new version is safe.
- **Missing tags** return type-appropriate defaults (0, empty, false). New app version reading old data is safe.
- **No migration code ever needed** for adding new fields.

## Two-Stage Encryption

### Key Hierarchy

```
Stage 1 — App-Bound (anti-clone)
  AppKey = HKDF-SHA256(
    ikm  = SHA256(app signing certificate),
    salt = HARDCODED_SALT_32_BYTES,
    info = "com.dark.tool_neuron"
  )

Stage 2 — User-Bound (passphrase)
  UserKey = PBKDF2-SHA256(
    password   = user passphrase,
    salt       = random 16 bytes (stored in manifest),
    iterations = 600,000
  )

Data Encryption Key (DEK)
  DEK = random 32 bytes (generated once on vault creation)
  Stored as: AES-GCM(AppKey, AES-GCM(UserKey, DEK))
  Double-wrapped: AppKey outer, UserKey inner
```

### Unlock Flow

1. Native code derives AppKey from signing cert + package name + salt
2. Unwrap outer layer of DEK blob with AppKey
3. User enters passphrase, derive UserKey via PBKDF2
4. Unwrap inner layer with UserKey, recover DEK
5. DEK used for all record-level AES-256-GCM encryption

### Password Change

Re-wrap DEK with new UserKey only. Zero data re-encryption.

### Cross-Device Backup

Backup files are bound to the signing certificate. Play Store installs share one cert, GitHub releases share another. A backup from Play Store restores only on Play Store installs, and vice versa. User passphrase required in both cases.

## manifest.ums Format

```
[4]  magic: 0x554D534D ("UMSM")
[2]  format_version: 1
[2]  flags: bit 0 = migration_complete
[16] pbkdf2_salt
[4]  pbkdf2_iterations (600000)
[N]  wrapped_dek_blob (double-encrypted DEK)
[32] key_check_block (encrypted known plaintext for verification)
[2]  collection_count
  Per collection:
    [2]  name_length
    [N]  name (UTF-8)
    [2]  filename_length
    [N]  filename (UTF-8)
    [4]  record_count
    [8]  last_modified (timestamp)
```

## Kotlin API (thin wrapper)

```kotlin
object UnifiedMemorySystem {
    fun open(context: Context, passphrase: String): UmsVault
    fun create(context: Context, passphrase: String): UmsVault
    fun exists(context: Context): Boolean
}

class UmsVault {
    fun collection(name: String): UmsCollection
    fun listCollections(): List<String>
    fun backup(outputPath: String)
    fun close()
}

class UmsCollection {
    fun put(record: UmsRecord): Int          // returns record_id
    fun get(recordId: Int): UmsRecord?
    fun delete(recordId: Int)
    fun query(filter: UmsFilter): List<UmsRecord>
    fun count(): Int
    fun forEach(block: (UmsRecord) -> Unit)
}

class UmsRecord {
    fun putInt(tag: Int, value: Int)
    fun putLong(tag: Int, value: Long)
    fun putString(tag: Int, value: String)
    fun putBytes(tag: Int, value: ByteArray)
    fun putFloat(tag: Int, value: Float)
    fun putDouble(tag: Int, value: Double)
    fun putBool(tag: Int, value: Boolean)

    fun getInt(tag: Int, default: Int = 0): Int
    fun getLong(tag: Int, default: Long = 0): Long
    fun getString(tag: Int, default: String = ""): String
    fun getBytes(tag: Int): ByteArray?
    fun getFloat(tag: Int, default: Float = 0f): Float
    fun getDouble(tag: Int, default: Double = 0.0): Double
    fun getBool(tag: Int, default: Boolean = false): Boolean
}
```

## WAL (Write-Ahead Log)

All writes go to `wal.ums` first:

```
1. Append operation to WAL (collection, record_id, data)
2. fsync WAL
3. Apply operation to collection file
4. fsync collection file
5. Mark WAL entry as committed
```

On crash recovery (next open): replay uncommitted WAL entries. Guarantees no data loss.

## Collections — Initial Schema

### chats (chats.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | chat_id (UUID string) |
| 2 | fixed64 | created_at (timestamp ms) |
| 3 | bytes | title |
| 4 | varint | message_count |
| 5 | bytes | persona_id |
| 6 | fixed64 | last_message_at |

### messages (messages.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | message_id (UUID string) |
| 2 | bytes | chat_id (foreign key) |
| 3 | varint | role (0=user, 1=assistant, 2=system) |
| 4 | bytes | content (text) |
| 5 | fixed64 | timestamp |
| 6 | varint | content_type (0=text, 1=image, 2=plugin_result) |
| 7 | bytes | metadata_json (plugin data, image params, etc.) |
| 8 | bytes | image_data (blob, if content_type=image) |

### personas (personas.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | persona_id |
| 2 | bytes | name |
| 3 | bytes | avatar (emoji or URI) |
| 4 | bytes | system_prompt |
| 5 | bytes | greeting |
| 6 | varint | is_default (bool) |
| 7 | fixed64 | created_at |
| 8 | bytes | description |
| 9 | bytes | personality |
| 10 | bytes | scenario |
| 11 | bytes | example_messages |
| 12 | bytes | alternate_greetings_json |
| 13 | bytes | tags_json |
| 14 | bytes | avatar_uri |
| 15 | bytes | creator_notes |
| 16 | bytes | sampling_profile_json |
| 17 | bytes | control_vectors_json |

### memories (memories.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | memory_id |
| 2 | bytes | fact |
| 3 | varint | category (0=personal, 1=preference, 2=work, 3=interest, 4=general) |
| 4 | bytes | source_chat_id |
| 5 | fixed64 | created_at |
| 6 | fixed64 | updated_at |
| 7 | fixed64 | last_accessed_at |
| 8 | varint | access_count |
| 9 | bytes | embedding (float array as blob) |
| 10 | varint | is_summarized (bool) |
| 11 | bytes | summary_group_id |
| 12 | bytes | persona_id |

### settings (settings.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | key (setting name) |
| 2 | bytes | value (serialized) |
| 3 | varint | type (0=bool, 1=int, 2=string, 3=long) |

### models (models.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | id |
| 2 | bytes | model_name |
| 3 | bytes | model_path |
| 4 | bytes | path_type |
| 5 | bytes | provider_type |
| 6 | varint | file_size |
| 7 | varint | is_active |

### model_config (model_config.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | id |
| 2 | bytes | model_id |
| 3 | bytes | loading_params_json |
| 4 | bytes | inference_params_json |

### rags (rags.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | id |
| 2 | bytes | name |
| 3 | bytes | description |
| 4 | bytes | source_type |
| 5 | bytes | file_path |
| 6 | varint | node_count |
| 7 | varint | embedding_dimension |
| 8 | bytes | embedding_model |
| 9 | bytes | domain |
| 10 | bytes | language |
| 11 | bytes | version |
| 12 | bytes | tags_csv |
| 13 | bytes | status |
| 14 | varint | is_enabled |
| 15 | fixed64 | created_at |
| 16 | fixed64 | updated_at |
| 17 | fixed64 | last_loaded_at |
| 18 | varint | size_bytes |
| 19 | bytes | metadata_json |
| 20 | varint | is_encrypted |
| 21 | varint | loading_mode |
| 22 | varint | has_admin_access |

### knowledge_entities (knowledge_entities.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | id |
| 2 | bytes | name |
| 3 | bytes | type |
| 4 | bytes | embedding |
| 5 | fixed64 | first_seen |
| 6 | fixed64 | last_seen |
| 7 | varint | mention_count |

### knowledge_relations (knowledge_relations.ums)

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | id |
| 2 | bytes | subject_id |
| 3 | bytes | predicate |
| 4 | bytes | object_id |
| 5 | fixed32 | confidence |
| 6 | bytes | source_fact_id |
| 7 | fixed64 | created_at |
| 8 | bytes | persona_id |

### vault_messages / vault_files / vault_embeddings / vault_custom_data

All share the same schema:

| Tag | Type | Field |
|---|---|---|
| 1 | bytes | block_id |
| 2 | bytes | content/data |
| 3 | bytes | category |
| 4 | bytes | tags_csv |
| 5 | fixed64 | timestamp |
| 6 | bytes | content_hash |
| 7 | bytes | searchable_text |

## Migration (ToolNeuron v1 to UMS)

Handled by an explicit migration screen on first launch:

1. Detect old Room DB (`llm_models_database`) and MemoryVault (`memory_vault/`)
2. Show migration UI with progress — user sets new passphrase
3. Derive keys, create UMS vault
4. Read old Room DB via SQLite (Kotlin side), encode as tagged binary, write to UMS
5. Read old MemoryVault (decrypt with Android KeyStore), encode, write to UMS
6. Build indexes, verify integrity
7. Keep old data for 7 days as safety net, then auto-delete

## Dependencies

- **system_encryptor** — AES-256-GCM, HKDF-SHA256, PBKDF2, secure wipe, random bytes
- **file_ops** — all disk I/O (atomic writes, path validation, permissions)
- **BoringSSL** — via system_encryptor (not linked directly)

## Module Dependency Graph

```
file_ops <── ums ──> system_encryptor
                │
                └──> app (Kotlin thin wrapper + UI)
```
