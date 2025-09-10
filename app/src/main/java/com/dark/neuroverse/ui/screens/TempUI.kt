package com.dark.neuroverse.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.userdata.ntds.neuron_tree.NeuronNode
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.ntds.neuron_tree.NodeType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

// ---------- TOP-LEVEL SCREEN ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuronTreeScreen(modifier: Modifier = Modifier, tree: NeuronTree) {
    val sessions = remember(tree) { extractChatSessions(tree) }
    val stats = remember(sessions) { ChatStats.from(sessions) }

    Column(modifier.fillMaxSize()) {
        // Quick Stats + Heatmap
        StatsRow(stats = stats)

        ContributionHeatmap(
            countsByDate = stats.countsByDate,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(84.dp)
                .fillMaxWidth()
        )

        HorizontalDivider(
            Modifier.padding(vertical = 4.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )

        // Explorer
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)
        ) {
            item {
                NodeBranch(
                    node = tree.root, depth = 0
                )
            }
        }
    }
}

// ---------- TREE: EXPANDABLE BRANCHES (INFINITE DEPTH) ----------

@Composable
private fun NodeBranch(node: NeuronNode, depth: Int) {
    var expanded by remember { mutableStateOf(true) }
    val chevronAlpha by animateFloatAsState(if (expanded) 1f else 0.6f, label = "chev")

    val bgColor = when (node.data.type) {
        NodeType.ROOT -> MaterialTheme.colorScheme.primaryContainer
        NodeType.OPERATOR -> MaterialTheme.colorScheme.secondaryContainer
        NodeType.HOLDER -> MaterialTheme.colorScheme.tertiaryContainer
        NodeType.STEAM -> MaterialTheme.colorScheme.surfaceVariant
        NodeType.LEAF -> MaterialTheme.colorScheme.surface
    }
    val lineColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .padding(start = (depth * 20).dp, bottom = 8.dp)
            .drawBehind {
                // vertical guideline for hierarchy
                if (depth > 0) {
                    val stroke = 6f
                    val dotted = PathEffect.dashPathEffect(floatArrayOf(stroke, stroke * 4)) // dot,gap

                    drawLine(
                        color = lineColor,
                        start = Offset(-12f, 50f),
                        end = Offset(-12f, size.height - 100f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = dotted
                    )
                }
            }) {
        // Node header
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // bullet dot
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
                )

                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "[${node.data.type}] ${node.id}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (node.data.content.isNotBlank()) {
                        Text(
                            text = node.data.content.take(120),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.alpha(chevronAlpha)
                )
            }

            // If this node is a chat session JSON → pretty render inline
            if (node.data.type == NodeType.LEAF && looksLikeChatJson(node.data.content)) {
                ChatSessionPreview(node.data.content)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Children
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                node.getChildNodes().forEach { child ->
                    NodeBranch(child, depth + 1)
                }
            }
        }
    }
}

// ---------- CHAT SESSION RENDER ----------

@Composable
private fun ChatSessionPreview(
    json: String,
    onOpen: () -> Unit = {}
) {
    val session = remember(json) { parseChatSession(json) } ?: return
    val msgs = session.messages
    val total = msgs.size
    val roleCounts = remember(msgs) {
        msgs.groupingBy { it.role.lowercase() }.eachCount()
    }
    val lastTs = remember(msgs) { msgs.mapNotNull { it.timestamp }.maxOrNull() }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp)
    ) {
        // header — gradient, title, quick chips
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.title?.ifBlank { "Untitled chat" } ?: "Untitled chat",
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
                            text = "Last • " + prettyDate(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                RoleDotRow(messages = msgs) // github-y dot strip
            }
        }

        // preview bubbles
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            msgs.take(4).forEach { msg ->
                CoolBubble(msg)
                Spacer(Modifier.height(8.dp))
            }
        }

        // footer actions
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap to open full thread",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Open")
            }
        }
    }
}


@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role.equals("user", ignoreCase = true)
    val isAssistant = msg.role.equals("assistant", ignoreCase = true)
    val isTool = msg.role.equals("tool", ignoreCase = true)

    val bg = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isAssistant -> MaterialTheme.colorScheme.tertiaryContainer
        isTool -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.fillMaxWidth(if (isUser) 0.82f else 0.9f) // width cap
        ) {
            Text(
                text = msg.role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = bg, shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

// ---------- QUICK STATS + HEATMAP ----------

@Composable
private fun StatsRow(stats: ChatStats) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatChip(Modifier.weight(1f),"Chats", stats.totalChats)
        StatChip(Modifier.weight(1f),"Messages", stats.totalMessages)
        StatChip(Modifier.weight(1f),"User", stats.roleCounts["user"] ?: 0)
        StatChip(Modifier.weight(1f),"Assistant", stats.roleCounts["assistant"] ?: 0)
        StatChip(Modifier.weight(1f),"Tool", stats.roleCounts["tool"] ?: 0)
    }
}

