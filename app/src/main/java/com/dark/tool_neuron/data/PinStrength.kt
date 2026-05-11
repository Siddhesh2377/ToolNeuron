package com.dark.tool_neuron.data

object PinStrength {

    sealed interface Result {
        data object Ok : Result
        data class TooShort(val min: Int) : Result
        data object AllSameDigit : Result
        data object Sequential : Result
        data object CommonlyUsed : Result
    }

    const val MIN_LENGTH = 6

    private val COMMON = setOf(
        "000000", "111111", "222222", "333333", "444444",
        "555555", "666666", "777777", "888888", "999999",
        "123456", "654321", "121212", "112233", "123123",
        "696969", "159753", "789456", "147258", "369369",
        "000007", "147852",
    )

    fun evaluate(pin: String): Result {
        if (pin.length < MIN_LENGTH) return Result.TooShort(MIN_LENGTH)
        if (pin.toCharArray().toSet().size == 1) return Result.AllSameDigit
        if (isMonotonic(pin)) return Result.Sequential
        if (pin in COMMON) return Result.CommonlyUsed
        return Result.Ok
    }

    private fun isMonotonic(pin: String): Boolean {
        if (pin.length < 3) return false
        val digits = pin.map { it.code }
        val diffs = (1 until digits.size).map { digits[it] - digits[it - 1] }
        val first = diffs[0]
        if (first != 1 && first != -1) return false
        return diffs.all { it == first }
    }
}
