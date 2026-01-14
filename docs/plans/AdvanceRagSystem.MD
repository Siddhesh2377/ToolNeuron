# RAG Neuron Packet System: Technical Specification

## Overview

This document specifies the implementation of a multi-user, permission-based RAG knowledge packet system that extends the existing MemoryVault architecture. The system allows encrypted RAG data to be exported, shared, and imported with role-based access control.

---

## Core Design Principles

1. **Privacy First**: UUID-based recovery keys, no UX compromises for security
2. **Offline Only**: No online records, no telemetry, no user tracking
3. **Simplified Permissions**: Two roles only - READ and READ-WRITE-ADMIN
4. **Ephemeral Access**: Passwords cached in memory until RAG unload/deletion/app close
5. **Flexible Loading**: Embedded mode (password once) or transient mode (password per load)

---

## Permission Model

### Role Definitions

```
READ (r)
- Query the RAG system
- View retrieved chunks
- See source citations
- Cannot modify data
- Cannot change permissions

READ-WRITE-ADMIN (r-w-a)
- All READ permissions
- Add new documents/chunks
- Modify existing chunks
- Delete chunks
- Add/remove users
- Change user permissions
- Change passwords (including own)
- Regenerate recovery key (new key, invalidates old)
```

### No Write-Only Permission

Write-only permission is removed from the design. All users have either READ or READ-WRITE-ADMIN.

---

## Recovery Key System

### Key Generation

```
Recovery Key Format: UUID v4
Example: 8f3a7b2c-9d4e-4f1a-b5c6-7e8f9a0b1c2d

Generation:
- Created once at packet creation time
- Cryptographically random UUID
- Stored as SHA-256 hash in packet header
- Original key given to packet creator only
```

### Recovery Key Purpose

```
Scope: ADMIN PASSWORD RESET ONLY

Recovery key can:
- Reset the admin user's password
- Does NOT reset READ-only user passwords
- Does NOT grant READ-only users admin access

Process:
1. User provides recovery key
2. System validates SHA-256(key) against stored hash
3. System prompts for new admin password
4. System re-encrypts admin DEK with new password
5. Recovery key remains valid (not rotated)
```

### Admin User Management

```
Admin responsibilities:
- Create READ-only user accounts
- Set/reset READ-only user passwords
- Remove READ-only users
- Modify their own admin password
- Manage packet permissions

Admin cannot:
- Recover their own password without recovery key
- Access other users' encrypted credentials
```

---

## Neuron Packet Structure

### File Format (.neuron)

```
NEURON PACKET FILE STRUCTURE
============================

Offset   | Size      | Field
---------|-----------|------------------------------------------
0        | 4 bytes   | Magic: "NRON"
4        | 2 bytes   | Version: 1
6        | 16 bytes  | Packet ID (UUID)
22       | 8 bytes   | Created timestamp (Unix milliseconds)
30       | 32 bytes  | Recovery Key Hash (SHA-256)
62       | 1 byte    | Loading Mode (0=transient, 1=embedded)
63       | 1 byte    | User Count (N)
64       | 2 bytes   | Max Users (default: 20)
66       | 446 bytes | Packet Metadata (JSON, zero-padded)
---------|-----------|------------------------------------------
512      | Variable  | Access Control List (ACL)
         |           | - N user entries, each 256 bytes
---------|-----------|------------------------------------------
512+256N | Variable  | Encrypted Payload
         |           | - IV (12 bytes)
         |           | - Ciphertext
         |           | - Auth Tag (16 bytes)
```

### Loading Mode Flag

```
0x00: TRANSIENT MODE
- Password required every time packet is loaded
- Password never persisted
- Use case: Highly sensitive data, shared devices

0x01: EMBEDDED MODE
- Password required once on first load
- Password cached in memory until:
  * RAG packet unloaded
  * RAG packet deleted
  * Application closed
  * Device reboot
- Cache cleared on any of above events
- Use case: Personal device, frequent access
```

### Packet Metadata Structure

