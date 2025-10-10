package com.dark.neuroverse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronNode
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.ntds.neuron_tree.NodeType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// If you have rDP/rSp helpers in theme, feel free to swap fixed dp with rDP(...)

// ——————————————————————————————————————————————————————————————
// FILTERS & SORT
// ——————————————————————————————————————————————————————————————

enum class SortKey { CHATS, MESSAGES, LAST_ACTIVE }
enum class RoleScope { ALL, USER, ASSISTANT, TOOL }

data class UiFilter(
    val query: String = "",
    val sort: SortKey = SortKey.LAST_ACTIVE,
    val roleScope: RoleScope = RoleScope.ALL,
    val onlyChats: Boolean = false,
)

// ——————————————————————————————————————————————————————————————
// ENTRY
// ——————————————————————————————————————————————————————————————

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NeuronTreeScreen(
    modifier: Modifier = Modifier,
    tree: NeuronTree,
    onOpenChat: (ChatSession) -> Unit = {},
) {
    val sessions = remember(tree) { extractChatSessions(tree) }
    val stats = remember(sessions) { ChatStats.from(sessions) }
    var filter by remember { mutableStateOf(UiFilter()) }

    Column(modifier.fillMaxSize()) {
        TopBar()
        QuickLooks(
            stats = stats,
            filter = filter,
            onFilterChange = { filter = it }
        )

        // (Optional) Heatmap spot — keep your impl if you have one
        ContributionHeatmap(
            countsByDate = stats.countsByDate,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .height(84.dp)
                .fillMaxWidth()
        )

        Divider(Modifier.padding(vertical = 6.dp))

        val visibleSessions = remember(filter, sessions) {
            sessions
                .asSequence()
                .filter { s ->
                    val q = filter.query.trim().lowercase()
                    if (q.isBlank()) true else (s.title?.lowercase()?.contains(q) == true ||
                            s.messages.any { it.text.lowercase().contains(q) })
                }
                .filter { s ->
                    when (filter.roleScope) {
                        RoleScope.ALL -> true
                        RoleScope.USER -> s.messages.any { it.role.equals("user", true) }
                        RoleScope.ASSISTANT -> s.messages.any { it.role.equals("assistant", true) }
                        RoleScope.TOOL -> s.messages.any { it.role.equals("tool", true) }
                    }
                }
                .let { seq ->
                    when (filter.sort) {
                        SortKey.LAST_ACTIVE -> seq.sortedByDescending {
                            it.messages.maxOfOrNull { m ->
                                m.timestamp ?: 0L
                            } ?: 0L
                        }

                        SortKey.MESSAGES -> seq.sortedByDescending { it.messages.size }
                        SortKey.CHATS -> seq.sortedBy { it.title ?: "" }
                    }
                }
                .toList()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            stickyHeader {
                SectionHeader(title = "Your Brain Map")
            }

            // Tree view
            item { NodeBranch(node = tree.root, depth = 0) }

            // Chats
            if (visibleSessions.isNotEmpty()) {
                stickyHeader { SubHeader(title = "Recent Chats (${visibleSessions.size})") }
                items(visibleSessions, key = { it.title ?: it.hashCode().toString() }) { s ->
                    ChatSessionCard(session = s, onOpen = { onOpenChat(s) })
                }
            }
        }
    }
}

// ——————————————————————————————————————————————————————————————
// TOP BAR + QUICK LOOKS
// ——————————————————————————————————————————————————————————————

@Composable
private fun TopBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Your Brain Map",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickLooks(stats: ChatStats, filter: UiFilter, onFilterChange: (UiFilter) -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(22.dp)
            )
            .padding(14.dp)
    ) {
        Text("Quick Looks", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        // Stats row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(
                modifier = Modifier.weight(1f),
                "Chats",
                stats.totalChats,
                selected = filter.roleScope == RoleScope.ALL
            ) {
                onFilterChange(filter.copy(roleScope = RoleScope.ALL))
            }
            StatPill(
                modifier = Modifier.weight(1f),
                "Messages",
                stats.totalMessages,
                selected = false
            ) {}
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(
                modifier = Modifier.weight(1f),
                "Users",
                stats.roleCounts["user"] ?: 0,
                selected = filter.roleScope == RoleScope.USER
            ) {
                onFilterChange(filter.copy(roleScope = RoleScope.USER))
            }
            StatPill(
                modifier = Modifier.weight(1f),
                "Assistants",
                stats.roleCounts["assistant"] ?: 0,
                selected = filter.roleScope == RoleScope.ASSISTANT
            ) {
                onFilterChange(filter.copy(roleScope = RoleScope.ASSISTANT))
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(
                modifier = Modifier.weight(1f),
                "Tool-Call",
                stats.roleCounts["tool"] ?: 0,
                selected = filter.roleScope == RoleScope.TOOL
            ) {
                onFilterChange(filter.copy(roleScope = RoleScope.TOOL))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Search + sort chips
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilterChange(filter.copy(query = it)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search titles & messages") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.sort == SortKey.LAST_ACTIVE,
                onClick = { onFilterChange(filter.copy(sort = SortKey.LAST_ACTIVE)) },
                label = { Text("Last Active") },
                leadingIcon = { Icon(Icons.Default.AccessTime, null) }
            )
            FilterChip(
                selected = filter.sort == SortKey.MESSAGES,
                onClick = { onFilterChange(filter.copy(sort = SortKey.MESSAGES)) },
                label = { Text("Messages") },
                leadingIcon = { Icon(Icons.Default.ListAlt, null) }
            )
            FilterChip(
                selected = filter.sort == SortKey.CHATS,
                onClick = { onFilterChange(filter.copy(sort = SortKey.CHATS)) },
                label = { Text("A–Z") },
                leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }
            )
        }
    }
}

