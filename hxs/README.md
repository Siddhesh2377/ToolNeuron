# HXS (Hex-Storage)

A flexible, encrypted, crash-safe storage engine for Android. Native C++17 with Kotlin JNI bindings.

Designed to be **fully dynamic** — no hardcoded schemas. Collections, fields, and indexes can be added or removed at runtime without migrations or data loss.

---

## Architecture

```
┌─────────────────────────────────────────────┐
│     HexStorage.kt / HxsRecord.kt           │
│          (Kotlin public API)                │
├─────────────────────────────────────────────┤
│              hxs.cpp                        │
│           (JNI bridge)                      │
├──────────┬─────────┬──────────┬─────────────┤
│Collection│  Index   │   WAL   │  Manifest   │
│  Engine  │  System  │(per-coll)│  (vault)   │
├──────────┴─────────┴──────────┴─────────────┤
│          Wire Format (HXSR)                 │
├──────────┬──────────────────────────────────┤
│   LZ4    │      hxs_encryptor (optional)    │
│(compress)│  (AES-GCM / ChaCha20 / PQ)      │
└──────────┴──────────────────────────────────┘
```

---

## Key Design Principles

### 1. Fully Dynamic Collections

Collections are created on first access and dropped on demand. No compile-time schema registration needed.

```kotlin
val hxs = HexStorage()
hxs.openPlaintext("/data/data/.../files/hxs")

// These collections didn't exist before — created automatically
hxs.put("chats", record)
hxs.put("messages", record)
hxs.put("my_new_feature", record)

// Remove a collection entirely
hxs.dropCollection("deprecated_feature")
```

### 2. Schema-Free Records

Records are tagged binary blobs. Any record can have any fields. Different records in the same collection can have different fields. Unknown fields are preserved on roundtrip (forward compatibility).

```kotlin
val record = HxsRecord.build {
    putString(1, "chat-uuid-123")
    putTimestamp(2, System.currentTimeMillis())
    putInt(3, 42)
    putBool(4, true)
    putFloat(5, 3.14f)
    putBytes(6, blobData)
}
```

### 3. Dynamic Indexes

Indexes can be added or removed on any field at any time. No schema changes, no migrations.

```kotlin
// Add an index on tag 1 (string type) for fast lookups
hxs.addIndex("messages", tag = 2, wireType = HexStorage.WIRE_BYTES)

// Later, remove it if no longer needed
hxs.removeIndex("messages", tag = 2)
```

### 4. Per-Collection Schema Versioning

Each collection has its own schema version. Migrations are scoped — changing one collection doesn't affect others.

```kotlin
val version = hxs.getSchemaVersion("chats")
if (version < 2) {
    // migrate chats collection
    hxs.setSchemaVersion("chats", 2)
}
```

---

## Wire Format

### Record (HXSR)

```
Header (16 bytes, little-endian):
  [0-3]    magic           0x48585352 ("HXSR")
  [4-7]    total_size      uint32
  [8-11]   record_id       uint32
  [12-13]  field_count     uint16
  [14]     flags           uint8 (bit0=deleted, bit1=compressed)
  [15]     version         uint8 (currently 1)

Per field:
  [+0-1]   tag             uint16 (1-65535, user-defined)
  [+2]     wire_type       uint8
  [+3..]   data            (size depends on wire type)

Wire types:
  0  VARINT   → [data_len:4][LEB128 zig-zag encoded bytes]
  1  FIXED64  → [8 bytes LE] (timestamp, double)
  2  BYTES    → [data_len:4][byte data] (string, blob)
  3  FIXED32  → [4 bytes LE] (float, small int)
```

Unknown tags are preserved through encode/decode cycles. A newer app version can add fields that older versions will carry through without loss.

### Collection File (collection.hxs)

```
Sequence of blocks:
  [0-3]    block_len       uint32 LE
  [4..]    sealed_data     encrypt(compress(record.encode()))
```

### WAL Entry (collection.wal)

```
  [0-3]    magic           0x48585357 ("HXSW")
  [4-7]    sequence        uint32
  [8]      op              uint8 (1=PUT, 2=DELETE)
  [9]      committed       uint8
  [10-13]  record_id       uint32
  [14-17]  data_len        uint32
  [18..]   data            encoded record (PUT only)
```

### Manifest (manifest.hxs)

