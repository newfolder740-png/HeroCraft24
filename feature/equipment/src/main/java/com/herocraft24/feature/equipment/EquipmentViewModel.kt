package com.herocraft24.feature.equipment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Item
import com.herocraft24.core.model.ItemCategory
import com.herocraft24.core.model.ItemRarity
import com.herocraft24.core.model.ItemSummary
import com.herocraft24.core.model.Spell
import com.herocraft24.core.ui.data.FavoritesStore
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EquipmentFilters(
    val categories: Set<ItemCategory> = emptySet(),
    val rarities: Set<ItemRarity> = emptySet(),
    val weaponCategories: Set<String> = emptySet(),
    val armorCategories: Set<String> = emptySet(),
    val magic: Boolean? = null,
    val showFavoritesOnly: Boolean = false
) {
    val isActive: Boolean get() = categories.isNotEmpty() || rarities.isNotEmpty() ||
        weaponCategories.isNotEmpty() || armorCategories.isNotEmpty() ||
        magic != null || showFavoritesOnly
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
                cost = item?.cost?.let { "${FormatUtils.formatAmount(it.amount)} ${UiLocalizer.costUnit(it.unit)}" },
                weight = item?.weight?.let { "${FormatUtils.formatAmount(it.amount)} ${UiLocalizer.weightUnit(it.unit)}" },
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

        if (f.categories.isNotEmpty()) result = result.filter { ItemCategory.fromValue(it.category) in f.categories || it.subcategory.any { ItemCategory.fromValue(it) in f.categories } }
        if (f.rarities.isNotEmpty()) result = result.filter { ItemRarity.fromValue(it.rarity) in f.rarities }
        if (f.weaponCategories.isNotEmpty()) result = result.filter { it.subcategory.any { it in f.weaponCategories } }
        if (f.armorCategories.isNotEmpty()) result = result.filter { it.subcategory.any { it in f.armorCategories } || (it.category == "shield" && "shield" in f.armorCategories) }
        if (f.magic != null) result = result.filter { it.magic == f.magic }
        if (f.showFavoritesOnly) result = result.filter { it.fullId in favs }

        result = when (sort) {
            EquipmentSortMode.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            EquipmentSortMode.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
            EquipmentSortMode.RARITY_ASC -> result.sortedBy { rarityOrder(it.rarity) }
            EquipmentSortMode.RARITY_DESC -> result.sortedByDescending { rarityOrder(it.rarity) }
            EquipmentSortMode.CATEGORY_ASC -> result.sortedBy { UiLocalizer.category(it.category) }
        }

        _filteredItems.value = result
    }

    private fun rarityOrder(r: String): Int = when (ItemRarity.fromValue(r)) {
        ItemRarity.NON_MAGIC -> 1
        ItemRarity.COMMON -> 2
        ItemRarity.UNCOMMON -> 3
        ItemRarity.RARE -> 4
        ItemRarity.VERY_RARE, ItemRarity.VERY_RARE_ALT -> 5
        ItemRarity.LEGENDARY -> 6
        ItemRarity.ARTIFACT -> 7
        ItemRarity.VARIES -> 8
        else -> 99
    }
}
