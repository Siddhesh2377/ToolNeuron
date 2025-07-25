package com.dark.plugins.sys.uiAction

import com.dark.plugins.sys.plugins.AppInfo
import kotlin.compareTo
import kotlin.math.min

 fun levenshtein(lhs: String, rhs: String): Int {
    val lhsLength = lhs.length
    val rhsLength = rhs.length
    val cost = Array(lhsLength + 1) { IntArray(rhsLength + 1) }

    for (i in 0..lhsLength) cost[i][0] = i
    for (j in 0..rhsLength) cost[0][j] = j

    for (i in 1..lhsLength) {
        for (j in 1..rhsLength) {
            val editCost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
            cost[i][j] = min(
                min(cost[i - 1][j] + 1, cost[i][j - 1] + 1),
                cost[i - 1][j - 1] + editCost
            )
        }
    }
    return cost[lhsLength][rhsLength]
}

 fun extractAppNames(input: String): List<String> {
    val openIndex = input.indexOf("open")
    if (openIndex == -1) return emptyList()

    val afterOpen = input.substring(openIndex + 4).trim()

    // Split on "and", "&", "then", ",", and normalize
    return afterOpen
        .lowercase()
        .replace("&", ",")
        .replace(" and ", ",")
        .replace(" then ", ",")
        .replace(",", ", ")
        .replace(" or ", ",")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

 fun fuzzyFindApp(apps: List<AppInfo>, name: String): AppInfo? {
    val cleanedName = name.trim().lowercase()
    return apps.minByOrNull {
        levenshtein(it.appName.lowercase(), cleanedName)
    }?.takeIf {
        // Optional: threshold filter
        levenshtein(it.appName.lowercase(), cleanedName) <= 3
    }
}