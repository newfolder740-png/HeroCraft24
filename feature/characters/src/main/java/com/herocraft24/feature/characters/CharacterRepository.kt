package com.herocraft24.feature.characters

import android.content.Context
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
        }.sortedByDescending { it.updatedAt }
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