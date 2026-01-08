## Implementation Plan: MemoryVault System

### System Constraints & Assumptions
- **Available RAM for vault operations:** ~1GB max
- **Model consumes:** 2-3GB
- **Android devices:** 6-12GB total RAM
- **Compression:** LZ4 (fast compression/decompression)
- **Defrag:** On-demand (user-triggered or automatic threshold)
- **Persistence:** Write-Ahead Logging (WAL) for safety
- **Embeddings:** Dynamic dimensions, float32 (can add quantization later)

---

## Phase-by-Phase Implementation Plan

### **Phase 1: Foundation Layer**
*Goal: Basic file structure, no encryption, simple operations*

#### 1.1 File Format Core
```
Components to build:
├─ VaultHeader (256 bytes)
│  ├─ Magic bytes validation
│  ├─ Version handling
│  ├─ Offset management
│  └─ Metadata storage
│
├─ BlockWriter
│  ├─ Append blocks to file
│  ├─ Write block headers
│  ├─ Calculate checksums (CRC32)
│  └─ Track block offsets
│
├─ BlockReader
│  ├─ Read blocks by offset
│  ├─ Validate checksums
│  ├─ Parse block headers
│  └─ Extract block data
│
└─ VaultFile (handles file I/O)
   ├─ Memory-mapped access
   ├─ RandomAccessFile wrapper
   └─ Thread-safe operations
```

**Key Classes:**
```kotlin
// Core data structures
data class VaultHeader(
    val magic: ByteArray,      // "MVLT"
    val version: Short,
    val indexOffset: Long,
    val indexSize: Long,
    val contentOffset: Long,
    val createdTime: Long,
    val modifiedTime: Long
)

data class BlockHeader(
    val blockId: UUID,
    val blockType: BlockType,
    val contentSize: Long,
    val timestamp: Long,
    val checksum: Int,
    val compressionFlag: Boolean
)

data class Block(
    val header: BlockHeader,
    val data: ByteArray
)
```

**Deliverables:**
- ✅ Can create new vault file
- ✅ Can append blocks
- ✅ Can read blocks by offset
- ✅ Checksum validation works

---

### **Phase 2: Index System**
*Goal: Fast lookups, range queries, basic search*

#### 2.1 In-Memory Index Structure

```
Index Components:
├─ PrimaryIndex (HashMap)
│  └─ UUID → BlockMetadata (O(1) lookup)
│
├─ TimestampIndex (TreeMap)
│  └─ Timestamp → List<UUID> (range queries)
│
├─ TypeIndex (HashMap)
│  └─ BlockType → List<UUID> (filter by type)
│
├─ CategoryIndex (HashMap)
│  └─ Category → List<UUID> (filter by category)
│
├─ TagIndex (HashMap)
│  └─ Tag → List<UUID> (filter by tags)
│
└─ FullTextIndex (Inverted index)
   └─ Token → List<UUID> (text search)
```

**Block Metadata Structure:**
```kotlin
data class BlockMetadata(
    val blockId: UUID,
    val blockType: BlockType,
    val fileOffset: Long,      // Where in file
    val compressedSize: Long,  // Size on disk
    val uncompressedSize: Long,// Size in memory
    val timestamp: Long,
    val category: String?,
    val tags: Set<String>,
    val contentHash: String,   // SHA-256 for dedup
    val searchableText: String? // Extracted for FTS
)
```

#### 2.2 Index Serialization Format

```
Index File Structure (at end of vault):
┌───────────────────────────────────────┐
│ Index Header (64 bytes)               │
│ - Magic "MIDX"                        │
│ - Version                             │
│ - Entry count                         │
│ - Offsets to sub-indices              │
├───────────────────────────────────────┤
│ Metadata Array (fixed 512 bytes each)│
│ ├─ Entry 1                            │
│ ├─ Entry 2                            │
│ └─ Entry N                            │
├───────────────────────────────────────┤
│ Timestamp B-Tree (serialized)         │
│ - Sorted tree structure               │
├───────────────────────────────────────┤
│ Category HashMap (serialized)         │
│ - Key-value pairs                     │
├───────────────────────────────────────┤
│ Tag HashMap (serialized)              │
│ - Key-value pairs                     │
├───────────────────────────────────────┤
│ Full-Text Inverted Index              │
│ - Token → offset to UUID list         │
│ - UUID lists compressed               │
└───────────────────────────────────────┘
```

#### 2.3 Index Loading Strategy (Memory-Conscious)

