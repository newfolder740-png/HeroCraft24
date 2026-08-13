package com.herocraft24.core.data

import android.content.Context
import com.herocraft24.core.model.*
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException

/**
 * Central access point for all game content.
 * Loads JSON files from the assets directory.
 */
class ContentRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ContentRepository? = null

        fun get(context: Context): ContentRepository =
            instance ?: synchronized(this) {
                instance ?: ContentRepository(context.applicationContext).also { instance = it }
            }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var manifestCache: Map<String, Manifest> = emptyMap()
    private var entryIndex: Map<String, Pair<String, ManifestEntry>> = emptyMap()
    private val objectCache = java.util.concurrent.ConcurrentHashMap<String, Any?>()
    private var initialized = false

    /**
     * Initialize the repository by loading manifests from all available packs.
     */
    @Synchronized
    fun initialize() {
        if (initialized) return
        
        manifestCache = loadManifests()
        
        val index = HashMap<String, Pair<String, ManifestEntry>>()
        for ((packId, manifest) in manifestCache) {
            for (entry in allEntries(manifest.objects)) {
                index["$packId:${entry.id}"] = packId to entry
            }
        }
        entryIndex = index
        initialized = true
    }

    private fun loadManifests(): Map<String, Manifest> {
        val manifests = mutableMapOf<String, Manifest>()
        
        try {
            val packDirs = context.assets.list("packs") ?: emptyArray()

            if (packDirs.isEmpty()) {
                return manifests
            }

            for (packId in packDirs) {
                try {
                    val manifestPath = "packs/$packId/manifest.json"
                    val manifestJson = context.assets
                        .open(manifestPath)
                        .bufferedReader()
                        .use { it.readText() }
                    val manifest = json.decodeFromString<Manifest>(manifestJson.stripBom())
                    manifests[packId] = manifest
                } catch (e: Exception) {
                    android.util.Log.e("ContentRepo", "Error loading manifest for pack '$packId': ${e.message}", e)
                    throw e
                }
            }
        } catch (e: FileNotFoundException) {
            // No packs directory - this might be OK for development
        } catch (e: Exception) {
            android.util.Log.e("ContentRepo", "Error in loadManifests: ${e.message}", e)
        }
        
        return manifests
    }

    // ─── Queries ────────────────────────────────────────────────────────

    fun getClassIds(): List<String> = getManifestIds { it.classes }
    fun getSpeciesIds(): List<String> = getManifestIds { it.species }
    fun getBackgroundIds(): List<String> = getManifestIds { it.backgrounds }
    fun getFeatIds(): List<String> = getManifestIds { it.feats }
    fun getConditionIds(): List<String> = getManifestIds { it.conditions }
    fun getMonsterIds(): List<String> = getManifestIds { it.monsters }
    fun getMechanicIds(): List<String> = getManifestIds { it.mechanics }
    fun getGlossaryIds(): List<String> = getManifestIds { it.glossary }
    fun getSpellIds(): List<String> = getManifestIds { it.spells }
    fun getItemIds(): List<String> = getManifestIds { it.items }
    fun getFeatureIds(): List<String> = getManifestIds { it.features }
    fun getSubclassIds(): List<String> = getManifestIds { it.subclasses }
    fun getMetamagicIds(): List<String> = getManifestIds { it.metamagics }
    fun getManeuverIds(): List<String> = getManifestIds { it.maneuvers }
    fun getSchemIds(): List<String> = getManifestIds { it.schems }

    private fun getManifestIds(selector: (ManifestObjects) -> List<ManifestEntry>): List<String> {
        val ids = mutableListOf<String>()
        for ((packId, manifest) in manifestCache) {
            for (entry in selector(manifest.objects)) {
                ids.add("$packId:${entry.id}")
            }
        }
        return ids
    }

    fun getManifestEntry(fullId: String): ManifestEntry? = entryIndex[fullId]?.second

    fun getEntryType(fullId: String): String? {
        val (packId, objectId) = parseFullId(fullId) ?: return null
        val manifest = manifestCache[packId] ?: return null
        return findType(manifest.objects, objectId)
    }

    // ─── Loaders ────────────────────────────────────────────────────────

    fun getClass(fullId: String): GameClass? = loadObject(fullId, "classes")
    fun getSpecies(fullId: String): Species? = loadObject(fullId, "species")
    fun getBackground(fullId: String): Background? = loadObject(fullId, "backgrounds")
    fun getFeat(fullId: String): Feat? = loadObject(fullId, "feats")
    fun getCondition(fullId: String): Condition? = loadObject(fullId, "conditions")
    fun getMonster(fullId: String): Monster? = loadObject(fullId, "monsters")
    fun getMechanic(fullId: String): Mechanic? = loadObject(fullId, "mechanics")
    fun getGlossaryEntry(fullId: String): GlossaryEntry? = loadObject(fullId, "glossary")
    fun getSpell(fullId: String): Spell? = loadObject(fullId, "spells")
    fun getItem(fullId: String): Item? = loadObject(fullId, "items")
    fun getFeature(fullId: String): Feature? = loadObject(fullId, "features")
    fun getSubclass(fullId: String): Subclass? = loadObject(fullId, "subclasses")
    fun getInvocation(fullId: String): Invocation? = loadObject(fullId, "invocations")
    fun getMetamagic(fullId: String): Metamagic? = loadObject(fullId, "metamagics")
    fun getManeuvers(fullId: String): Maneuvers? = loadObject(fullId, "maneuvers")
    fun getSchems(fullId: String): Schems? = loadObject(fullId, "schems")

    /**
     * Resolve any reference by ID — returns the object as a generic map
     * for cross-reference navigation.
     */
    fun resolveReference(fullId: String): Any? {
        val type = getEntryType(fullId) ?: return null
        return when (type) {
            "class" -> getClass(fullId)
            "species" -> getSpecies(fullId)
            "background" -> getBackground(fullId)
            "feat" -> getFeat(fullId)
            "condition" -> getCondition(fullId)
            "monster" -> getMonster(fullId)
            "mechanic" -> getMechanic(fullId)
            "glossary" -> getGlossaryEntry(fullId)
            "feature" -> getFeature(fullId)
            "subclass" -> getSubclass(fullId)
            else -> null
        }
    }

    /**
     * Get the display name for any object by its ID.
     * Returns null if the object is not found.
     */
    fun resolveName(fullId: String): String? {
        val entry = getManifestEntry(fullId)
        return entry?.name?.get()
    }

    // ─── Internal ───────────────────────────────────────────────────────

    private inline fun <reified T> loadObject(fullId: String, dirName: String): T? {
        objectCache[fullId]?.let { return it as? T }
        val (packId, objectId) = parseFullId(fullId) ?: return null
        return try {
            val path = "packs/$packId/$dirName/$objectId.json"
            val content = context.assets.open(path).bufferedReader().use { it.readText() }
            val result = json.decodeFromString<T>(content.stripBom())
            objectCache[fullId] = result
            result
        } catch (e: Exception) {
            android.util.Log.e("ContentRepo", "Failed to load $fullId from $dirName: ${e.message}", e)
            null
        }
    }

    private fun String.stripBom(): String =
        if (isNotEmpty() && this[0] == '\uFEFF') substring(1) else this

    private fun parseFullId(fullId: String): Pair<String, String>? {
        val idx = fullId.indexOf(':')
        if (idx < 0) return null
        return Pair(fullId.substring(0, idx), fullId.substring(idx + 1))
    }

    private fun allEntries(objects: ManifestObjects): List<ManifestEntry> =
        objects.classes + objects.species + objects.backgrounds +
        objects.feats + objects.conditions + objects.monsters +
        objects.mechanics + objects.glossary + objects.spells + objects.items +
        objects.features + objects.subclasses + objects.invocations +
        objects.metamagics + objects.maneuvers + objects.schems

    private fun findType(objects: ManifestObjects, objectId: String): String? {
        if (objects.classes.any { it.id == objectId }) return "class"
        if (objects.species.any { it.id == objectId }) return "species"
        if (objects.backgrounds.any { it.id == objectId }) return "background"
        if (objects.feats.any { it.id == objectId }) return "feat"
        if (objects.conditions.any { it.id == objectId }) return "condition"
        if (objects.monsters.any { it.id == objectId }) return "monster"
        if (objects.mechanics.any { it.id == objectId }) return "mechanic"
        if (objects.glossary.any { it.id == objectId }) return "glossary"
        if (objects.spells.any { it.id == objectId }) return "spell"
        if (objects.items.any { it.id == objectId }) return "item"
        if (objects.features.any { it.id == objectId }) return "feature"
        if (objects.subclasses.any { it.id == objectId }) return "subclass"
        if (objects.invocations.any { it.id == objectId }) return "invocation"
        if (objects.metamagics.any { it.id == objectId }) return "metamagic"
        if (objects.maneuvers.any { it.id == objectId }) return "maneuvers"
        if (objects.schems.any { it.id == objectId }) return "schems"
        return null
    }
}