```
  [0-3]    magic           0x4858534D ("HXSM")
  [4-5]    format_version  uint16 (=1)
  [6-7]    flags           uint16
  [8-23]   argon2_salt     16 bytes
  [24-35]  argon2 params   t_cost(4), m_cost(4), p_cost(4)
  [36..]   wrapped_dek     double-encrypted DEK
  [..]     key_check       encrypted known plaintext
  [..]     collections     dynamic registry
```

### Index File (collection.idx)

Serialized red-black tree indexes. One file per collection, containing all indexed fields.

```
  [0-3]    index_count     uint32
  Per index:
    [0-3]  chunk_size      uint32
    [4-5]  tag             uint16
    [6]    wire_type       uint8
    [7-10] entry_count     uint32
    Per entry:
      key_data + id_list
```

---

## On-Disk Layout

```
{base_dir}/
├── manifest.hxs          # Vault metadata, collection registry, wrapped DEK
├── chats.hxs             # Collection data (encrypted + compressed records)
├── chats.idx             # Serialized indexes
├── chats.wal             # Write-ahead log
├── messages.hxs
├── messages.idx
├── messages.wal
├── {any_name}.hxs        # Collections are just files — add/remove freely
├── {any_name}.idx
└── {any_name}.wal
```

---

## Encryption

HXS delegates all cryptography to the `hxs_encryptor` module. The storage engine itself has zero crypto code.

- **Encrypted mode**: pass an `HxsEncryptor` instance + app key + user key on open
- **Plaintext mode**: for development/testing, no encryption

In encrypted mode:
- Each record is individually encrypted (AES-256-GCM or ChaCha20-Poly1305)
- DEK is double-wrapped in the manifest (app_key + user_key)
- Argon2id parameters stored in manifest for key re-derivation

---

## Crash Safety

Every mutation follows the WAL protocol:

```
1. Append operation to WAL (fsync)
2. Apply to in-memory collection
3. Mark WAL entry as committed
4. On close/flush: write data to disk, checkpoint WAL
5. On crash: replay uncommitted WAL entries on next open
```

Each collection has its own WAL file — a crash during one collection's write doesn't affect others.

---

## Concurrency

- **Per-collection `shared_mutex`** (readers-writer lock)
  - Multiple concurrent reads
  - Exclusive writes
- **Global mutex** for vault-level operations (open, close, collection create/drop)
- Thread-safe from any Android thread (main, IO, coroutine dispatcher)

---

## Compression

Records larger than 128 bytes are LZ4-compressed before encryption.

```
LZ4 Header (8 bytes):
  [0-3]    magic           0x4C5A3400 ("LZ4\0")
  [4-7]    original_size   uint32 LE
  [8..]    compressed data
```

If compression doesn't reduce size, the original data is stored as-is.

---

## Kotlin API

```kotlin
val hxs = HexStorage()

// ── Open vault ──
hxs.openPlaintext(context.filesDir.resolve("hxs").absolutePath)
// or
hxs.openEncrypted(path, appKey, userKey, encryptor)

// ── Build a record ──
val chat = HxsRecord.build {
    putString(1, UUID.randomUUID().toString())  // chat_id
    putTimestamp(2, System.currentTimeMillis())  // created_at
    putString(3, "My Chat Title")               // title
    putInt(4, 0)                                // message_count
}

// ── CRUD ──
val id = hxs.put("chats", chat)
val fetched = hxs.get("chats", id)
chat.putInt(4, 5)  // update message count
hxs.update("chats", chat)
hxs.delete("chats", id)

// ── Queries ──
hxs.addIndex("chats", tag = 1, wireType = HexStorage.WIRE_BYTES)
val results = hxs.queryString("chats", tag = 1, value = "some-uuid")
val recent = hxs.queryRange("chats", tag = 2, minVal = yesterday, maxVal = now)

// ── Collection management ──
val collections = hxs.listCollections()
hxs.dropCollection("old_feature")

// ── Flush & close ──
hxs.flushAll()
hxs.close()
```

---

## Schema Management

HXS is schema-free at the storage level — any record can have any fields. But the **app layer** needs to know what tag numbers mean. This is managed entirely in Kotlin code: no JSON files, no asset files, no runtime parsing.

### Structure