```
Three-Tier Caching:
┌─────────────────────────────────────┐
│ L1: Hot Cache (in RAM)              │
│ - 1000 most recent BlockMetadata    │
│ - LRU eviction                      │
│ - ~500KB                            │
├─────────────────────────────────────┤
│ L2: Memory-mapped index             │
│ - Metadata array mapped to memory   │
│ - Fast random access                │
│ - OS manages paging                 │
├─────────────────────────────────────┤
│ L3: Full-text index (on-demand)     │
│ - Load only during search           │
│ - Cache top N tokens                │
│ - Compress rarely used              │
└─────────────────────────────────────┘
```

**Memory Budget:**
```
Estimated for 50,000 items:
- Metadata array: 512 bytes × 50k = 25MB (memory-mapped, not fully in RAM)
- L1 cache: 1000 items × 512 bytes = ~500KB
- Timestamp tree: ~2MB (loaded on demand)
- FTS index: ~10MB (loaded during search only)
- Total RAM footprint: ~3-5MB during normal ops, ~15MB during search
```

**Deliverables:**
- ✅ Index builds from blocks
- ✅ Fast ID lookup (<1ms)
- ✅ Range queries work
- ✅ Filter by type/category/tags
- ✅ Memory stays under budget

---

### **Phase 3: Write-Ahead Log (WAL)**
*Goal: Crash recovery, data integrity*

#### 3.1 WAL Structure

```
WAL File: memory_vault.wal
┌─────────────────────────────────────┐
│ WAL Header (128 bytes)              │
│ - Magic "WLOG"                      │
│ - Sequence number                   │
│ - Checkpoint offset in main file   │
├─────────────────────────────────────┤
│ Log Entries (append-only)           │
│                                     │
│ Entry format:                       │
│ ┌─────────────────────────────────┐ │
│ │ Entry Header (32 bytes)         │ │
│ │ - Operation type                │ │
│ │ - Timestamp                     │ │
│ │ - Entry size                    │ │
│ │ - Checksum                      │ │
│ ├─────────────────────────────────┤ │
│ │ Operation Data (variable)       │ │
│ │ - For INSERT: full block        │ │
│ │ - For DELETE: block ID          │ │
│ │ - For UPDATE: block ID + data   │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

#### 3.2 WAL Operations

**Write Flow:**
```
1. Append operation to WAL
2. fsync() WAL file (ensure durability)
3. Perform operation on main vault file
4. Mark WAL entry as committed
5. Periodically checkpoint (merge WAL → main file, truncate WAL)
```

**Recovery Flow (on app restart):**
```
1. Check if WAL exists
2. Read uncommitted entries
3. Replay operations on main vault
4. Truncate WAL
5. Rebuild index if needed
```

**Checkpoint Strategy:**
```kotlin
Trigger checkpoint when:
- WAL size > 10MB
- App enters background
- Manual flush called
- Every N operations (configurable)

Checkpoint process:
1. Flush all pending writes to main file
2. Update index
3. Encrypt and write index
4. Truncate WAL
5. Update vault header with new index offset
```

**Deliverables:**
- ✅ WAL appends work
- ✅ Recovery replays correctly
- ✅ Checkpoint merges data
- ✅ No data loss on crash

---

### **Phase 4: Encryption Layer**
*Goal: Secure storage with minimal performance hit*

#### 4.1 Encryption Strategy

**Key Hierarchy:**
```
AndroidKeyStore (hardware-backed)
        ↓
  Master Key (AES-256)
        ↓
    ┌───┴───┐
    ↓       ↓
Content   Index
 DEK      DEK
(AES-256) (AES-256)

DEK = Data Encryption Key (derived from master)
```

**Why two DEKs:**
- Content blocks encrypted independently
- Index encrypted separately
- Can re-key one without touching the other

#### 4.2 Block Encryption

```
Encrypted Block Format:
┌─────────────────────────────────────┐
│ IV (12 bytes)                       │
├─────────────────────────────────────┤
│ Encrypted Data                      │
│ ├─ Block Header (encrypted)         │
│ └─ Block Data (encrypted)           │
├─────────────────────────────────────┤
│ Auth Tag (16 bytes)                 │
└─────────────────────────────────────┘

Algorithm: AES-256-GCM
- Authenticated encryption
- Random IV per block
- Auth tag prevents tampering
```

#### 4.3 Compression + Encryption Pipeline

```
Write Pipeline:
Data → LZ4 Compress → AES-GCM Encrypt → Write to file
                ↓
         (if compression ratio > 0.9, skip compression)

