package com.herocraft24.feature.characters

import android.content.Context
import com.herocraft24.core.data.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class CharacterRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dir = File(context.filesDir, "characters")

    private val _characters = MutableStateFlow<List<CharacterData>>(emptyList())
    val characters: StateFlow<List<CharacterData>> = _characters

    init {
        dir.mkdirs()
    }

    suspend fun loadAll() {
        val files = withContext(Dispatchers.IO) {
            dir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        }
        _characters.value = files.mapNotNull { f ->
            try { json.decodeFromString<CharacterData>(f.readText()) } catch (_: Exception) { null }
        }.map { migrateSpellEntries(it) }.sortedByDescending { it.updatedAt }
    }

    // Старые сохранения держали заклинания как чистые fullId, а источник — отдельно
    // в innateSpellSources; переводим записи в формат "fullId|source".
    private fun migrateSpellEntries(char: CharacterData): CharacterData {
        val sp = char.spells ?: return char
        val hasLegacy = sp.innateSpells.values.any { list -> list.any { "|" !in it } } ||
            sp.preparedByAbility.values.any { list -> list.any { "|" !in it } } ||
            sp.alwaysPreparedSpells.values.any { list -> list.any { "|" !in it } }
        if (!hasLegacy) return char

        fun migrate(list: List<String>, sourceFor: (String) -> String): List<String> =
            list.map { if ("|" in it) it else spellEntry(it, sourceFor(it)) }

        return char.copy(spells = sp.copy(
            preparedByAbility = sp.preparedByAbility.mapValues { (_, list) -> migrate(list) { SPELL_SOURCE_MANUAL } },
            innateSpells = sp.innateSpells.mapValues { (_, list) -> migrate(list) { fullId -> legacyInnateSource(char, fullId) } },
            innateSpellSources = emptyMap(),
            alwaysPreparedSpells = sp.alwaysPreparedSpells.mapValues { (_, list) -> migrate(list) { fullId -> legacyInnateSource(char, fullId) } }
        ))
    }

    private fun legacyInnateSource(char: CharacterData, fullId: String): String {
        char.spells?.innateSpellSources?.get(fullId)?.let { return it }
        val species = ContentRepository.get(context).getSpecies(char.speciesId) ?: return char.classId
        val subspecies = char.subspeciesId?.let { id -> species.subspecies?.find { it.id == id } }
        val traits = species.traits.flatMap { trait ->
            if (trait.is_placeholder && subspecies != null) subspecies.traits else listOf(trait)
        }
        return if (traits.any { it.spell == fullId }) char.speciesId else char.classId
    }

    suspend fun save(char: CharacterData) {
        withContext(Dispatchers.IO) {
            val updated = char.copy(updatedAt = System.currentTimeMillis())
            File(dir, "${char.id}.json").writeText(json.encodeToString(CharacterData.serializer(), updated))
        }
        loadAll()
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { File(dir, "$id.json").delete() }
        loadAll()
    }

    suspend fun duplicate(id: String): CharacterData? {
        val char = _characters.value.find { it.id == id } ?: return null
        val copy = char.copy(
            id = UUID.randomUUID().toString(),
            name = "${char.name} (copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        save(copy)
        return copy
    }

    fun getById(id: String): CharacterData? = _characters.value.find { it.id == id }
}