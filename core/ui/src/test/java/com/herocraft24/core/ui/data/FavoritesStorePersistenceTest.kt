package com.herocraft24.core.ui.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.eq

class FavoritesStorePersistenceTest {

    private fun createStore(name: String = "test_favs"): Pair<FavoritesStore, InMemorySharedPreferences> {
        val prefs = InMemorySharedPreferences()
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getSharedPreferences(eq(name), eq(Context.MODE_PRIVATE))).thenReturn(prefs)
        return FavoritesStore(context, name) to prefs
    }

    @Test
    fun `load returns empty set when no favorites saved`() {
        val (store, _) = createStore()
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `toggle adds and removes favorite`() {
        val (store, _) = createStore()
        val afterAdd = store.toggle("spell_1", emptySet())
        assertEquals(setOf("spell_1"), afterAdd)

        val afterRemove = store.toggle("spell_1", afterAdd)
        assertTrue(afterRemove.isEmpty())
    }

    @Test
    fun `new store instance reads previously saved favorites`() {
        val (store, prefs) = createStore("equip_favs")
        store.save(setOf("item_a", "item_b"))

        val restoredContext = Mockito.mock(Context::class.java)
        Mockito.`when`(restoredContext.getSharedPreferences(eq("equip_favs"), eq(Context.MODE_PRIVATE))).thenReturn(prefs)
        val restoredStore = FavoritesStore(restoredContext, "equip_favs")
        assertEquals(setOf("item_a", "item_b"), restoredStore.load())
    }

    @Test
    fun `toggle persists changes and returns updated set`() {
        val (store, _) = createStore("spells_favs")
        store.load() // ensure prefs exist

        val ids = store.toggle("fireball", emptySet())
        assertTrue(ids.contains("fireball"))

        val removed = store.toggle("fireball", ids)
        assertFalse(removed.contains("fireball"))
    }
}
