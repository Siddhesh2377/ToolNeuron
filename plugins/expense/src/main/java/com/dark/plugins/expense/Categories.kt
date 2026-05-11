package com.dark.plugins.expense

internal data class CategoryDef(
    val id: String,
    val label: String,
    val description: String,
    val emoji: String,
)

internal data class CategoryScore(
    val category: CategoryDef,
    val score: Float,
)

internal object Categories {
    val all: List<CategoryDef> = listOf(
        CategoryDef(
            id = "food",
            label = "Food & Drink",
            description = "groceries restaurant lunch dinner coffee cafe meal food beverage",
            emoji = "🍴",
        ),
        CategoryDef(
            id = "transport",
            label = "Transport",
            description = "uber lyft taxi fuel gas petrol bus train metro parking toll",
            emoji = "🚗",
        ),
        CategoryDef(
            id = "shopping",
            label = "Shopping",
            description = "amazon clothing electronics gadget purchase store mall retail",
            emoji = "🛒",
        ),
        CategoryDef(
            id = "bills",
            label = "Bills & Utilities",
            description = "rent mortgage electricity water internet phone insurance",
            emoji = "📄",
        ),
        CategoryDef(
            id = "entertainment",
            label = "Entertainment",
            description = "movie netflix spotify concert game subscription streaming",
            emoji = "🎬",
        ),
        CategoryDef(
            id = "health",
            label = "Health",
            description = "pharmacy medicine doctor hospital clinic prescription vitamin",
            emoji = "💊",
        ),
        CategoryDef(
            id = "personal_care",
            label = "Personal Care",
            description = "salon haircut barber spa gym fitness wellness",
            emoji = "💇",
        ),
        CategoryDef(
            id = "travel",
            label = "Travel",
            description = "flight hotel airbnb vacation trip airline",
            emoji = "✈️",
        ),
        CategoryDef(
            id = "education",
            label = "Education",
            description = "course book tuition class lesson tutorial",
            emoji = "📚",
        ),
        CategoryDef(
            id = "other",
            label = "Other",
            description = "miscellaneous general expense uncategorized",
            emoji = "✨",
        ),
    )

    fun byId(id: String): CategoryDef = all.firstOrNull { it.id == id } ?: all.last()
}