```json
{
  "packetId": "uuid",
  "name": "Emergency Medicine Protocols",
  "description": "ER triage and treatment guidelines",
  "domain": "medical",
  "language": "en",
  "version": "2024.1",
  "tags": ["emergency", "medicine", "trauma"],
  "stats": {
    "documentCount": 150,
    "chunkCount": 5420,
    "embeddingCount": 5420,
    "totalBytes": 47185920,
    "compressedBytes": 12582912
  },
  "embeddingModel": "all-MiniLM-L6-v2-q8",
  "createdBy": null,
  "loadingMode": "embedded"
}
```

---

## Access Control List (ACL)

### ACL Entry Structure

```
ACL ENTRY (256 bytes per user)
==============================

Offset | Size      | Field
-------|-----------|------------------------------------------
0      | 1 byte    | Slot ID (0-255)
1      | 1 byte    | Permissions (bitfield)
       |           | - Bit 0: READ
       |           | - Bit 1: WRITE
       |           | - Bit 2: ADMIN
       |           | - 0x01 = READ
       |           | - 0x07 = READ+WRITE+ADMIN
2      | 64 bytes  | Password Hash (bcrypt, cost=12)
66     | 16 bytes  | Key Derivation Salt
82     | 32 bytes  | Encrypted DEK (Data Encryption Key)
114    | 8 bytes   | Created timestamp
122    | 8 bytes   | Last modified timestamp
130    | 64 bytes  | User Label (UTF-8, zero-padded)
194    | 62 bytes  | Reserved (zero-filled)
```

### Permission Bitfield

```
Value | Binary | Permissions
------|--------|------------------
0x01  | 001    | READ
0x07  | 111    | READ-WRITE-ADMIN
```

Only two valid values. Any other value is invalid.

### User Label

```
Optional human-readable label for user slot.
Examples:
- "Nursing Staff"
- "Attending Physicians"
- "Hospital Administrator"
- "Department Head"

Max 64 bytes UTF-8.
Used for UI display only, not for access control.
```

---

## Encryption Architecture

### Multi-Layer Key Hierarchy

```
┌──────────────────────────────────────────────────────┐
│                 Recovery Key (UUID)                   │
│         (User stores securely, not in packet)         │
└────────────────────┬─────────────────────────────────┘
                     │
                     │ (Only for admin password reset)
                     │
┌────────────────────▼─────────────────────────────────┐
│            Admin Password (User 0)                    │
│                                                       │
│  Derivation: PBKDF2-HMAC-SHA256                      │
│  Iterations: 100,000                                  │
│  Salt: 16 bytes (random, stored in ACL)              │
│  Output: 256-bit key                                  │
└────────────────────┬─────────────────────────────────┘
                     │
                     │ Encrypts
                     │
┌────────────────────▼─────────────────────────────────┐
│              Master DEK (32 bytes)                    │
│        (Same for all users, encrypted per-user)       │
│                                                       │
│  Algorithm: AES-256-GCM                               │
│  Stored: Encrypted in each ACL entry                  │
└────────────────────┬─────────────────────────────────┘
                     │
                     │ Encrypts
                     │
┌────────────────────▼─────────────────────────────────┐
│           Encrypted Payload Sections                  │
│                                                       │
│  Section 1: INDEX BLOCK                              │
│  - Document metadata                                  │
│  - Chunk index                                        │
│  - Embedding index (HNSW)                            │
│                                                       │
│  Section 2: CONTENT BLOCKS                           │
│  - Document chunks (LZ4 compressed)                  │
│  - Full-text search index                            │
│                                                       │
│  Section 3: EMBEDDING BLOCKS                         │
│  - Embedding vectors (float32 or int8)               │
│                                                       │
│  Section 4: METADATA BLOCKS                          │
│  - Medical codes (ICD-10, SNOMED)                    │
│  - Military classifications                          │
│  - Custom tags/categories                            │
└──────────────────────────────────────────────────────┘
```

### Key Derivation