Read Pipeline:
Read from file → AES-GCM Decrypt → LZ4 Decompress → Data
```

**Optimization: Compress only if beneficial**
```kotlin
fun shouldCompress(data: ByteArray): Boolean {
    // Sample first 1KB
    val sample = data.take(1024)
    val compressed = LZ4.compress(sample)
    return compressed.size < sample.size * 0.9
}
```

**Deliverables:**
- ✅ All blocks encrypted
- ✅ Index encrypted
- ✅ Compression works
- ✅ Keys managed securely
- ✅ Performance acceptable (<10ms per block)

---

### **Phase 5: Content Deduplication**
*Goal: Save space, avoid storing duplicates*

#### 5.1 Deduplication Strategy

```
Content Hash (SHA-256):
- Hash uncompressed content
- Store hash in metadata
- Before writing, check if hash exists
- If exists, create reference instead of new block

Reference Block:
┌─────────────────────────────────────┐
│ Block Header                        │
│ - Type: REFERENCE                   │
├─────────────────────────────────────┤
│ Reference Data                      │
│ - Target Block ID (16 bytes)        │
│ - Ref count (4 bytes)               │
└─────────────────────────────────────┘
```

**Ref Counting:**
```
When block has multiple references:
- Track ref count
- Only delete when ref count = 0
- Update ref count on delete
```

**Deliverables:**
- ✅ Duplicate content detected
- ✅ References created instead
- ✅ Ref counting works
- ✅ Space savings verified

---

### **Phase 6: Search System**
*Goal: Fast text search, tag filtering*

#### 6.1 Text Tokenization

```kotlin
Text Processing Pipeline:
Input text 
  → Lowercase
  → Remove punctuation
  → Split on whitespace
  → Remove stop words (optional)
  → Stem/lemmatize (optional)
  → Tokens

Example:
"Hello World! This is Kotlin." 
  → ["hello", "world", "kotlin"]
```

#### 6.2 Inverted Index Structure

```
In-Memory Structure:
Map<String, PostingList>

PostingList:
- List of (BlockID, positions)
- Sorted by relevance/timestamp

Example:
"kotlin" → [
  (uuid1, [5, 23, 67]),    // Positions in block
  (uuid2, [12]),
  (uuid5, [3, 89])
]
```

#### 6.3 Search Query Processing

```
Query Types:

1. Single term: "kotlin"
   → Lookup "kotlin" in index
   
2. AND query: "kotlin android"
   → Intersection of posting lists
   
3. OR query: "kotlin | java"
   → Union of posting lists
   
4. Phrase: "hello world"
   → Find blocks with both, check positions
   
5. Prefix: "kot*"
   → Trie-based prefix match
```

**Ranking Algorithm (simple TF-IDF):**
```kotlin
Score = (Term Frequency in doc) × log(Total docs / Docs with term)

Sort results by score descending
```

#### 6.4 Index Persistence

```
FTS Index Serialization:
┌─────────────────────────────────────┐
│ Token Dictionary (sorted)           │
│ ├─ "android" → offset1              │
│ ├─ "kotlin" → offset2               │
│ └─ "world" → offset3                │
├─────────────────────────────────────┤
│ Posting Lists (LZ4 compressed)      │
│ ├─ offset1: [uuid1, uuid5, ...]    │
│ ├─ offset2: [uuid2, uuid3, ...]    │
│ └─ offset3: [uuid1, uuid9, ...]    │
└─────────────────────────────────────┘

Load on demand:
- Keep token dictionary in RAM (~1-2MB)
- Memory-map posting lists
- Decompress only needed lists
```

**Deliverables:**
- ✅ Text tokenization works
- ✅ Index builds correctly
- ✅ Search returns relevant results
- ✅ Ranking makes sense
- ✅ Performance: <100ms for most queries

---

### **Phase 7: Embedding Storage & Vector Search**
*Goal: Semantic search with embeddings*

#### 7.1 Embedding Block Structure

```
Embedding Block:
┌─────────────────────────────────────┐
│ Block Header                        │
│ - Type: EMBEDDING                   │
├─────────────────────────────────────┤
│ Embedding Metadata                  │
│ - Dimension (4 bytes)               │
│ - Model name (64 bytes)             │
│ - Linked content ID (16 bytes)     │
├─────────────────────────────────────┤
│ Vector Data (float32 array)         │
│ - Dimension × 4 bytes               │
└─────────────────────────────────────┘