```
app/src/main/java/com/dark/tool_neuron/data/schema/
├── ChatSchema.kt
├── MessageSchema.kt
├── PersonaSchema.kt
├── ModelSchema.kt
├── MemorySchema.kt
└── ... (one file per collection)
```

### Defining a Schema

Each collection gets an `object` with tag constants:

```kotlin
object ChatSchema {
    const val COLLECTION = "chats"

    // Tag numbers — append-only, NEVER reuse a deleted tag
    const val CHAT_ID       = 1   // BYTES (string UUID)
    const val CREATED_AT    = 2   // FIXED64 (timestamp ms)
    const val TITLE         = 3   // BYTES (string)
    const val MESSAGE_COUNT = 4   // VARINT (int)
    const val PERSONA_ID    = 5   // BYTES (string UUID)
    const val LAST_MSG_AT   = 6   // FIXED64 (timestamp ms)
    // Tag 7+ reserved for future fields

    const val SCHEMA_VERSION = 1
}
```

### Typed Wrappers (Optional)

For frequently-used collections, wrap `HxsRecord` in a typed class:

```kotlin
class Chat(val record: HxsRecord) {
    val chatId: String get() = record.getString(ChatSchema.CHAT_ID)
    val createdAt: Long get() = record.getTimestamp(ChatSchema.CREATED_AT)
    val title: String get() = record.getString(ChatSchema.TITLE)
    val messageCount: Long get() = record.getInt(ChatSchema.MESSAGE_COUNT)
    val personaId: String get() = record.getString(ChatSchema.PERSONA_ID)
    val lastMessageAt: Long get() = record.getTimestamp(ChatSchema.LAST_MSG_AT)

    companion object {
        fun create(chatId: String, title: String, personaId: String? = null) =
            Chat(HxsRecord.build {
                putString(ChatSchema.CHAT_ID, chatId)
                putTimestamp(ChatSchema.CREATED_AT, System.currentTimeMillis())
                putString(ChatSchema.TITLE, title)
                putInt(ChatSchema.MESSAGE_COUNT, 0)
                personaId?.let { putString(ChatSchema.PERSONA_ID, it) }
                putTimestamp(ChatSchema.LAST_MSG_AT, System.currentTimeMillis())
            })
    }
}
```

Usage:

```kotlin
// Create
val chat = Chat.create("uuid-123", "My Chat")
hxs.put(ChatSchema.COLLECTION, chat.record)

// Query
val chats = hxs.queryString(ChatSchema.COLLECTION, ChatSchema.CHAT_ID, "uuid-123")
    .map { Chat(it) }

println(chats.first().title) // "My Chat"
```

### The Rules

1. **Tag numbers are append-only.** Never reuse a tag number after deleting a field. Old records with that tag still exist on disk — reusing it with a different type will corrupt reads.

2. **Comment the wire type.** Tags are just integers — the comment is the only place documenting what type each tag stores.

    ```kotlin
    const val TITLE = 3   // BYTES (string)  ← this comment matters
    ```

3. **One `SCHEMA_VERSION` per collection.** Migrations are scoped to individual collections. Bumping one doesn't affect others.

    ```kotlin
    // In your repository init
    if (hxs.getSchemaVersion(ChatSchema.COLLECTION) < 2) {
        // migrate: add persona_id to existing chats
        for (chat in hxs.getAll(ChatSchema.COLLECTION)) {
            if (!chat.hasField(ChatSchema.PERSONA_ID)) {
                chat.putString(ChatSchema.PERSONA_ID, "default")
                hxs.update(ChatSchema.COLLECTION, chat)
            }
        }
        hxs.setSchemaVersion(ChatSchema.COLLECTION, 2)
    }
    ```

4. **Typed wrappers are optional.** For quick prototyping or small collections, use `HxsRecord` directly. Add a typed wrapper when a collection stabilizes and you want IDE autocomplete + type safety.

5. **No central registry needed.** Each schema object is self-contained. The storage engine doesn't know or care about schemas — it just stores tagged bytes. The schema layer is purely an app-level convention.

### Adding a New Field

