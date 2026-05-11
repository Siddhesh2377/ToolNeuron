package com.dark.plugins.expense

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme as M3
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Screen { List, Add }

private val amountRegex = Regex("""\$?\s*(\d+(?:[.,]\d{1,2})?)""")

@Composable
internal fun ExpenseApp(
    expenses: List<Expense>,
    loaded: Boolean,
    ready: Boolean,
    onSuggest: suspend (String) -> List<CategoryScore>,
    onSave: (description: String, amount: Double, categoryId: String, note: String) -> Unit,
    onDelete: (Expense) -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.List) }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "expense-nav",
    ) { current ->
        when (current) {
            Screen.List -> ListScreen(
                expenses = expenses,
                loaded = loaded,
                ready = ready,
                onAdd = { screen = Screen.Add },
                onDelete = onDelete,
            )
            Screen.Add -> AddScreen(
                ready = ready,
                onSuggest = onSuggest,
                onCancel = { screen = Screen.List },
                onSave = { description, amount, categoryId, note ->
                    onSave(description, amount, categoryId, note)
                    screen = Screen.List
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    expenses: List<Expense>,
    loaded: Boolean,
    ready: Boolean,
    onAdd: () -> Unit,
    onDelete: (Expense) -> Unit,
) {
    val monthly = remember(expenses) { monthlyBreakdown(expenses) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Expenses",
                            style = M3.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (ready) "${expenses.size} tracked" else "Preparing AI…",
                            style = M3.typography.labelSmall,
                            color = M3.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = M3.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = M3.colorScheme.primary,
                contentColor = M3.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("+ New expense", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = M3.colorScheme.background,
    ) { inner ->
        if (!loaded) {
            CenteredText(inner, "Loading…")
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            MonthSummaryCard(monthly)
            if (expenses.isEmpty()) {
                EmptyExpenseState()
            } else {
                ExpenseList(expenses = expenses, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(summary: MonthlySummary) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = M3.colorScheme.primaryContainer,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "This month",
                style = M3.typography.labelMedium,
                color = M3.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatAmount(summary.total),
                style = M3.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = M3.colorScheme.onPrimaryContainer,
            )
            if (summary.byCategory.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (entry in summary.byCategory.take(6)) {
                        CategoryChip(entry.category, formatAmount(entry.total))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: CategoryDef, amount: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = M3.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(category.emoji, fontSize = 14.sp)
            Text(
                category.label,
                style = M3.typography.labelMedium,
                color = M3.colorScheme.onSurface,
            )
            Text(
                amount,
                style = M3.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = M3.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyExpenseState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(M3.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("💰", fontSize = 36.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "No expenses yet",
            style = M3.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = M3.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + New expense to track your first purchase.",
            style = M3.typography.bodyMedium,
            color = M3.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpenseList(expenses: List<Expense>, onDelete: (Expense) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(expenses, key = { it.timestamp.toString() + it.id }) { expense ->
            ExpenseRow(expense = expense, onDelete = { onDelete(expense) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onDelete: () -> Unit) {
    val category = Categories.byId(expense.categoryId)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = M3.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(M3.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(category.emoji, fontSize = 18.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description.ifBlank { category.label },
                    style = M3.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = M3.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${category.label} • ${formatDate(expense.timestamp)}",
                    style = M3.typography.labelSmall,
                    color = M3.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatAmount(expense.amount),
                style = M3.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = M3.colorScheme.primary,
            )
            IconButton(onClick = onDelete) {
                Text("✕", color = M3.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScreen(
    ready: Boolean,
    onSuggest: suspend (String) -> List<CategoryScore>,
    onCancel: () -> Unit,
    onSave: (description: String, amount: Double, categoryId: String, note: String) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var amountTouched by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<CategoryScore>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var categorizing by remember { mutableStateOf(false) }

    LaunchedEffect(description) {
        if (!amountTouched) {
            val match = amountRegex.find(description)
            if (match != null) {
                val captured = match.groupValues[1].replace(',', '.')
                amountInput = captured
            }
        }
        if (ready && description.trim().length >= 3) {
            categorizing = true
            val ranked = runCatching { onSuggest(description) }.getOrDefault(emptyList())
            suggestions = ranked
            if (selectedCategory == null) selectedCategory = ranked.firstOrNull()?.category?.id
            categorizing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New expense",
                        style = M3.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = M3.colorScheme.surface),
            )
        },
        containerColor = M3.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabeledField(label = "Description") {
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = M3.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    decorationBox = { inner ->
                        if (description.isEmpty()) Text(
                            "e.g. Lunch at Subway \$12.50",
                            color = M3.colorScheme.onSurfaceVariant,
                        )
                        inner()
                    },
                )
            }
            LabeledField(label = "Amount") {
                BasicTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                        amountTouched = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        color = M3.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    decorationBox = { inner ->
                        if (amountInput.isEmpty()) Text(
                            "0.00",
                            color = M3.colorScheme.onSurfaceVariant,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        inner()
                    },
                )
            }
            CategorySuggestionSection(
                ready = ready,
                categorizing = categorizing,
                suggestions = suggestions,
                selectedId = selectedCategory,
                onSelect = { selectedCategory = it },
            )
            LabeledField(label = "Note (optional)") {
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    textStyle = TextStyle(color = M3.colorScheme.onSurface, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (note.isEmpty()) Text(
                            "Add a note",
                            color = M3.colorScheme.onSurfaceVariant,
                        )
                        inner()
                    },
                )
            }

            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
                val parsed = parseAmount(amountInput)
                val canSave = description.trim().isNotEmpty() && parsed != null && parsed > 0.0
                ExtendedFloatingActionButton(
                    onClick = {
                        if (parsed != null) {
                            onSave(
                                description,
                                parsed,
                                selectedCategory ?: Categories.byId("other").id,
                                note,
                            )
                        }
                    },
                    containerColor = if (canSave) M3.colorScheme.primary else M3.colorScheme.outlineVariant,
                    contentColor = if (canSave) M3.colorScheme.onPrimary else M3.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save expense", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CategorySuggestionSection(
    ready: Boolean,
    categorizing: Boolean,
    suggestions: List<CategoryScore>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Category",
                style = M3.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = M3.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            if (!ready) {
                Text(
                    "Loading AI…",
                    style = M3.typography.labelSmall,
                    color = M3.colorScheme.onSurfaceVariant,
                )
            } else if (categorizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = M3.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Categorizing…",
                    style = M3.typography.labelSmall,
                    color = M3.colorScheme.onSurfaceVariant,
                )
            } else if (suggestions.isNotEmpty()) {
                Text(
                    "Top suggestions",
                    style = M3.typography.labelSmall,
                    color = M3.colorScheme.onSurfaceVariant,
                )
            }
        }

        val top = suggestions.take(3)
        if (top.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (entry in top) {
                    SuggestionChip(
                        category = entry.category,
                        confidence = entry.score,
                        selected = entry.category.id == selectedId,
                        onClick = { onSelect(entry.category.id) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (cat in Categories.all) {
                MiniCategoryChip(
                    category = cat,
                    selected = cat.id == selectedId,
                    onClick = { onSelect(cat.id) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    category: CategoryDef,
    confidence: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) M3.colorScheme.primary else M3.colorScheme.surfaceContainerHigh
    val fg = if (selected) M3.colorScheme.onPrimary else M3.colorScheme.onSurface
    val barColor = if (selected) M3.colorScheme.onPrimary.copy(alpha = 0.4f) else M3.colorScheme.primary
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bg,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.emoji, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    category.label,
                    style = M3.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                )
            }
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) M3.colorScheme.onPrimary.copy(alpha = 0.18f) else M3.colorScheme.outlineVariant),
            ) {
                val pct = confidence.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(4.dp)
                        .background(barColor),
                )
            }
        }
    }
}

@Composable
private fun MiniCategoryChip(
    category: CategoryDef,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) M3.colorScheme.primaryContainer else M3.colorScheme.surfaceContainer
    val fg = if (selected) M3.colorScheme.onPrimaryContainer else M3.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(category.emoji, fontSize = 12.sp)
            Text(
                category.label,
                style = M3.typography.labelSmall,
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = M3.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = M3.colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = M3.colorScheme.surfaceContainer,
        ) {
            Box(modifier = Modifier.padding(14.dp)) { content() }
        }
    }
}

@Composable
private fun CenteredText(inner: PaddingValues, text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(inner),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = M3.colorScheme.onSurfaceVariant)
    }
}

internal data class MonthlySummary(
    val total: Double,
    val byCategory: List<CategoryTotal>,
)

internal data class CategoryTotal(
    val category: CategoryDef,
    val total: Double,
)

private fun monthlyBreakdown(expenses: List<Expense>): MonthlySummary {
    val cal = Calendar.getInstance()
    val now = System.currentTimeMillis()
    cal.timeInMillis = now
    val currentYear = cal.get(Calendar.YEAR)
    val currentMonth = cal.get(Calendar.MONTH)

    val totals = HashMap<String, Double>()
    var total = 0.0
    for (expense in expenses) {
        cal.timeInMillis = expense.timestamp
        if (cal.get(Calendar.YEAR) != currentYear) continue
        if (cal.get(Calendar.MONTH) != currentMonth) continue
        total += expense.amount
        totals[expense.categoryId] = (totals[expense.categoryId] ?: 0.0) + expense.amount
    }
    val ranked = totals.entries
        .map { (id, sum) -> CategoryTotal(Categories.byId(id), sum) }
        .sortedByDescending { it.total }
    return MonthlySummary(total, ranked)
}

private fun parseAmount(s: String): Double? {
    val normalized = s.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    return normalized.toDoubleOrNull()
}

private fun formatAmount(value: Double): String {
    return "$%.2f".format(Locale.US, value)
}

private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)

private fun formatDate(ts: Long): String = dateFormat.format(Date(ts))