```
Password → PBKDF2-HMAC-SHA256(password, salt, 100000 iterations) → 256-bit Key

This key is used to:
1. Encrypt/decrypt the Master DEK in the user's ACL entry
2. The Master DEK then encrypts/decrypts the actual payload
```

### Encryption Algorithm

```
Algorithm: AES-256-GCM

Per-block encryption:
- Random 12-byte IV per encrypted section
- 16-byte authentication tag
- Associated data: Section type + offset

Benefits:
- Authenticated encryption (detects tampering)
- Fast on modern hardware
- Matches existing MemoryVault encryption
```

---

## Password Management

### Password Caching Strategy

```
Cache Location: In-memory only (never persisted)

Cache Structure:
Map<PacketID, CachedPassword>

CachedPassword:
- passwordHash: SHA-256 (for quick validation)
- derivedKey: 256-bit key (for DEK decryption)
- userSlot: Integer (0-N)
- permissions: Bitfield (0x01 or 0x07)
- cachedAt: Timestamp

Cache Invalidation:
- On packet unload
- On packet deletion
- On application close
- On device reboot
- Manual clear by user
- Timeout: Never (until invalidation event)
```

### Transient Mode Behavior

```
Mode: 0x00 (Transient)

Every packet load:
1. Check if password in cache
2. If not in cache:
   - Prompt user for password
   - Validate against ACL
   - Derive key
   - Decrypt DEK
   - Cache password
3. If in cache:
   - Use cached key directly
   - No prompt
4. On packet unload:
   - Clear cache entry
```

### Embedded Mode Behavior

```
Mode: 0x01 (Embedded)

First load:
1. Prompt user for password
2. Validate against ACL
3. Derive key
4. Decrypt DEK
5. Cache password (persists until app close)

Subsequent loads:
1. Check cache
2. Use cached key
3. No password prompt

Cache persists across:
- Multiple queries
- App backgrounding
- Screen lock

Cache cleared on:
- App termination
- Packet unload
- Packet deletion
- Manual clear
```

---

## MemoryVault Integration

### New Block Types

```kotlin
enum class BlockType {
    MESSAGE,
    FILE,
    CUSTOM_DATA,
    EMBEDDING,
    DOCUMENT_CHUNK,      // Existing
    METADATA_LINK,       // Existing
    NEURON_PACKET_META,  // NEW: Metadata for loaded neuron packets
    NEURON_ACL_ENTRY     // NEW: User access control entries
}
```

### New API Methods

