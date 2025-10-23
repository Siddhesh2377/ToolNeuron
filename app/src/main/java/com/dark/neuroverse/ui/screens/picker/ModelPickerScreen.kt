package com.dark.neuroverse.ui.screens.picker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.dark.neuroverse.ui.theme.rDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    finishWithPath: (String) -> Unit, onClose: () -> Unit
) {
    val context = LocalContext.current
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }

    var hasAllFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    var currentPath by rememberSaveable { mutableStateOf(rootPath) }
    var listState by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var detailTarget by remember { mutableStateOf<FileItem?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(hasAllFiles, currentPath) {
        if (hasAllFiles) {
            loading = true
            error = null
            listState = try {
                withContext(Dispatchers.IO) { listChildrenFiltered(currentPath) }
            } catch (t: Throwable) {
                error = t.message
                emptyList()
            } finally {
                loading = false
            }
        }
    }

    // Back press goes up a directory; exits at root
    BackHandler(true) {
        if (currentPath != rootPath) {
            currentPath = File(currentPath).parentFile?.absolutePath ?: rootPath
        } else onClose()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified
            ), navigationIcon = {
                IconButton(onClick = {
                    if (currentPath != rootPath) {
                        currentPath = File(currentPath).parentFile?.absolutePath ?: rootPath
                    } else onClose()
                }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            }, title = { PathBreadcrumbText(currentPath) }, actions = {
                if (!hasAllFiles) {
                    TextButton(onClick = { openAllFilesAccessSettings(context) }) { Text("Grant access") }
                } else {
                    IconButton(onClick = { currentPath = rootPath }) {
                        Icon(Icons.Outlined.Home, contentDescription = "Reset to root")
                    }
                }
            }, scrollBehavior = scrollBehavior
            )
        }) { inner ->
        val blurRadius = if (detailTarget != null) 12.dp else 0.dp
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            when {
                !hasAllFiles -> PermGate(
                    modifier = Modifier.fillMaxSize()
                ) { hasAllFiles = hasAllFilesAccess() }

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            hasAllFiles = hasAllFilesAccess()
                        }) { Text("Retry") }
                    }
                }

                else -> FileList(
                    modifier = Modifier.fillMaxSize(),
                    items = listState,
                    rootPath = rootPath,
                    currentPath = currentPath,
                    onNavigate = { folder -> currentPath = folder.absolutePath },
                    onPick = { file -> finishWithPath(file.absolutePath) },
                    onLongPress = { detailTarget = it })
            }
        }

        if (detailTarget != null) {
            FileDetailDialog(
                item = detailTarget!!,
                onDismiss = { detailTarget = null },
                onSelect = { finishWithPath(detailTarget!!.file.absolutePath) })
        }
    }
}

@Composable
private fun PathBreadcrumbText(path: String) {
    val scroll = rememberScrollState()
    val normalized = remember(path) { path.removeSuffix("/") }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(normalized) {
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PermGate(modifier: Modifier = Modifier, onCheck: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "Storage access needed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text("To browse folders and pick .gguf models from /storage/emulated/0, allow \"All files access\" for this app.")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { openAllFilesAccessSettings(ctx) }) { Text("Open Settings") }
            OutlinedButton(onClick = { onCheck() }) { Text("I granted it") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    modifier: Modifier,
    items: List<FileItem>,
    rootPath: String,
    currentPath: String,
    onNavigate: (File) -> Unit,
    onPick: (File) -> Unit,
    onLongPress: (FileItem) -> Unit
) {
    val parent = File(currentPath).parentFile
    LazyColumn(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (parent != null && currentPath != rootPath) {
            item("..parent") {
                ListRow(
                    icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    title = "..",
                    subtitle = parent.absolutePath,
                    onClick = { onNavigate(parent) },
                    onLongClick = null
                )
            }
        }
        items(items, key = { it.file.absolutePath }) { item ->
            val isDir = item.isDir
            val subtitle = if (isDir) "Folder" else humanSize(item.size)
            ListRow(
                icon = {
                if (isDir) Icon(Icons.Outlined.Folder, contentDescription = null)
                else Icon(Icons.Filled.SmartToy, contentDescription = null)
            },
                title = item.name,
                subtitle = subtitle,
                trailing = if (!isDir) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.clickable {
                                    onLongPress(item)
                                })
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }

                    }
                } else null,
                onClick = { if (isDir) onNavigate(item.file) else onPick(item.file) },
                onLongClick = if (!isDir) {
                    { onLongPress(item) }
                } else null)
        }
    }
}

@Composable
private fun ListRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box { trailing() }
        }
    }
}

@Composable
private fun FileDetailDialog(
    item: FileItem, onDismiss: () -> Unit, onSelect: () -> Unit
) {
    var quickSha by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.file) {
        // quick 4MB SHA-256 fingerprint so the UI stays snappy
        quickSha = computeSha256Prefix(item.file, limitBytes = 4L * 1024 * 1024)
    }

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(rDP(20.dp)), verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
            ) {
                Text(
                    "Model details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                // Basic facts
                InfoRow("Name", item.name)
                InfoRow("Size", "${humanSize(item.size)} (${item.size} B)")
                InfoRow("Readable / Writable", "${item.file.canRead()} / ${item.file.canWrite()}")
                guessQuant(item.name)?.let { InfoRow("Quant", it) }
                quickSha?.let { InfoRow("SHA-256 (first 4MB)", it) }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                Spacer(Modifier.height(rDP(6.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                    Button(onClick = onSelect) { Text("Load") }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            k,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(
            v,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class FileItem(
    val file: File, val name: String, val isDir: Boolean, val size: Long, val lastModified: Long
)

private suspend fun listChildrenFiltered(path: String): List<FileItem> =
    withContext(Dispatchers.IO) {
        val dir = File(path)
        val children = dir.listFiles()?.toList().orEmpty()
        return@withContext children.filter {
            it.isDirectory || it.extension.equals(
                "gguf", ignoreCase = true
            )
        }.sortedWith(
            compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) })
        ).map {
            FileItem(
                file = it,
                name = it.name,
                isDir = it.isDirectory,
                size = if (it.isFile) it.length() else 0L,
                lastModified = it.lastModified()
            )
        }
    }

private fun hasAllFilesAccess(): Boolean {
    return Environment.isExternalStorageManager()
}

private fun openAllFilesAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = "package:${context.packageName}".toUri()
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return String.format(
        Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(group.toDouble()), units[group]
    )
}

private fun guessQuant(name: String): String? {
    // naive guess e.g., Q4_K_M, Q5_0, Q8_0 etc.
    val rx = Regex("(?i)Q\\d+[_A-Z0-9]*")
    return rx.find(name)?.value
}

private suspend fun computeSha256Prefix(file: File, limitBytes: Long): String? =
    withContext(Dispatchers.IO) {
        return@withContext try {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            var remaining = limitBytes
            FileInputStream(file).use { fis ->
                while (remaining > 0) {
                    val toRead = min(buf.size.toLong(), remaining).toInt()
                    val len = fis.read(buf, 0, toRead)
                    if (len <= 0) break
                    md.update(buf, 0, len)
                    remaining -= len
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }