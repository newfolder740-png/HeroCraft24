package com.herocraft24.feature.spells

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Spell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpellSummary(
    val fullId: String,
    val name: String,
    val level: Int,
    val school: String,
    val concentration: Boolean,
    val ritual: Boolean,
    val components: List<String>,
    val classes: List<String>,
    val subclasses: List<String>,
    val castingTime: String,
    val damageType: String?,
    val tags: List<String>,
    val material: String? = null,
    val materialHasCost: Boolean = false,
    val materialConsumable: Boolean = false
)

data class SpellFilters(
    val levels: Set<Int> = emptySet(),
    val schools: Set<String> = emptySet(),
    val classes: Set<String> = emptySet(),
    val subclasses: Set<String> = emptySet(),
    val concentration: Boolean? = null,
    val ritual: Boolean? = null,
    val components: Set<String> = emptySet(),
    val castingTimes: Set<String> = emptySet(),
    val showFavoritesOnly: Boolean = false
) {
    val isActive: Boolean get() = levels.isNotEmpty() || schools.isNotEmpty() ||
        classes.isNotEmpty() || subclasses.isNotEmpty() ||
        concentration != null || ritual != null ||
        components.isNotEmpty() || castingTimes.isNotEmpty() ||
        showFavoritesOnly
}

enum class SpellSortMode(val label: String) {
    LEVEL_ASC("Уровень ↑"), LEVEL_DESC("Уровень ↓"),
    NAME_ASC("Имя А–Я"), NAME_DESC("Имя Я–А"),
    SCHOOL_ASC("Школа А–Я")
}

class SpellsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContentRepository.get(application)
    private val prefs = application.getSharedPreferences("spells_favs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    private val _filters = MutableStateFlow(SpellFilters())
    private val _sortMode = MutableStateFlow(SpellSortMode.LEVEL_ASC)
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())

    val isLoading: StateFlow<Boolean> = _isLoading
    val error: StateFlow<String?> = _error
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds
    val searchQuery: StateFlow<String> = _searchQuery
    val filters: StateFlow<SpellFilters> = _filters
    val sortMode: StateFlow<SpellSortMode> = _sortMode

    private val allSpellsCache: List<SpellSummary> by lazy {
        val ids = repository.getSpellIds()
        ids.mapNotNull { fullId ->
            val entry = repository.getManifestEntry(fullId) ?: return@mapNotNull null
            val spell = repository.getSpell(fullId)
            SpellSummary(
                fullId = fullId,
                name = entry.name.get(),
                level = entry.level ?: 0,
                school = entry.school ?: "",
                concentration = entry.concentration ?: false,
                ritual = entry.ritual ?: false,
                components = spell?.components ?: emptyList(),
                classes = entry.classes ?: emptyList(),
                subclasses = spell?.subclasses ?: emptyList(),
                castingTime = spell?.casting_time ?: "",
                damageType = spell?.damage?.damage_type,
                tags = entry.tags,
                material = spell?.material,
                materialHasCost = entry.material_has_cost ?: false,
                materialConsumable = entry.material_consumable ?: false
            )
        }
    }

    private val _filteredSpells = MutableStateFlow<List<SpellSummary>>(emptyList())
    val filteredSpells: StateFlow<List<SpellSummary>> = _filteredSpells

    init {
        repository.initialize()
        loadFavorites()
        recompute()
        _isLoading.value = false
    }

    private fun loadFavorites() {
        val favs = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        _favoriteIds.value = favs
    }

    private fun saveFavorites() {
        prefs.edit().putStringSet("ids", _favoriteIds.value).apply()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        recompute()
    }

    fun setFilters(filters: SpellFilters) {
        _filters.value = filters
        recompute()
    }

    fun setSortMode(mode: SpellSortMode) {
        _sortMode.value = mode
        recompute()
    }

    /** Clears the search query and all filters (used by the system back gesture). */
    fun clearSearchAndFilters() {
        _searchQuery.value = ""
        _filters.value = SpellFilters()
        recompute()
    }

    fun hasActiveSearchOrFilters(): Boolean =
        _searchQuery.value.isNotBlank() || _filters.value.isActive

    fun toggleFavorite(spellId: String) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(spellId)) current.remove(spellId)
        else current.add(spellId)
        _favoriteIds.value = current
        saveFavorites()
        recompute()
    }

    fun getSpell(fullId: String): Spell? = repository.getSpell(fullId)
    fun resolveName(fullId: String): String? = repository.resolveName(fullId)
    fun getEntryType(fullId: String): String? = repository.getEntryType(fullId)
    fun getCondition(fullId: String): com.herocraft24.core.model.Condition? = repository.getCondition(fullId)

    /** Maps a subclass's localised name to its parent class's localised name (e.g. "Круг земли" -> "Друид"). */
    val subclassToClassMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (subclassId in repository.getSubclassIds()) {
            val subclass = repository.getSubclass(subclassId) ?: continue
            val subclassName = subclass.name.get().takeIf { it.isNotBlank() } ?: continue
            val className = repository.resolveName(subclass.class_id) ?: continue
            map[subclassName] = className
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

    private fun recompute() {
        val all = allSpellsCache
        val query = _searchQuery.value
        val filters = _filters.value
        val sort = _sortMode.value
        val favIds = _favoriteIds.value

        var result = all

        if (query.isNotBlank()) {
            val tokens = query.lowercase().trim().split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                result = result.filter { spell ->
                    tokens.all { token ->
                        spell.name.lowercase().contains(token) ||
                        spell.tags.any { it.lowercase().contains(token) } ||
                        spell.school.lowercase().contains(token)
                    }
                }
            }
        }

        if (filters.levels.isNotEmpty()) result = result.filter { it.level in filters.levels }
        if (filters.schools.isNotEmpty()) result = result.filter { it.school in filters.schools }
        if (filters.classes.isNotEmpty()) result = result.filter { spell ->
            spell.classes.any { it.removePrefix("phb2024:") in filters.classes }
        }
        if (filters.subclasses.isNotEmpty()) result = result.filter { spell ->
            spell.subclasses.any { it in filters.subclasses }
        }
        if (filters.concentration != null) result = result.filter { it.concentration == filters.concentration }
        if (filters.ritual != null) result = result.filter { it.ritual == filters.ritual }
        if (filters.components.isNotEmpty()) result = result.filter { spell ->
            matchesComponentFilter(spell, filters.components)
        }
        if (filters.castingTimes.isNotEmpty()) result = result.filter { spell ->
            matchesCastingTimeFilter(spell.castingTime, filters.castingTimes)
        }
        if (filters.showFavoritesOnly) result = result.filter { it.fullId in favIds }

        result = when (sort) {
            SpellSortMode.LEVEL_ASC -> result.sortedWith(compareBy<SpellSummary> { it.level }.thenBy { it.name.lowercase() })
            SpellSortMode.LEVEL_DESC -> result.sortedWith(compareByDescending<SpellSummary> { it.level }.thenBy { it.name.lowercase() })
            SpellSortMode.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            SpellSortMode.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
            SpellSortMode.SCHOOL_ASC -> result.sortedWith(compareBy<SpellSummary> { localizeSchool(it.school) }.thenBy { it.name.lowercase() })
        }

        _filteredSpells.value = result
    }

    private fun localizeSchool(school: String): String = when (school.lowercase()) {
        "abjuration" -> "Ограждение"
        "conjuration" -> "Призыв"
        "divination" -> "Прорицание"
        "enchantment" -> "Очарование"
        "evocation" -> "Воплощение"
        "illusion" -> "Иллюзия"
        "necromancy" -> "Некромантия"
        "transmutation" -> "Преобразование"
        else -> school.replaceFirstChar { it.uppercase() }
    }

    private fun matchesComponentFilter(
        spell: SpellSummary,
        filterComponents: Set<String>
    ): Boolean {
        val components = spell.components
        val material = spell.material
        for (fc in filterComponents) {
            val matches = when (fc) {
                "V" -> "V" in components
                "S" -> "S" in components
                "M" -> "M" in components
                "M_cost" -> "M" in components && (spell.materialHasCost || material?.contains("ЗМ") == true)
                "M_consumed" -> "M" in components && (spell.materialConsumable || material?.contains("расходуем", ignoreCase = true) == true)
                "no_V" -> "V" !in components
                "no_S" -> "S" !in components
                "no_M" -> "M" !in components
                else -> false
            }
            if (!matches) return false
        }
        return true
    }

    private fun matchesCastingTimeFilter(castingTime: String, filterTimes: Set<String>): Boolean {
        for (ft in filterTimes) {
            val matches = when (ft) {
                "action" -> castingTime.startsWith("Действие") || castingTime.contains("Действие")
                "bonus" -> castingTime.contains("Бонусное действие")
                "reaction" -> castingTime.contains("Реакция")
                "minutes" -> castingTime.contains("минут") || castingTime.contains("Минут")
                "hours" -> castingTime.contains("час") || castingTime.contains("Час")
                else -> false
            }
            if (matches) return true
        }
        return false
    }
}