@Composable
private fun StatChip(modifier: Modifier = Modifier, label: String, value: Int) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * GitHub-like contribution heatmap.
 * - Expects counts grouped by LocalDate.
 * - Renders last N weeks (default 18 cols).
 * - If no dates (timestamps missing), renders nothing.
 */
@Composable
private fun ContributionHeatmap(
    countsByDate: Map<LocalDate, Int>, modifier: Modifier = Modifier, weeks: Int = 18
) {
    if (countsByDate.isEmpty()) return

    val today = LocalDate.now()
    val start = today.minusWeeks(weeks.toLong())
        .minusDays((today.dayOfWeek.value % 7).toLong()) // align to Sun

    val dates = buildList {
        var d = start
        while (!d.isAfter(today)) {
            add(d)
            d = d.plusDays(1)
        }
    }

    val maxCount = (countsByDate.values.maxOrNull() ?: 1).coerceAtLeast(1)

    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // columns are weeks
        val cols = dates.chunked(7)
        cols.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val c = countsByDate[day] ?: 0
                    val alpha = (c.toFloat() / maxCount).coerceIn(0.12f, 1f)
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

// ---------- DATA PARSING + STATS ----------

private fun looksLikeChatJson(s: String): Boolean {
    val t = s.trim()
    return t.startsWith("{") && t.contains("\"conversations\"")
}

private data class ChatMessage(
    val id: String?,
    val role: String,
    val text: String,
    val timestamp: Long? // epoch millis (optional)
)

private data class ChatSession(
    val title: String?,
    val messages: List<ChatMessage>,
    val createdAt: Long? // optional top-level timestamp if present
)

private fun parseChatSession(raw: String): ChatSession? = runCatching {
    val obj = JSONObject(raw)
    val title = obj.optString("title").takeIf { it.isNotBlank() }
    val createdAt = obj.optLongOrNull("createdAt")

    val arr = obj.optJSONArray("conversations") ?: JSONArray()
    val msgs = buildList {
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            add(
                ChatMessage(
                    id = m.optString("id").takeIf { it.isNotBlank() },
                    role = m.optString("role", "unknown"),
                    text = m.optString("text", "").trim(),
                    timestamp = m.optLongOrNull("ts") ?: m.optLongOrNull("timestamp")
                    ?: m.optStringOrNull("time")?.let { parseIsoToMillis(it) } ?: m.optStringOrNull(
                        "date"
                    )?.let { parseIsoToMillis(it) })
            )
        }
    }
    ChatSession(title, msgs, createdAt)
}.getOrNull()

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key)) runCatching { getLong(key) }.getOrNull() else null

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key)) runCatching { getString(key) }.getOrNull()?.takeIf { it.isNotBlank() } else null

@SuppressLint("NewApi")
private fun parseIsoToMillis(s: String): Long? = runCatching {
    // supports ISO-8601 like "2025-09-10T23:12:31.564Z" or without Z
    Instant.parse(s).toEpochMilli()
}.getOrNull()

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
                            LocalDate.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
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

// walk the real tree and pull chat sessions from chatHistory subtree (LEAF nodes with JSON)
private fun extractChatSessions(tree: NeuronTree): List<ChatSession> {
    val root = tree.root
    val list = mutableListOf<ChatSession>()
    fun dfs(n: NeuronNode) {
        if (n.data.type == NodeType.LEAF && looksLikeChatJson(n.data.content)) {
            parseChatSession(n.data.content)?.let { list.add(it) }
        }
        n.getChildNodes().forEach(::dfs)
    }
    dfs(root)
    return list
}

// ---------- OPTIONAL: SIMPLE ROLE BAR (if you want) ----------

@Composable
private fun RoleBars(stats: ChatStats, modifier: Modifier = Modifier) {
    val total = max(1, stats.totalMessages)
    Row(
        modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val user = (stats.roleCounts["user"] ?: 0).toFloat() / total
        val assistant = (stats.roleCounts["assistant"] ?: 0).toFloat() / total
        val tool = (stats.roleCounts["tool"] ?: 0).toFloat() / total

        Box(
            Modifier
                .weight(user)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        Box(
            Modifier
                .weight(assistant)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
        )
        Box(
            Modifier
                .weight(tool)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )
    }
}


@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun RoleDotRow(messages: List<ChatMessage>, maxDots: Int = 42) {
    if (messages.isEmpty()) return
    val recent = messages.takeLast(maxDots)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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

@Composable
private fun CoolBubble(msg: ChatMessage) {
    val isUser = msg.role.equals("user", ignoreCase = true)
    val isAssistant = msg.role.equals("assistant", ignoreCase = true)
    val isTool = msg.role.equals("tool", ignoreCase = true)

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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .fillMaxWidth(if (isUser) 0.80f else 0.88f)
        ) {
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
                    text = msg.role,
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
                border = BorderStroke(1.dp, border),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

private fun prettyDate(ts: Long): String {
    return try {
        val dt = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(ts),
            java.time.ZoneId.systemDefault()
        )
        dt.format(DateTimeFormatter.ofPattern("dd MMM, h:mm a"))
    } catch (_: Throwable) {
        ""
    }
}
