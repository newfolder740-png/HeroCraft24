package com.herocraft24.core.ui.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Small helper that encapsulates load/save/toggle logic for a set of favorite item ids.
 * Each feature module should use its own [prefsName] to keep favourites isolated.
 */
class FavoritesStore(context: Context, private val prefsName: String) {

    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun load(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    fun save(ids: Set<String>) {
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    fun toggle(id: String, current: Set<String>): Set<String> {
        val next = current.toMutableSet()
        if (next.contains(id)) next.remove(id) else next.add(id)
        save(next)
        return next
    }

    companion object {
        private const val KEY = "ids"
    }
}
