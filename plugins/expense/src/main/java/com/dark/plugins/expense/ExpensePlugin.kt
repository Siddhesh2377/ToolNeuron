package com.dark.plugins.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.dark.plugin_api.Plugin
import com.dark.plugin_api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExpensePlugin : Plugin {

    private lateinit var ctx: PluginContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val expenses: SnapshotStateList<Expense> = mutableStateListOf()
    private var loaded = mutableStateOf(false)
    private var ready = mutableStateOf(false)

    private var store: ExpenseStore? = null
    private var categorizer: Categorizer? = null
    private var embedder: Embedder? = null

    override fun onLoad(context: PluginContext) {
        ctx = context
        val store = ExpenseStore(ctx.hxs)
        this.store = store

        scope.launch {
            val items = withContext(Dispatchers.IO) { store.list() }
            expenses.clear()
            expenses.addAll(items)
            loaded.value = true
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val pluginDir = File(ctx.appContext.filesDir, "plugins/${ctx.pluginId}")
                    val modelPath = File(pluginDir, "model.onnx").absolutePath
                    val vocabFile = File(pluginDir, "vocab.txt")
                    val tokenizer = WordPieceTokenizer.fromVocabFile(vocabFile)
                    val emb = Embedder.create(ctx.onnx, modelPath, tokenizer)
                    val cat = Categorizer(emb, ctx.hxs)
                    cat.ensureCentroids()
                    embedder = emb
                    categorizer = cat
                }
                ready.value = true
            }
        }
    }

    override fun onStart() {}
    override fun onPause() {}
    override fun onUnload() {
        runCatching { embedder?.close() }
        embedder = null
        categorizer = null
        scope.cancel()
    }

    @Composable
    override fun Content() {
        ExpenseTheme {
            ExpenseApp(
                expenses = expenses,
                loaded = loaded.value,
                ready = ready.value,
                onSuggest = { text -> categorizer?.rank(text) ?: emptyList() },
                onSave = { description, amount, categoryId, note ->
                    saveExpense(description, amount, categoryId, note)
                },
                onDelete = { expense -> deleteExpense(expense) },
            )
        }
    }

    private fun saveExpense(description: String, amount: Double, categoryId: String, note: String) {
        val expense = Expense(
            id = ExpenseStore.newId(),
            timestamp = System.currentTimeMillis(),
            description = description.trim(),
            amount = amount,
            categoryId = categoryId,
            note = note.trim(),
        )
        expenses.add(0, expense)
        val store = this.store ?: return
        scope.launch(Dispatchers.IO) { store.save(expense) }
    }

    private fun deleteExpense(expense: Expense) {
        expenses.removeAll { it.id == expense.id && it.timestamp == expense.timestamp }
        val store = this.store ?: return
        scope.launch(Dispatchers.IO) { store.delete(expense) }
    }
}