```kotlin
// 1. Add the constant to the schema object
object ChatSchema {
    // ... existing tags ...
    const val PINNED = 7   // VARINT (bool)  ← new field

    const val SCHEMA_VERSION = 2  // ← bump
}

// 2. Add getter to the typed wrapper
class Chat(val record: HxsRecord) {
    // ... existing getters ...
    val isPinned: Boolean get() = record.getBool(ChatSchema.PINNED)
}

// 3. Handle migration (old records won't have this field)
// Option A: default value in getter (zero-cost, no migration)
val isPinned: Boolean get() = record.getBool(ChatSchema.PINNED, default = false)

// Option B: backfill migration (if you need the field indexed)
if (hxs.getSchemaVersion(ChatSchema.COLLECTION) < 2) {
    for (chat in hxs.getAll(ChatSchema.COLLECTION)) {
        chat.putBool(ChatSchema.PINNED, false)
        hxs.update(ChatSchema.COLLECTION, chat)
    }
    hxs.addIndex(ChatSchema.COLLECTION, ChatSchema.PINNED, HexStorage.WIRE_VARINT)
    hxs.setSchemaVersion(ChatSchema.COLLECTION, 2)
}
```

### Removing a Field

```kotlin
// 1. Delete the constant from the schema object (or mark deprecated)
// const val OLD_FIELD = 7  // REMOVED in v3 — do NOT reuse tag 7

// 2. Remove the getter from the typed wrapper

// 3. No migration needed — old records still decode fine,
//    the unused field is silently ignored.
//    It will be cleaned up naturally as records are updated.
```

### Why Not JSON / Assets?

| Approach | Type-safe | IDE support | Runtime cost | Hot reload |
|---|---|---|---|---|
| **Kotlin objects** | Compile-time | Full autocomplete | Zero | No (recompile) |
| JSON in assets | No | No | Parse on startup | Yes |
| Room-style annotations | Compile-time | Partial | Code generation | No |
| Protobuf `.proto` files | Compile-time | With plugin | Code generation | No |

Kotlin objects win: zero overhead, full IDE support, no build plugins, no codegen. The tradeoff — you must recompile to change a schema — is irrelevant because schema changes require code changes anyway (new getters, migration logic, UI updates).

---

## Build

### Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| LZ4 | 1.10.0 | Record compression (fetched via CMake) |
| hxs_encryptor | local module | Encryption (runtime dependency via Gradle) |

### Configuration

- **C++ standard**: C++17
- **NDK ABIs**: arm64-v8a, x86_64
- **Min SDK**: 29 (Android 10)
- **Symbol visibility**: hidden
- **Optimizations**: LTO, section GC, ICF
- **Output**: `libhxs.so`

### Gradle

```kotlin
dependencies {
    implementation(project(":hxs"))
    implementation(project(":hxs_encryptor"))
}
```

---

## File Structure

```
hxs/
├── build.gradle.kts
├── consumer-rules.pro
├── src/main/
│   ├── AndroidManifest.xml
│   ├── cpp/
│   │   ├── CMakeLists.txt       # Build config, LZ4 fetch
│   │   ├── wire_format.h/cpp    # Tagged binary record format (HXSR)
│   │   ├── index.h/cpp          # Red-black tree indexes (serialized)
│   │   ├── wal.h/cpp            # Per-collection write-ahead log
│   │   ├── collection.h/cpp     # Collection engine (mmap, RWLock, LZ4, crypto)
│   │   ├── manifest.h/cpp       # Vault metadata, collection registry, DEK
│   │   └── hxs.cpp              # JNI bridge
│   └── java/com/dark/hxs/
│       └── HexStorage.kt        # Kotlin API + HxsRecord builder
```

---

## Flexibility Guarantees

| Scenario | Supported |
|---|---|
| Add a new collection at runtime | Yes — auto-created on first `put()` or `ensureCollection()` |
| Remove a collection at runtime | Yes — `dropCollection()` deletes all files |
| Add a new field to existing records | Yes — just use a new tag number |
| Remove a field from records | Yes — `removeField()` or just stop writing it |
| Add an index on any field | Yes — `addIndex()` rebuilds from existing data |
| Remove an index | Yes — `removeIndex()` |
| Read records from a newer app version | Yes — unknown fields preserved |
| Read records from an older app version | Yes — missing fields return defaults |
| Per-collection migrations | Yes — `getSchemaVersion()` / `setSchemaVersion()` |
| Switch from plaintext to encrypted | Create new vault, copy data |
| Mix encrypted and plaintext collections | No — vault-level setting |