Storage size examples:
- 384-dim: 384 × 4 = 1,536 bytes
- 768-dim: 768 × 4 = 3,072 bytes
- 1536-dim: 1536 × 4 = 6,144 bytes
```

#### 7.2 Vector Search Strategy

**For small datasets (<5k embeddings):**
```
Brute Force Search:
1. Load all embeddings into memory
2. Compute cosine similarity with query
3. Sort by similarity
4. Return top K

Cosine similarity:
cos(A, B) = (A · B) / (||A|| × ||B||)

Memory: 5k × 1536 × 4 = ~30MB
Time: O(N) but fast for small N
```

**For large datasets (>5k embeddings):**
```
HNSW Index (Hierarchical Navigable Small World):
- Build graph structure
- Approximate nearest neighbor
- Much faster: O(log N)
- Trade-off: slight accuracy loss

Library option: usearch-kotlin
- Native bindings
- Very fast
- Android compatible
```

#### 7.3 Embedding Index Structure

```
Embedding Index:
┌─────────────────────────────────────┐
│ Index Header                        │
│ - Algorithm type (brute/HNSW)      │
│ - Dimension                         │
│ - Metric (cosine/euclidean/dot)    │
│ - Entry count                       │
├─────────────────────────────────────┤
│ Vector Data (all embeddings)        │
│ - Flat array: [v1, v2, ..., vN]    │
│ - Or HNSW graph structure           │
├─────────────────────────────────────┤
│ ID Mapping                          │
│ - Index position → Block ID         │
└─────────────────────────────────────┘

Lazy Loading:
- Build index on first semantic search
- Cache in memory
- Rebuild when embeddings added/removed
```

#### 7.4 Quantization (Optional Optimization)

```
Float32 → Int8 quantization:
- Reduces size by 4×
- Slight accuracy loss (~1-2%)
- Much faster on mobile CPUs

Process:
1. Find min/max of each dimension
2. Map to [0, 255] range
3. Store scale factors
4. Dequantize for search

384-dim embedding:
- Float32: 1,536 bytes
- Int8: 384 bytes + 8 bytes (scale) = 392 bytes
- 75% space savings!
```

**Deliverables:**
- ✅ Embeddings stored efficiently
- ✅ Vector search works
- ✅ Returns relevant results
- ✅ Performance acceptable
- ✅ Optional quantization implemented

---

### **Phase 8: Defragmentation**
*Goal: Reclaim space from deleted blocks*

#### 8.1 Free Space Tracking

```
Free List Structure (in index):
List<FreeSpan>

FreeSpan:
- offset: Long        // Where in file
- size: Long          // How many bytes
- timestamp: Long     // When freed

Sorted by size for best-fit allocation
```

#### 8.2 Defragmentation Algorithm

```
Compact Operation:
1. Create new temporary file
2. Write header
3. For each active block:
   - Read from old file
   - Write to new file (sequential)
   - Update offset in index
4. Write updated index to new file
5. Atomic rename: new → old
6. Delete old file

Trigger conditions:
- Wasted space > 30% of file size
- User manually triggers
- Scheduled (e.g., app idle)
```

#### 8.3 Online Defragmentation (Advanced)

```
Incremental Compaction:
- Move blocks in small batches
- Don't block user operations
- Update pointers atomically
- Can pause/resume

Use coroutines:
suspend fun defragmentAsync() {
    // Move 100 blocks at a time
    // Yield between batches
}
```

**Deliverables:**
- ✅ Deleted blocks tracked
- ✅ Defrag reclaims space
- ✅ No data loss during defrag
- ✅ Progress reporting works

---

### **Phase 9: Corruption Recovery**
*Goal: Handle file corruption gracefully*

#### 9.1 Checksum Strategy

```
Multiple Levels:

L1: Block-level (already have CRC32)
- Detects corruption in single block
- Can skip corrupted block

L2: Index checksum (SHA-256)
- Detects index corruption
- Can rebuild from blocks

L3: File-level checksum (in header)
- Quick validation on open
- Rolling hash for efficiency
```

#### 9.2 Index Rebuilding

```
Rebuild Process (if index corrupted):
1. Scan entire file sequentially
2. Read each block header
3. Validate checksum
4. Extract metadata
5. Rebuild index structures
6. Write new index

Can recover as long as blocks are intact!
```

#### 9.3 Backup Strategies

```
Auto-Backup:
- Keep last N versions
- Store in separate directory
- Compressed copies
- Periodic (daily/weekly)