```kotlin
// Export API
suspend fun MemoryVault.exportNeuronPacket(
    metadata: NeuronPacketMetadata,
    config: ExportConfig,
    outputFile: File,
    progressCallback: (Float) -> Unit = {}
): ExportResult

data class ExportConfig(
    val adminPassword: String,
    val readOnlyUsers: List<ReadOnlyUser> = emptyList(),
    val loadingMode: LoadingMode = LoadingMode.EMBEDDED,
    val includeEmbeddings: Boolean = true,
    val compressionLevel: Int = 4,
    val quantizeEmbeddings: Boolean = false,
    val filterByTags: Set<String>? = null,
    val filterByCategory: String? = null,
    val filterByDateRange: LongRange? = null
)

data class ReadOnlyUser(
    val password: String,
    val label: String
)

enum class LoadingMode {
    TRANSIENT,  // 0x00
    EMBEDDED    // 0x01
}

data class ExportResult(
    val packetFile: File,
    val recoveryKey: String,  // UUID, give to user
    val metadata: NeuronPacketMetadata,
    val stats: PacketStats,
    val success: Boolean,
    val error: String? = null
)

// Import API
suspend fun MemoryVault.importNeuronPacket(
    packetFile: File,
    password: String,
    config: ImportConfig = ImportConfig(),
    progressCallback: (Float) -> Unit = {}
): ImportResult

data class ImportConfig(
    val mergeStrategy: MergeStrategy = MergeStrategy.SKIP_DUPLICATES,
    val rebuildEmbeddings: Boolean = false,
    val validateIntegrity: Boolean = true,
    val namespacePrefix: String? = null
)

enum class MergeStrategy {
    SKIP_DUPLICATES,
    OVERWRITE,
    KEEP_BOTH,
    MERGE_TAGS
}

data class ImportResult(
    val success: Boolean,
    val packetId: String,
    val userPermissions: Int,  // 0x01 or 0x07
    val metadata: NeuronPacketMetadata,
    val stats: ImportStats,
    val error: String? = null
)

data class ImportStats(
    val documentsImported: Int,
    val chunksImported: Int,
    val embeddingsImported: Int,
    val duplicatesSkipped: Int
)

// Query API (Permission-Aware)
suspend fun MemoryVault.queryNeuronPacket(
    packetId: String,
    query: String,
    config: QueryConfig = QueryConfig()
): QueryResult

data class QueryConfig(
    val maxResults: Int = 10,
    val relevanceThreshold: Float = 0.7f,
    val includeEmbeddings: Boolean = true
)

data class QueryResult(
    val answer: String,
    val sources: List<ContextChunk>,
    val confidence: Float,
    val permissionLevel: Int,  // 0x01 or 0x07
    val retrievalTimeMs: Long,
    val generationTimeMs: Long
)

// Admin API (Only for r-w-a users)
suspend fun MemoryVault.addPacketUser(
    packetId: String,
    newUser: ReadOnlyUser,
    adminPassword: String
): Result<Unit>

suspend fun MemoryVault.removePacketUser(
    packetId: String,
    userSlot: Int,
    adminPassword: String
): Result<Unit>

suspend fun MemoryVault.changePacketPassword(
    packetId: String,
    userSlot: Int,
    oldPassword: String,
    newPassword: String
): Result<Unit>

suspend fun MemoryVault.resetAdminPassword(
    packetId: String,
    recoveryKey: String,
    newPassword: String
): Result<Unit>

// Cache Management
suspend fun MemoryVault.clearPasswordCache(packetId: String)
suspend fun MemoryVault.clearAllPasswordCaches()
suspend fun MemoryVault.isPacketCached(packetId: String): Boolean
```

---

## Export Workflow

### Step 1: Prepare Data

```
1. Filter documents based on ExportConfig
   - By tags
   - By category
   - By date range

2. Gather blocks:
   - Document chunks
   - Embeddings (if included)
   - Document metadata
   - Full-text index

3. Validate data integrity:
   - Check all embeddings have corresponding chunks
   - Verify no orphaned references
```

### Step 2: Build Packet Structure

```
1. Generate Packet ID (UUID)
2. Generate Recovery Key (UUID)
3. Hash recovery key (SHA-256)
4. Build metadata JSON
5. Set loading mode (transient or embedded)
```

### Step 3: Create ACL

```
For Admin User (Slot 0):
1. Generate salt (16 bytes random)
2. Derive key: PBKDF2(adminPassword, salt, 100000)
3. Generate Master DEK (32 bytes random)
4. Encrypt Master DEK with derived key (AES-256-GCM)
5. Hash password: bcrypt(adminPassword, cost=12)
6. Build ACL entry:
   - Slot: 0
   - Permissions: 0x07
   - Password hash
   - Salt
   - Encrypted DEK
   - Timestamps
   - Label: "Administrator"

For Each READ-Only User:
1. Generate salt
2. Derive key: PBKDF2(userPassword, salt, 100000)
3. Encrypt SAME Master DEK with user's derived key
4. Hash password: bcrypt(userPassword, cost=12)
5. Build ACL entry:
   - Slot: 1, 2, 3, ...
   - Permissions: 0x01
   - Password hash
   - Salt
   - Encrypted DEK
   - Timestamps
   - Label: user-provided
```

### Step 4: Encrypt Payload

```
1. Serialize data sections:
   - Index block
   - Content blocks
   - Embedding blocks
   - Metadata blocks

2. Compress with LZ4 (level 4)

3. Encrypt with Master DEK:
   - Generate random IV (12 bytes)
   - AES-256-GCM encryption
   - Append auth tag (16 bytes)
```

