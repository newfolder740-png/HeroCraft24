package com.herocraft24.feature.reference

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.data.ManifestEntry
import com.herocraft24.core.model.*
import com.herocraft24.core.ui.util.ItemLinkifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReferenceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContentRepository.get(application)

    init {
        repository.initialize()
        preloadClasses()
    }

    /**
     * Preloads all classes and their referenced features/subclasses/invocations/metamagics
     * into the shared repository cache on a background thread, so opening a class card
     * doesn't have to read tens of JSON files from disk on the main thread.
     *
     * Also pre-builds the combined name map (items + spells + conditions) used by
     * `ItemLinkifier` for auto-linking text in class features. This avoids a costly
     * lazy initialization on the main thread when the first class card is opened.
     */
    private fun preloadClasses() {
        viewModelScope.launch(Dispatchers.IO) {
            for (classId in repository.getClassIds()) {
                val gameClass = repository.getClass(classId) ?: continue
                gameClass.features.forEach { repository.getFeature(it) }
                gameClass.subclasses.forEach { subclassId ->
                    repository.getSubclass(subclassId)?.features?.forEach { repository.getFeature(it) }
                }
                gameClass.invocations.forEach { repository.getInvocation(it) }
                gameClass.metamagics.forEach { repository.getMetamagic(it) }
            }
            // Pre-build the combined name map and its buckets cache for ItemLinkifier.
            getCombinedNameMap()
            getCombinedBucketsCache()
        }
    }

    val categoryKeys = listOf(
        "classes" to "Классы",
        "species" to "Виды",
        "backgrounds" to "Происхождения",
        "feats" to "Черты",
        "items" to "Снаряжение",
        "conditions" to "Состояния",
        "mechanics" to "Игровые механики",
        "monsters" to "Бестиарий",
        "spells" to "Заклинания"
    )

    fun getCategoryIds(key: String): List<String> = when (key) {
        "classes" -> repository.getClassIds()
        "species" -> repository.getSpeciesIds()
        "backgrounds" -> repository.getBackgroundIds()
        "feats" -> repository.getFeatIds()
        "items" -> repository.getItemIds()
        "conditions" -> repository.getConditionIds()
        "mechanics" -> repository.getMechanicIds()
        "monsters" -> repository.getMonsterIds()
        "spells" -> repository.getSpellIds()
        else -> emptyList<String>()
    }

    fun getEntryInfo(fullId: String): ManifestEntry? = repository.getManifestEntry(fullId)

    fun getClass(fullId: String): GameClass? = repository.getClass(fullId)
    fun getSpecies(fullId: String): Species? = repository.getSpecies(fullId)
    fun getBackground(fullId: String): Background? = repository.getBackground(fullId)
    fun getFeat(fullId: String): Feat? = repository.getFeat(fullId)
    fun getItem(fullId: String): Item? = repository.getItem(fullId)
    fun getCondition(fullId: String): Condition? = repository.getCondition(fullId)
    fun getMonster(fullId: String): Monster? = repository.getMonster(fullId)
    fun getMechanic(fullId: String): Mechanic? = repository.getMechanic(fullId)
    fun getFeature(fullId: String): Feature? = repository.getFeature(fullId)
    fun getSubclass(fullId: String): Subclass? = repository.getSubclass(fullId)
    fun getSpell(fullId: String): Spell? = repository.getSpell(fullId)
    fun getInvocation(fullId: String): Invocation? = repository.getInvocation(fullId)
    fun getManeuvers(fullId: String): Maneuvers? = repository.getManeuvers(fullId)
    fun getMetamagic(fullId: String): Metamagic? = repository.getMetamagic(fullId)
    fun getSchems(fullId: String): Schems? = repository.getSchems(fullId)

    fun resolveName(fullId: String): String? = repository.resolveName(fullId)
    fun getEntryType(fullId: String): String? = repository.getEntryType(fullId)

    // Cached name→id maps (rebuilt lazily once). Rebuilding these on every render
    // caused UI freezes on pages with many rich-linked text blocks (e.g. species traits).
    private val _itemNameMap: Map<String, String> by lazy {
        buildNameMap { repository.getItemIds() }
    }

    private val _spellNameMap: Map<String, String> by lazy {
        buildNameMap { repository.getSpellIds() }
    }

    private val _conditionNameMap: Map<String, String> by lazy {
        buildNameMap { repository.getConditionIds().filterNot { it.endsWith(":condition") } }
    }

    private val _combinedNameMap: Map<String, String> by lazy {
        HashMap<String, String>().apply {
            // Items have the highest priority, then spells, then conditions.
            // This matches the convention that equipment names win over spell names
            // when a word like "Щит" could refer to both.
            putAll(_spellNameMap)
            putAll(_conditionNameMap)
            putAll(_itemNameMap)
        }
    }

    private inline fun buildNameMap(idsProvider: () -> List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (id in idsProvider()) {
            val name = resolveName(id) ?: continue
            map[name.lowercase()] = id
        }
        return map
    }

    fun getItemNameMap(): Map<String, String> = _itemNameMap
    fun getSpellNameMap(): Map<String, String> = _spellNameMap

    /** Combined cached map of item/spell/condition localised names → full ids. */
    fun getCombinedNameMap(): Map<String, String> = _combinedNameMap

    private val _combinedBucketsCache: ItemLinkifier.BucketsCache by lazy {
        ItemLinkifier.BucketsCache(_combinedNameMap)
    }

    /** Pre-built buckets cache for ItemLinkifier — avoids rebuilding on every call. */
    fun getCombinedBucketsCache(): ItemLinkifier.BucketsCache = _combinedBucketsCache
}