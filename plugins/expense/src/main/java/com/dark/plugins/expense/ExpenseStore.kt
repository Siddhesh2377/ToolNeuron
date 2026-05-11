package com.dark.plugins.expense

import com.dark.plugin_api.api.HxsApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
internal data class Expense(
    val id: String,
    val timestamp: Long,
    val description: String,
    val amount: Double,
    val categoryId: String,
    val note: String = "",
)

internal class ExpenseStore(private val hxs: HxsApi) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun list(): List<Expense> {
        val keys = runCatching { hxs.list(prefix = KEY_PREFIX) }.getOrDefault(emptyList())
        val out = ArrayList<Expense>(keys.size)
        for (key in keys) {
            val bytes = runCatching { hxs.get(key) }.getOrNull() ?: continue
            val parsed = runCatching {
                json.decodeFromString(Expense.serializer(), bytes.decodeToString())
            }.getOrNull() ?: continue
            out.add(parsed)
        }
        return out.sortedByDescending { it.timestamp }
    }

    suspend fun save(expense: Expense) {
        val payload = json.encodeToString(Expense.serializer(), expense).encodeToByteArray()
        hxs.put(keyFor(expense), payload)
    }

    suspend fun delete(expense: Expense) {
        hxs.delete(keyFor(expense))
    }

    private fun keyFor(expense: Expense): String =
        "$KEY_PREFIX${expense.timestamp}:${expense.id}"

    companion object {
        private const val KEY_PREFIX = "expense:"

        fun newId(): String {
            val suffix = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
            return "e$suffix"
        }
    }
}
