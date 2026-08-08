package com.herocraft24.feature.equipment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Item
import com.herocraft24.core.model.Spell
import com.herocraft24.core.ui.data.FavoritesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ItemSummary(
    val fullId: String,
    val name: String,
    val category: String,
    val subcategory: List<String>,
    val rarity: String,
    val magic: Boolean,
    val cost: String?,
    val weight: String?,
    val tags: List<String>
)

data class EquipmentFilters(
    val categories: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val weaponCategories: Set<String> = emptySet(),
    val armorCategories: Set<String> = emptySet(),
    val allSubcategories: Set<String> = emptySet(),
    val magic: Boolean? = null,
    val showFavoritesOnly: Boolean = false
) {
    val isActive: Boolean get() = categories.isNotEmpty() || rarities.isNotEmpty() ||
        weaponCategories.isNotEmpty() || armorCategories.isNotEmpty() ||
        allSubcategories.isNotEmpty() || magic != null || showFavoritesOnly
}

enum class EquipmentSortMode(val label: String) {
    NAME_ASC("Имя А–Я"), NAME_DESC("Имя Я–А"),
    RARITY_ASC("Редкость ↑"), RARITY_DESC("Редкость ↓"),
    CATEGORY_ASC("Тип А–Я")
}

class EquipmentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContentRepository.get(application)
    private val favoritesStore = FavoritesStore(application, "equip_favs")

    private val _searchQuery = MutableStateFlow("")
    private val _filters = MutableStateFlow(EquipmentFilters())
    private val _sortMode = MutableStateFlow(EquipmentSortMode.NAME_ASC)
    private val _isLoading = MutableStateFlow(true)
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())

    val isLoading: StateFlow<Boolean> = _isLoading
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds
    val searchQuery: StateFlow<String> = _searchQuery
    val filters: StateFlow<EquipmentFilters> = _filters
    val sortMode: StateFlow<EquipmentSortMode> = _sortMode

    private val allItemsCache: List<ItemSummary> by lazy {
        val ids = repository.getItemIds()
        ids.mapNotNull { fullId ->
            val entry = repository.getManifestEntry(fullId) ?: return@mapNotNull null
            val item = repository.getItem(fullId)
            ItemSummary(
                fullId = fullId,
                name = entry.name.get(),
                category = entry.category ?: "",
                subcategory = item?.subcategory ?: emptyList<String>(),
                rarity = entry.rarity ?: "non-magic",
                magic = item?.magic ?: false,
                cost = item?.cost?.let { "${formatAmount(it.amount)} ${localizeCostUnit(it.unit)}" },
                weight = item?.weight?.let { "${formatAmount(it.amount)} ${localizeWeightUnit(it.unit)}" },
                tags = entry.tags
            )
        }
    }

    val itemNameToIdMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (fullId in repository.getItemIds()) {
            val entry = repository.getManifestEntry(fullId) ?: continue
            entry.name.en?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
            entry.name.ru?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
        }
        map
    }

    /** Localised spelling -> spell full id, used to make spell references within item text clickable. */
    val spellNameToIdMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (fullId in repository.getSpellIds()) {
            val entry = repository.getManifestEntry(fullId) ?: continue
            entry.name.en?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
            entry.name.ru?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
        }
        map
    }

    /** Localised spelling -> condition full id, excluding the generic "Состояние"/Condition overview article. */
    val conditionNameToIdMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (fullId in repository.getConditionIds()) {
            if (fullId.endsWith(":condition")) continue
            val entry = repository.getManifestEntry(fullId) ?: continue
            entry.name.en?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
            entry.name.ru?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
        }
        map
    }

    private val _filteredItems = MutableStateFlow<List<ItemSummary>>(emptyList<ItemSummary>())
    val filteredItems: StateFlow<List<ItemSummary>> = _filteredItems

    init {
        repository.initialize()
        _favoriteIds.value = favoritesStore.load()
        recompute()
        _isLoading.value = false
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query; recompute() }
    fun setFilters(filters: EquipmentFilters) { _filters.value = filters; recompute() }
    fun setSortMode(mode: EquipmentSortMode) { _sortMode.value = mode; recompute() }

    /** Clears the search query and all filters (used by the system back gesture). */
    fun clearSearchAndFilters() {
        _searchQuery.value = ""
        _filters.value = EquipmentFilters()
        recompute()
    }

    fun hasActiveSearchOrFilters(): Boolean =
        _searchQuery.value.isNotBlank() || _filters.value.isActive

    fun toggleFavorite(itemId: String) {
        _favoriteIds.value = favoritesStore.toggle(itemId, _favoriteIds.value)
        recompute()
    }

    fun getItem(fullId: String): Item? = repository.getItem(fullId)
    fun resolveName(fullId: String): String? = repository.resolveName(fullId)
    fun getEntryTypeSafely(fullId: String): String? = repository.getEntryType(fullId)
    fun getSpell(fullId: String): Spell? = repository.getSpell(fullId)
    fun getCondition(fullId: String): com.herocraft24.core.model.Condition? = repository.getCondition(fullId)

    private fun recompute() {
        var result = allItemsCache
        val q = _searchQuery.value.lowercase().trim()
        val f = _filters.value
        val sort = _sortMode.value
        val favs = _favoriteIds.value

        if (q.isNotBlank()) {
            val tokens = q.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                result = result.filter { item ->
                    tokens.all { t ->
                        item.name.lowercase().contains(t) || item.tags.any { it.lowercase().contains(t) }
                    }
                }
            }
        }

        if (f.categories.isNotEmpty()) result = result.filter { it.category in f.categories || it.subcategory.any { it in f.categories } }
        if (f.rarities.isNotEmpty()) result = result.filter { it.rarity in f.rarities }
        if (f.weaponCategories.isNotEmpty()) result = result.filter { it.subcategory.any { it in f.weaponCategories } }
        if (f.armorCategories.isNotEmpty()) result = result.filter { it.subcategory.any { it in f.armorCategories } || (it.category == "shield" && "shield" in f.armorCategories) }
        if (f.allSubcategories.isNotEmpty()) result = result.filter { it.subcategory.any { it in f.allSubcategories } }
        if (f.magic != null) result = result.filter { it.magic == f.magic }
        if (f.showFavoritesOnly) result = result.filter { it.fullId in favs }

        result = when (sort) {
            EquipmentSortMode.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            EquipmentSortMode.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
            EquipmentSortMode.RARITY_ASC -> result.sortedBy { rarityOrder(it.rarity) }
            EquipmentSortMode.RARITY_DESC -> result.sortedByDescending { rarityOrder(it.rarity) }
            EquipmentSortMode.CATEGORY_ASC -> result.sortedBy { localizeCategory(it.category) }
        }

        _filteredItems.value = result
    }

    private fun localizeRarity(rarity: String): String = when (rarity.lowercase()) {
        "non-magic", "nonmagic" -> "Немагический"
        "common" -> "Обычный"
        "uncommon" -> "Необычный"
        "rare" -> "Редкий"
        "very-rare", "veryrare" -> "Очень редкий"
        "legendary" -> "Легендарный"
        "artifact" -> "Артефакт"
        "varies" -> "Варьируется"
        else -> rarity.replaceFirstChar { it.uppercase() }
    }

    private fun localizeCategory(category: String): String = when (category) {
        "weapon" -> "Оружие"
        "armor" -> "Доспех"
        "shield" -> "Щит"
        "adventuring_gear" -> "Снаряжение приключений"
        "pack" -> "Набор"
        "tool" -> "Ремесленный инструмент"
        "instrument" -> "Инструмент"
        "focus" -> "Фокусировка"
        "wand" -> "Волшебная палочка"
        "rod" -> "Жезл"
        "potion" -> "Зелье"
        "ring" -> "Кольцо"
        "staff" -> "Посох"
        "scroll" -> "Свиток"
        "wondrous_item" -> "Чудесная вещь"
        "ammunition" -> "Боеприпасы"
        "gear" -> "Снаряжение"
        else -> category.replaceFirstChar { it.uppercase() }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }

    private fun localizeCostUnit(unit: String): String = when (unit.lowercase()) {
        "gp" -> "ЗМ"
        "sp" -> "сер"
        "cp" -> "мед"
        "pp" -> "ПМ"
        else -> unit
    }

    private fun localizeWeightUnit(unit: String): String = when (unit.lowercase()) {
        "lb" -> "фнт."
        "kg" -> "кг"
        "oz" -> "унц."
        else -> unit
    }

    private fun rarityOrder(r: String): Int = when (r.lowercase()) {
        "non-magic", "nonmagic" -> 1
        "common" -> 2
        "uncommon" -> 3
        "rare" -> 4
        "very-rare", "veryrare" -> 5
        "legendary" -> 6
        "artifact" -> 7
        "varies" -> 8
        else -> 99
    }
}