### Step 5: Write File

```
1. Write header (512 bytes)
2. Write ACL entries (256 * N bytes)
3. Write encrypted payload
4. Sync to disk
5. Return ExportResult with recovery key
```

---

## Import Workflow

### Step 1: Read Header

```
1. Open .neuron file
2. Validate magic bytes "NRON"
3. Read version (must be 1)
4. Read packet ID
5. Read recovery key hash
6. Read loading mode
7. Read user count
8. Read metadata JSON
```

### Step 2: Authenticate User

```
1. Prompt user for password
2. Read all ACL entries
3. For each ACL entry:
   - Hash provided password with bcrypt
   - Compare with stored hash
   - If match found:
     * Extract user's salt
     * Derive key: PBKDF2(password, salt, 100000)
     * Decrypt user's encrypted DEK
     * Validate DEK decryption (check auth tag)
     * Break loop

4. If no match found:
   - Return authentication error
   - Do not proceed

5. Extract user permissions from matched ACL entry
```

### Step 3: Decrypt Payload

```
1. Read IV from payload
2. Read ciphertext
3. Decrypt with Master DEK using AES-256-GCM
4. Verify auth tag
5. If verification fails:
   - Return integrity error
   - Do not proceed
```

### Step 4: Decompress

```
1. Decompress with LZ4
2. Parse sections:
   - Index block
   - Content blocks
   - Embedding blocks
   - Metadata blocks
```

### Step 5: Import into MemoryVault

```
1. Check merge strategy:
   - SKIP_DUPLICATES: Check content hashes, skip if exists
   - OVERWRITE: Replace existing with same ID
   - KEEP_BOTH: Generate new IDs
   - MERGE_TAGS: Combine tags if duplicate

2. For each document:
   - Create DOCUMENT_CHUNK blocks
   - Store in MemoryVault
   - Link to packet ID

3. For each embedding:
   - Create EMBEDDING blocks
   - Link to chunks
   - Add to vector index

4. Rebuild indices:
   - Full-text index (if requested)
   - Embedding index (HNSW)
```

### Step 6: Cache Password (if applicable)

```
If loading mode is EMBEDDED:
1. Store in password cache:
   - Packet ID → Cached Password
   - Derived key
   - User slot
   - Permissions

If loading mode is TRANSIENT:
1. Do not cache
2. Clear any existing cache entry
```

---

## Query Workflow (Permission-Aware)

### Step 1: Validate Access

```
1. Check if packet is loaded
2. Check password cache:
   - If not cached and mode is TRANSIENT:
     * Prompt for password
     * Validate and cache
   - If not cached and mode is EMBEDDED:
     * Should not happen (cached on import)
     * Prompt for password as fallback

3. Retrieve user permissions from cache
```

### Step 2: Execute Query

```
1. Preprocess query:
   - Expand medical/military terms
   - Generate query embedding

2. Hybrid retrieval:
   - Sparse: BM25 on full-text index
   - Dense: Vector similarity on embeddings
   - Fuse results (RRF)

3. Filter by packet ID:
   - Only return chunks from this packet
   - Do not mix with other vault data

4. Rerank results
```

### Step 3: Check Permissions

```
If user has READ permission (0x01):
- Return query results
- Allow viewing chunks
- Allow viewing sources

If user has READ-WRITE-ADMIN permission (0x07):
- Same as READ
- Plus: Allow modification operations
```

### Step 4: Generate Response

```
1. Build context from retrieved chunks
2. Generate answer with LLM
3. Include citations (chunk IDs)
4. Calculate confidence score
5. Return QueryResult
```

---

## Admin Operations

### Add READ-Only User

```
Requirements:
- Caller must have ADMIN permission (0x07)
- Must provide admin password
- Must provide new user password
- Must provide user label

Process:
1. Validate admin password
2. Check user slot availability (max 20)
3. Generate salt for new user
4. Derive key from new user password
5. Encrypt Master DEK with new user's key
6. Hash new user password (bcrypt)
7. Create new ACL entry:
   - Next available slot
   - Permissions: 0x01
   - Encrypted DEK
8. Append to packet ACL
9. Update user count in header
```