Quick Backup Format:
.mvault.backup/
├─ vault_2024_01_08.mvlt.gz
├─ vault_2024_01_07.mvlt.gz
└─ vault_2024_01_06.mvlt.gz

Export/Import:
- User-triggered full export
- Encrypted archive
- Can import to new device
```

#### 9.4 Validation Tool

```kotlin
suspend fun validateVault(): ValidationReport {
    return ValidationReport(
        headerValid = checkHeader(),
        blocksChecked = checkAllBlocks(),
        blocksCorrupted = corruptedBlocks,
        indexValid = checkIndex(),
        canRecover = true/false,
        recommendations = [...]
    )
}
```

**Deliverables:**
- ✅ Detect corruption
- ✅ Rebuild index if needed
- ✅ Auto-backup works
- ✅ Validation tool functional

---

### **Phase 10: Public API & Convenience**
*Goal: Clean, intuitive API for app developers*

#### 10.1 High-Level API

```kotlin
class MemoryVault(context: Context) {
    
    // === Core Operations ===
    
    suspend fun addMessage(
        message: Messages,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String // returns block ID
    
    suspend fun addFile(
        file: File,
        category: String? = null,
        tags: Set<String> = emptySet(),
        generateThumbnail: Boolean = true
    ): String
    
    suspend fun addCustomData(
        type: String,
        data: JSONObject,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String
    
    suspend fun addEmbedding(
        vector: FloatArray,
        linkedContentId: String,
        modelName: String
    ): String
    
    // === Retrieval ===
    
    suspend fun getById(id: String): VaultItem?
    
    suspend fun getMessages(
        category: String? = null,
        tags: Set<String>? = null,
        fromTime: Long? = null,
        toTime: Long? = null,
        limit: Int = 100
    ): List<Messages>
    
    suspend fun getFiles(
        mimeType: String? = null,
        category: String? = null,
        limit: Int = 100
    ): List<VaultFile>
    
    // === Search ===
    
    suspend fun textSearch(
        query: String,
        filters: SearchFilters? = null
    ): SearchResults
    
    suspend fun semanticSearch(
        embedding: FloatArray,
        limit: Int = 10,
        threshold: Float = 0.7f
    ): List<ScoredResult>
    
    // === Management ===
    
    suspend fun delete(id: String)
    suspend fun deleteMany(ids: List<String>)
    suspend fun updateTags(id: String, tags: Set<String>)
    suspend fun updateCategory(id: String, category: String?)
    
    // === Maintenance ===
    
    suspend fun defragment(progress: (Float) -> Unit = {})
    suspend fun validate(): ValidationReport
    suspend fun backup(destination: File)
    suspend fun restore(backup: File)
    suspend fun getStats(): VaultStats
}
```

#### 10.2 Data Classes

```kotlin
sealed class VaultItem {
    abstract val id: String
    abstract val timestamp: Long
    abstract val category: String?
    abstract val tags: Set<String>
}

data class MessageItem(
    override val id: String,
    override val timestamp: Long,
    override val category: String?,
    override val tags: Set<String>,
    val message: Messages
) : VaultItem()

data class FileItem(
    override val id: String,
    override val timestamp: Long,
    override val category: String?,
    override val tags: Set<String>,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val thumbnailId: String?
) : VaultItem()

data class CustomDataItem(
    override val id: String,
    override val timestamp: Long,
    override val category: String?,
    override val tags: Set<String>,
    val dataType: String,
    val data: JSONObject
) : VaultItem()

data class SearchFilters(
    val category: String? = null,
    val tags: Set<String>? = null,
    val types: Set<BlockType>? = null,
    val fromTime: Long? = null,
    val toTime: Long? = null
)

data class SearchResults(
    val items: List<VaultItem>,
    val totalCount: Int,
    val queryTime: Long // ms
)

data class ScoredResult(
    val item: VaultItem,
    val score: Float // similarity score
)

data class VaultStats(
    val totalItems: Int,
    val totalSizeBytes: Long,
    val wastedSpaceBytes: Long,
    val messageCount: Int,
    val fileCount: Int,
    val embeddingCount: Int,
    val oldestItem: Long,
    val newestItem: Long
)
```

**Deliverables:**
- ✅ Clean API surface
- ✅ Type-safe operations
- ✅ Coroutine-based async
- ✅ Good error handling
- ✅ Comprehensive examples