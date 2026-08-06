package com.herocraft24.feature.reference

/**
 * Formats a monster challenge rating as a whole number or a natural fraction.
 * 18.0 -> "18", 0.125 -> "1/8", 0.25 -> "1/4", 0.5 -> "1/2".
 */
fun formatChallengeRating(cr: Double): String {
    if (cr == 0.0) return "0"
    val whole = cr.toInt()
    if (Math.abs(cr - whole) < 1e-6) return whole.toString()
    // Common CR fractions with denominators 8/4/2.
    for (den in intArrayOf(8, 4, 2)) {
        val n = cr * den
        val rounded = Math.round(n)
        if (Math.abs(n - rounded) < 1e-6 && rounded > 0) {
            val g = gcd(rounded.toInt(), den)
            return "${rounded.toInt() / g}/${den / g}"
        }
    }
    return cr.toString()
}

private fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) { val t = x % y; x = y; y = t }
    return x
}