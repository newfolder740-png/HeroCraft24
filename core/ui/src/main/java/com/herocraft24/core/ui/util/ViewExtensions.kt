package com.herocraft24.core.ui.util

import android.content.Context
import android.util.TypedValue

/**
 * Converts density-independent pixels (dp) to pixels using the given [context].
 */
fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

/**
 * Resolves a theme attribute to its color value.
 */
fun Context.resolveColor(attrRes: Int): Int {
    val typedValue = TypedValue()
    this.theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