### Remove User

```
Requirements:
- Caller must have ADMIN permission
- Must provide admin password
- Cannot remove admin (slot 0)

Process:
1. Validate admin password
2. Validate user slot (1-N)
3. Mark ACL entry as deleted:
   - Zero out entry
   - Or mark with special flag
4. Update user count in header
5. Optionally: Compact ACL (remove gaps)
```

### Change Password

```
For Admin (slot 0):
1. Validate old password
2. Derive new key from new password
3. Decrypt Master DEK with old key
4. Re-encrypt Master DEK with new key
5. Update ACL entry:
   - New password hash
   - New salt
   - New encrypted DEK
6. Clear password cache

For READ-only user:
1. Validate old password
2. Follow same process as admin
3. Clear password cache

Note: Admin can reset READ-only passwords without knowing old password
```

### Reset Admin Password with Recovery Key

```
Requirements:
- Must provide recovery key (UUID)
- Must provide new admin password

Process:
1. Validate recovery key:
   - Hash provided key (SHA-256)
   - Compare with stored hash in header
   - If mismatch: Reject

2. If valid:
   - Generate new salt
   - Derive new key from new password
   - Master DEK is still encrypted with OLD key
   - Decrypt Master DEK (requires old DEK still accessible)
   
3. Problem: How to decrypt Master DEK without old password?

Solution: Store recovery-key-encrypted Master DEK:
- During export, encrypt Master DEK twice:
  * Once with admin password (in ACL slot 0)
  * Once with recovery key (in special header field)
- Recovery key encrypted DEK is stored in header
- This allows password reset without old password
```

### Updated Header Structure

```
Add new field to header:

Offset | Size      | Field
-------|-----------|------------------------------------------
66     | 44 bytes  | Recovery-Encrypted DEK
       |           | - 32 bytes: Encrypted Master DEK
       |           | - 12 bytes: IV
110    | 402 bytes | Packet Metadata (JSON, adjusted size)
```

---

## Security Considerations

### Threat Model

```
Threats considered:
1. Password brute force
2. Recovery key theft
3. Man-in-the-middle (file tampering)
4. Privilege escalation (READ → ADMIN)
5. Packet cloning/replay

Mitigations:
1. Bcrypt with cost=12 (slow hashing)
2. Recovery key must be stored securely by user
3. AES-GCM authentication prevents tampering
4. Permissions enforced cryptographically (separate DEKs not feasible, rely on application logic)
5. Packet ID uniqueness, timestamp validation
```

### Key Storage

```
DO NOT STORE:
- Recovery key in packet
- Recovery key in application
- Recovery key in cloud (unless user chooses)
- Plaintext passwords anywhere

STORE SECURELY:
- Password cache: In-memory only, cleared on app close
- Recovery key: User responsibility (print, write down, password manager)
```

### Audit Trail

```
For medical/military compliance:

Optional audit log (encrypted):
- Timestamp
- User slot
- Action (QUERY, ADD, MODIFY, DELETE, PASSWORD_CHANGE)
- Result (SUCCESS, FAILURE)
- No sensitive data in log

Audit log encrypted with Master DEK
Append-only structure
Stored in separate section of packet
```

---

## File Format Versioning

### Version 1 Specification

```
Version: 1
Magic: "NRON"

Breaking changes in future versions:
- Increment version number
- Maintain backward compatibility reader
- Old clients reject new versions

Non-breaking changes:
- Add optional sections
- Add reserved fields
- Do not increment version
```

---

## Error Handling

### Export Errors

```
- ERR_NO_DATA: No documents match filters
- ERR_TOO_MANY_USERS: Exceeded max users (20)
- ERR_WEAK_PASSWORD: Password too short (<12 chars recommended)
- ERR_DUPLICATE_LABEL: User label already exists
- ERR_WRITE_FAILED: Cannot write to output file
```

