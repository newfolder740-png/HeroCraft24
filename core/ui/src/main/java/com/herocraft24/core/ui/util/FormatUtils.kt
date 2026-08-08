package com.herocraft24.core.ui.util

object FormatUtils {

    /**
     * Formats a numeric amount, omitting the decimal part when it is zero.
     * Examples: 10.0 -> "10", 10.5 -> "10.5".
     */
    fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }
}
