# file_ops — Native File I/O Layer

Strict, sandboxed file operations module for ToolNeuron2. All disk I/O in the app goes through this C++ native layer. Kotlin never touches the filesystem directly.

## Purpose

- Enforce strict file permissions (700 dirs, 600 files)
- Jail all paths to the app's data directory (no traversal, no symlinks)
- Provide atomic writes (write → fsync → rename)
- Memory-mapped reads for large files
- File locking for concurrent access safety
- Secure deletion (zero-fill + unlink)

## Architecture

```
app/ (Kotlin)
  └── FileOps.kt (thin JNI wrapper)
        │
        │ JNI
        ▼
file_ops/ (C++)
  ├── file_ops.cpp        JNI entry points
  ├── io_engine.cpp/h     Core I/O (read, write, mmap, fsync)
  ├── path_guard.cpp/h    Path validation & sandboxing
  └── CMakeLists.txt
```

## JNI API

| Function | Description |
|---|---|
| `nativeInit(basePath)` | Set data root, create directory structure, set permissions |
| `nativeWrite(path, data, offset)` | Atomic write: write to `.tmp`, fsync, rename over target |
| `nativeRead(path, offset, length)` | Read bytes, mmap-backed for large files |
| `nativeDelete(path)` | Secure delete: zero-fill file contents, then unlink |
| `nativeExists(path)` | Fast `stat()` check |
| `nativeListDir(path)` | List directory entries (files only, no `.` / `..`) |
| `nativeRename(from, to)` | Atomic rename (`rename()` syscall) |
| `nativeGetSize(path)` | Return file size in bytes |
| `nativeFsync(path)` | Force flush to disk |
| `nativeLock(path)` | Acquire exclusive file lock (`flock`) |
| `nativeUnlock(path)` | Release file lock |

## Path Security

All paths are validated in C++ before any I/O:

1. Paths must be **relative** — joined with the initialized base path
2. `..` components are **rejected** (no directory traversal)
3. Symlinks are **rejected** (`lstat` check, not `stat`)
4. Final resolved path must start with base path prefix
5. Path length capped at 256 characters

```
Base: /data/data/com.dark.tool_neuron/files/

Valid:   "ums/chats.ums"     → /data/.../files/ums/chats.ums  ✓
Invalid: "../shared_prefs"   → rejected (traversal)           ✗
Invalid: "/etc/passwd"       → rejected (absolute)            ✗
```

## Permissions

| Resource | Mode | Notes |
|---|---|---|
| Base directory | `0700` | Owner (app) only, set on `nativeInit` |
| Subdirectories | `0700` | Created with `mkdir()` + `chmod()` |
| Data files | `0600` | Set after every write |
| Temp files | `0600` | Used during atomic writes |

## Atomic Write Flow

```
1. Write data to "path.tmp" (new file, 0600)
2. fsync("path.tmp")         — ensure data on disk
3. rename("path.tmp", "path") — atomic replace (POSIX guarantee)
4. fsync(parent_directory)    — ensure directory entry on disk
```

If crash occurs at any point: either old data intact (step 1-2 crash) or new data intact (step 3+ crash). Never partial.

## Dependencies

- Android NDK (C++17)
- POSIX APIs only (no external libraries)
- No dependency on system_encryptor or ums (lowest layer in the stack)

## Module Dependency Graph

```
file_ops  ←── ums (uses for all disk I/O)
          ←── app (future: any direct file needs)
```