@Composable
private fun StatPill(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.5f
        ),
        tonalElevation = if (selected) 1.dp else 0.dp,
        border = ButtonDefaults.outlinedButtonBorder,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "%02d".format(value),
                style = MaterialTheme.typography.labelMedium,
                color = outline
            )
        }
    }
}

// ——————————————————————————————————————————————————————————————
// LIST HEADERS
// ——————————————————————————————————————————————————————————————

@Composable
private fun SectionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.weight(1f))
            AssistChip(
                onClick = { /* TODO: scroll to root */ },
                label = { Text("Root") },
                leadingIcon = { Icon(Icons.Default.Home, null) })
        }
    }
}

@Composable
private fun SubHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ——————————————————————————————————————————————————————————————
// TREE BRANCH
// ——————————————————————————————————————————————————————————————

@Composable
private fun NodeBranch(node: NeuronNode, depth: Int) {
    var expanded by remember { mutableStateOf(true) }
    val bg = when (node.data.type) {
        NodeType.ROOT -> MaterialTheme.colorScheme.primaryContainer
        NodeType.OPERATOR -> MaterialTheme.colorScheme.secondaryContainer
        NodeType.HOLDER -> MaterialTheme.colorScheme.tertiaryContainer
        NodeType.STEAM -> MaterialTheme.colorScheme.surfaceVariant
        NodeType.LEAF -> MaterialTheme.colorScheme.surface
    }

    Column(Modifier.padding(start = (depth * 18).dp, bottom = 8.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "[${node.data.type}] ${node.id}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (node.data.content.isNotBlank()) {
                        Text(
                            node.data.content.take(120),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (node.data.type == NodeType.LEAF && looksLikeChatJson(node.data.content)) {
                parseChatSession(node.data.content)?.let { ChatSessionCard(session = it) }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column { node.getChildNodes().forEach { NodeBranch(it, depth + 1) } }
        }
    }
}

// ——————————————————————————————————————————————————————————————
// CHAT CARD (with spoiler/blur reveal)
// ——————————————————————————————————————————————————————————————

@Composable
private fun ChatSessionCard(session: ChatSession, onOpen: () -> Unit = {}) {
    val msgs = session.messages
    val total = msgs.size
    val roleCounts = remember(msgs) { msgs.groupingBy { it.role.lowercase() }.eachCount() }
    val lastTs = remember(msgs) { msgs.maxOfOrNull { it.timestamp ?: 0L } }
    var hidden by remember { mutableStateOf(true) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp)
    ) {
        // Header gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.title?.ifBlank { "Untitled chat" } ?: "Untitled chat",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(text = "$total msgs")
                    Spacer(Modifier.width(6.dp))
                    Pill(text = "U ${roleCounts["user"] ?: 0}")
                    Pill(text = "A ${roleCounts["assistant"] ?: 0}")
                    Pill(text = "T ${roleCounts["tool"] ?: 0}")
                    Spacer(Modifier.weight(1f))
                    lastTs?.let {
                        Text(
                            "Last • ${prettyDate(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                RoleDotRow(messages = msgs)
            }
        }

        // Body: blurred preview when hidden
        Box(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                Modifier
                    .matchParentSize()
                    .alpha(if (hidden) 0.5f else 1f)
                    .blur(if (hidden) 7.dp else 0.dp)
            ) {
                msgs.take(4).forEach { msg ->
                    Bubble(msg)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (hidden) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(onClick = { hidden = false }) {
                        Icon(Icons.Default.Visibility, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Show Messages")
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hidden) "Blurred for privacy" else "Tap Open for full thread",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { hidden = !hidden }) {
                    Icon(
                        if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (hidden) "Reveal" else "Hide")
                }
                Button(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val isUser = msg.role.equals("user", true)
    val isAssistant = msg.role.equals("assistant", true)
    val isTool = msg.role.equals("tool", true)

    val bg = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isAssistant -> MaterialTheme.colorScheme.tertiaryContainer
        isTool -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val border = when {
        isUser -> MaterialTheme.colorScheme.primary
        isAssistant -> MaterialTheme.colorScheme.tertiary
        isTool -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }.copy(alpha = 0.35f)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(Modifier.fillMaxWidth(if (isUser) 0.82f else 0.90f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        isUser -> Icons.Default.Person
                        isAssistant -> Icons.Default.SmartToy
                        isTool -> Icons.Default.Build
                        else -> Icons.Default.Chat
                    },
                    contentDescription = null,
                    tint = border
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    msg.role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = bg,
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                border = ButtonDefaults.outlinedButtonBorder,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun RoleDotRow(messages: List<ChatMessage>, maxDots: Int = 42) {
    if (messages.isEmpty()) return
    val recent = messages.takeLast(maxDots)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        recent.forEachIndexed { idx, m ->
            val alpha = (0.35f + (idx + 1) / recent.size.toFloat() * 0.65f).coerceIn(0.35f, 1f)
            val color = when (m.role.lowercase()) {
                "user" -> MaterialTheme.colorScheme.primary
                "assistant" -> MaterialTheme.colorScheme.tertiary
                "tool" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outline
            }.copy(alpha = alpha)

            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ——————————————————————————————————————————————————————————————
// DATA + STATS + PARSERS
// ——————————————————————————————————————————————————————————————

data class ChatMessage(
    val id: String?,
    val role: String,
    val text: String,
    val timestamp: Long?
)

data class ChatSession(
    val title: String?,
    val messages: List<ChatMessage>,
    val createdAt: Long?
)

private fun looksLikeChatJson(s: String): Boolean {
    val t = s.trim()
    return t.startsWith("{") && t.contains("\"conversations\"")
}

private fun parseChatSession(raw: String): ChatSession? = runCatching {
    val obj = JSONObject(raw)
    val title = obj.optString("title").takeIf { it.isNotBlank() }
    val createdAt = obj.optLong("createdAt")

    val arr = obj.optJSONArray("conversations") ?: JSONArray()
    val msgs = buildList {
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            add(
                ChatMessage(
                    id = m.optString("id").takeIf { it.isNotBlank() },
                    role = m.optString("role", "unknown"),
                    text = m.optString("text", "").trim(),
                    timestamp = m.optLong("ts", 0L).takeIf { it != 0L }
                        ?: m.optLong("timestamp", 0L).takeIf { it != 0L }
                        ?: m.optString("time", null)?.let { parseIsoToMillis(it) }
                        ?: m.optString("date", null)?.let { parseIsoToMillis(it) }
                )
            )
        }
    }
    ChatSession(title, msgs, createdAt)
}.getOrNull()

private fun parseIsoToMillis(s: String): Long? =
    runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()

private data class ChatStats(
    val totalChats: Int,
    val totalMessages: Int,
    val roleCounts: Map<String, Int>,
    val countsByDate: Map<LocalDate, Int>
) {
    companion object {
        fun from(sessions: List<ChatSession>): ChatStats {
            val totalMsgs = sessions.sumOf { it.messages.size }
            val roles = mutableMapOf<String, Int>()
            val byDate = mutableMapOf<LocalDate, Int>()
            sessions.forEach { s ->
                s.messages.forEach { m ->
                    roles[m.role.lowercase()] = (roles[m.role.lowercase()] ?: 0) + 1
                    m.timestamp?.let { ts ->
                        val d =
                            Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
                        byDate[d] = (byDate[d] ?: 0) + 1
                    }
                }
            }
            return ChatStats(
                totalChats = sessions.size,
                totalMessages = totalMsgs,
                roleCounts = roles,
                countsByDate = byDate
            )
        }
    }
}

private fun extractChatSessions(tree: NeuronTree): List<ChatSession> {
    val list = mutableListOf<ChatSession>()
    fun dfs(n: NeuronNode) {
        if (n.data.type == NodeType.LEAF && looksLikeChatJson(n.data.content)) {
            parseChatSession(n.data.content)?.let { list.add(it) }
        }
        n.getChildNodes().forEach(::dfs)
    }
    dfs(tree.root)
    return list
}

@Composable
private fun ContributionHeatmap(
    countsByDate: Map<LocalDate, Int>, modifier: Modifier = Modifier, weeks: Int = 18
) {
    // Optional stub so screen compiles. Replace with your real heatmap.
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {}
}

@Composable
private fun prettyDate(ts: Long): String = try {
    val dt = java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
    dt.format(DateTimeFormatter.ofPattern("dd MMM, h:mm a"))
} catch (_: Throwable) {
    ""
}