### Import Errors

```
- ERR_INVALID_MAGIC: Not a .neuron file
- ERR_UNSUPPORTED_VERSION: Version > 1
- ERR_AUTH_FAILED: Password incorrect
- ERR_CORRUPTED: Auth tag verification failed
- ERR_DECOMPRESS_FAILED: LZ4 decompression error
- ERR_DUPLICATE_PACKET: Packet ID already imported
```

### Query Errors

```
- ERR_NOT_LOADED: Packet not imported
- ERR_AUTH_REQUIRED: Password cache expired (transient mode)
- ERR_PERMISSION_DENIED: User lacks required permission
- ERR_PACKET_CORRUPTED: Data integrity check failed
```

---

## Implementation Checklist

### Phase 1: Core Structure

```
- [ ] Define BlockType.NEURON_PACKET_META
- [ ] Define BlockType.NEURON_ACL_ENTRY
- [ ] Implement NeuronPacketMetadata data class
- [ ] Implement ACLEntry data class
- [ ] Implement file header writer
- [ ] Implement file header reader
```

### Phase 2: Encryption

```
- [ ] Implement PBKDF2 key derivation
- [ ] Implement bcrypt password hashing
- [ ] Implement AES-256-GCM encryption/decryption
- [ ] Implement Master DEK generation
- [ ] Implement recovery-key-encrypted DEK
- [ ] Implement per-user DEK encryption
```

### Phase 3: Export

```
- [ ] Implement exportNeuronPacket() API
- [ ] Implement data filtering (tags, category, date)
- [ ] Implement ACL builder
- [ ] Implement payload serialization
- [ ] Implement LZ4 compression
- [ ] Implement file writer
- [ ] Implement progress callbacks
```

### Phase 4: Import

```
- [ ] Implement importNeuronPacket() API
- [ ] Implement header parser
- [ ] Implement ACL parser
- [ ] Implement password authentication
- [ ] Implement payload decryption
- [ ] Implement LZ4 decompression
- [ ] Implement merge strategies
- [ ] Implement MemoryVault integration
```

### Phase 5: Password Management

```
- [ ] Implement password cache (in-memory)
- [ ] Implement cache invalidation logic
- [ ] Implement transient mode
- [ ] Implement embedded mode
- [ ] Implement cache clearing on app close
```

### Phase 6: Query System

```
- [ ] Implement queryNeuronPacket() API
- [ ] Implement permission checks
- [ ] Implement packet-scoped retrieval
- [ ] Integrate with existing hybrid search
- [ ] Implement response generation
```

### Phase 7: Admin Operations

```
- [ ] Implement addPacketUser() API
- [ ] Implement removePacketUser() API
- [ ] Implement changePacketPassword() API
- [ ] Implement resetAdminPassword() API
- [ ] Implement permission validation
```

### Phase 8: Testing

```
- [ ] Unit tests for encryption
- [ ] Unit tests for key derivation
- [ ] Integration tests for export/import
- [ ] Permission enforcement tests
- [ ] Cache invalidation tests
- [ ] Corruption recovery tests
```

---

## Performance Targets

```
Export (10,000 chunks, 5MB compressed):
- Processing: < 5 seconds
- Encryption: < 2 seconds
- Total: < 10 seconds

Import (same dataset):
- Decryption: < 2 seconds
- Decompression: < 1 second
- MemoryVault insertion: < 5 seconds
- Total: < 10 seconds

Query (with cached password):
- Authentication: < 1 ms (cache hit)
- Retrieval: < 200 ms
- LLM generation: < 3 seconds
- Total: < 3.5 seconds

Password validation (cold):
- Bcrypt verification: ~100 ms (cost=12)
- PBKDF2 derivation: ~50 ms (100,000 iterations)
- Total: ~150 ms
```

---

## Migration from Existing MemoryVault

### Backward Compatibility

