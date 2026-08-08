package com.herocraft24.core.ui.data

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences] implementation for JVM unit tests.
 */
class InMemorySharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {

    private val store = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<android.content.SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        store.putAll(initial)
    }

    override fun getAll(): Map<String, *> = store.toMap()
    override fun getString(key: String?, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): Set<String>? {
        return store[key] as? Set<String> ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = key in store
    override fun edit(): SharedPreferences.Editor = Editor(store, listeners)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners.remove(it) }
    }

    class Editor(
        private val store: MutableMap<String, Any?>,
        private val listeners: MutableSet<android.content.SharedPreferences.OnSharedPreferenceChangeListener>
    ) : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        @Suppress("UNCHECKED_CAST")
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            key?.let { pending[it] = values?.toSet() }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let { pending[it] = null }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            pending.clear()
            store.keys.forEach { pending[it] = null }
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changedKeys = mutableSetOf<String>()
            pending.forEach { (key, value) ->
                if (value == null) {
                    if (store.remove(key) != null) changedKeys.add(key)
                } else {
                    store[key] = value
                    changedKeys.add(key)
                }
            }
            // Listeners intentionally not notified in this in-memory test double.
        }
    }
}
