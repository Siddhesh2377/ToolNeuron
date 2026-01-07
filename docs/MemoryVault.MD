# MemoryVault

A high-performance, encrypted storage system for Android applications designed for secure data persistence with minimal memory footprint.

## Overview

MemoryVault is a custom binary storage engine optimized for Android devices with RAM constraints. It provides encrypted, compressed storage for heterogeneous data types including messages, files, custom data, and vector embeddings.

## Key Features

- **Hardware-backed Encryption**: AES-256-GCM encryption using AndroidKeyStore
- **Compression**: LZ4 fast compression for reduced storage footprint
- **Memory Efficient**: Operates within ~1GB RAM budget with three-tier caching
- **Crash Recovery**: Write-Ahead Logging (WAL) ensures data durability
- **Multiple Index Types**: Primary, timestamp, type, category, tag, and full-text search indices
- **Vector Search**: Support for semantic search with embeddings
- **Content Deduplication**: Automatic detection and reference counting for duplicate content
- **Defragmentation**: On-demand space reclamation from deleted blocks

## System Requirements

- Android API 26 (Oreo) or higher
- Minimum 6GB device RAM
- Kotlin 1.9+

## Architecture

### Storage Format

MemoryVault uses a custom binary file format with the following structure:

```
[256-byte Header][Content Blocks][Index]
```

Each block consists of:
- 64-byte block header (UUID, type, size, timestamp, checksum, flags)
- Variable-length encrypted and compressed data

### Memory Management

Three-tier caching system:
- L1: Hot cache (1000 most recent items, ~500KB)
- L2: Memory-mapped index (on-demand paging)
- L3: Full-text index (loaded during search only)

Target memory usage: 3-5MB during normal operations, 15MB during search.

### Data Integrity

- CRC32 checksums at block level
- SHA-256 checksums for index
- Write-Ahead Log for crash recovery
- Automatic index rebuild from blocks if corrupted

## Installation

Add the module to your `settings.gradle`:

```gradle
include ':memoryvault'
```

Add dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation project(':memoryvault')
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.lz4:lz4-java:1.8.0'
}
```

## Usage

### Initialization

```kotlin
val vault = MemoryVault(context)
vault.initialize()
```

### Adding Data

```kotlin
// Add a text message
val messageId = vault.addMessage(
    content = "Hello, World!",
    category = "chat",
    tags = setOf("important")
)

// Add a file
val fileId = vault.addFile(
    file = File("/path/to/file"),
    mimeType = "image/jpeg",
    category = "photos"
)

// Add custom JSON data
val dataId = vault.addCustomData(
    dataType = "user_profile",
    data = JSONObject("""{"name": "John", "age": 30}"""),
    category = "users"
)

// Add vector embedding
val embeddingId = vault.addEmbedding(
    vector = floatArrayOf(0.1f, 0.2f, 0.3f),
    linkedContentId = messageId,
    modelName = "sentence-transformers"
)
```

### Retrieving Data

```kotlin
// Get by ID
val item = vault.getById(messageId)

// Get messages by category
val messages = vault.getMessages(
    category = "chat",
    tags = setOf("important"),
    limit = 100
)

// Full-text search
val results = vault.textSearch("Hello")

// Semantic search
val similarItems = vault.semanticSearch(
    embedding = queryVector,
    limit = 10,
    threshold = 0.7f
)
```

### Maintenance

```kotlin
// Defragmentation
vault.defragment { progress ->
    println("Progress: ${(progress * 100).toInt()}%")
}

// Validation
val report = vault.validate()
println("Valid blocks: ${report.validBlocks}/${report.totalBlocks}")

// Backup
val backupFile = File("/path/to/backup.mvlt")
vault.backup(backupFile)

// Restore
vault.restore(backupFile)

// Statistics
val stats = vault.getStats()
println("Total items: ${stats.totalItems}")
println("Total size: ${stats.totalSizeBytes / 1024 / 1024} MB")
println("Compression ratio: ${stats.compressionRatio}")
```

### Cleanup

```kotlin
vault.close()
```

## Implementation Details

### Encryption

Uses Android Keystore for master key generation with randomized IV generation per encryption operation. All content blocks and the index are encrypted independently.

### Compression

LZ4 compression is applied selectively based on data characteristics. A 1KB sample is tested, and compression is only applied if it achieves better than 90% compression ratio.

### Index Structure

Multiple indices are maintained for efficient queries:
- Primary index: UUID to metadata (O(1) lookup)
- Timestamp index: TreeMap for range queries
- Type index: Filter by block type
- Category index: Filter by category
- Tag index: Filter by tags
- Full-text index: Inverted index with TF-IDF ranking

### Vector Search

For datasets under 5,000 embeddings, brute-force cosine similarity search is used. For larger datasets, integration with HNSW (Hierarchical Navigable Small World) graphs is recommended.

### Write-Ahead Log

All mutations are first written to the WAL and fsync'd before being applied to the main vault file. On startup, any uncommitted WAL entries are replayed to ensure consistency.

### Defragmentation

Deleted blocks are tracked in a free list. Defragmentation creates a new file, copies all active blocks sequentially, updates the index, and atomically replaces the old file.

## Performance Characteristics

- Write throughput: ~1000 messages/second
- Read throughput: ~5000 messages/second
- Index lookup: <1ms
- Full-text search: <100ms for most queries
- Checkpoint overhead: ~20ms per 1000 items

## Thread Safety

All public methods are thread-safe and use coroutines with proper synchronization. Internal operations use mutex locks to prevent concurrent modifications.

## Limitations

- Maximum block size: 2GB (practical limit ~100MB for memory efficiency)
- Maximum items: Limited by available storage
- Full-text index size: Grows linearly with content
- Vector search: Linear time for brute-force, requires external HNSW for large datasets

## License

Proprietary - All rights reserved

## Author

Developed for secure chat storage in Android applications requiring high performance and low memory overhead.

## Version

1.0.0

## Changelog

### 1.0.0 (2026-01-08)
- Initial release
- AES-256-GCM encryption with AndroidKeyStore
- LZ4 compression
- Multiple index types
- Vector embedding support
- Write-Ahead Log for crash recovery
- Defragmentation and backup/restore functionality