```
Existing MemoryVault files (.mvlt):
- Continue to work unchanged
- No migration required
- Neuron packets are separate files

Loading behavior:
1. If file extension is .mvlt:
   - Load as traditional MemoryVault
   - No password required (or existing password system)

2. If file extension is .neuron:
   - Load as Neuron packet
   - Require password authentication
   - Apply permission model
```

### Data Sharing Between Systems

```
Traditional MemoryVault → Neuron Packet:
- Use exportNeuronPacket() API
- Converts vault data to packet format
- Adds ACL and encryption

Neuron Packet → Traditional MemoryVault:
- Use importNeuronPacket() API
- Decrypts and imports chunks
- Chunks become regular vault blocks
- Permission model no longer enforced (single-user vault)
```

---

## Example Usage

### Export Example

```kotlin
val vault = MemoryVault(context)

// Create packet with admin and 2 read-only users
val exportResult = vault.exportNeuronPacket(
    metadata = NeuronPacketMetadata(
        name = "Emergency Medicine Protocols",
        description = "ER triage and treatment guidelines",
        domain = "medical",
        tags = setOf("emergency", "medicine", "trauma")
    ),
    config = ExportConfig(
        adminPassword = "AdminSecurePass123!",
        readOnlyUsers = listOf(
            ReadOnlyUser(
                password = "NursePass456!",
                label = "Nursing Staff"
            ),
            ReadOnlyUser(
                password = "DocPass789!",
                label = "Attending Physicians"
            )
        ),
        loadingMode = LoadingMode.EMBEDDED,
        filterByTags = setOf("emergency")
    ),
    outputFile = File("/sdcard/emergency_protocols.neuron"),
    progressCallback = { progress ->
        println("Export progress: ${(progress * 100).toInt()}%")
    }
)

if (exportResult.success) {
    println("Packet created: ${exportResult.packetFile}")
    println("Recovery key: ${exportResult.recoveryKey}")
    println("SAVE THIS KEY SECURELY!")
} else {
    println("Export failed: ${exportResult.error}")
}
```

### Import Example

```kotlin
val vault = MemoryVault(context)

// Import as read-only user
val importResult = vault.importNeuronPacket(
    packetFile = File("/sdcard/emergency_protocols.neuron"),
    password = "NursePass456!",
    config = ImportConfig(
        mergeStrategy = MergeStrategy.SKIP_DUPLICATES,
        validateIntegrity = true
    ),
    progressCallback = { progress ->
        println("Import progress: ${(progress * 100).toInt()}%")
    }
)

if (importResult.success) {
    println("Packet imported: ${importResult.packetId}")
    println("Permissions: ${importResult.userPermissions}")
    // 0x01 = READ only
} else {
    println("Import failed: ${importResult.error}")
}
```

### Query Example

```kotlin
// Query the imported packet
val queryResult = vault.queryNeuronPacket(
    packetId = importResult.packetId,
    query = "What is the protocol for treating severe burns?",
    config = QueryConfig(
        maxResults = 5,
        relevanceThreshold = 0.7f
    )
)

println("Answer: ${queryResult.answer}")
println("Sources:")
queryResult.sources.forEach { source ->
    println("  - ${source.source}: ${source.citationKey}")
}
println("Confidence: ${queryResult.confidence}")
```

### Admin Operations Example

```kotlin
// Add a new read-only user (requires admin password)
vault.addPacketUser(
    packetId = packetId,
    newUser = ReadOnlyUser(
        password = "NewUserPass!",
        label = "Lab Technicians"
    ),
    adminPassword = "AdminSecurePass123!"
)

// Reset admin password with recovery key
vault.resetAdminPassword(
    packetId = packetId,
    recoveryKey = "8f3a7b2c-9d4e-4f1a-b5c6-7e8f9a0b1c2d",
    newPassword = "NewAdminPass456!"
)
```

---

## Conclusion

This specification defines a complete multi-user, permission-based RAG packet system that extends MemoryVault with secure sharing capabilities. The design prioritizes privacy, offline operation, and cryptographic security while maintaining usability for medical and military use cases.