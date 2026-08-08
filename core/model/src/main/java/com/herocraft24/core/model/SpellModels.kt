package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Spell(
    val id: String,
    val type: String = "spell",
    val format_version: Int = 1,
    val name: LocalizedString,
    val short_description: LocalizedString? = null,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val image: ImageInfo? = null,
    val level: Int,
    val school: String,
    val casting_time: String,
    val range: SpellRange? = null,
    val components: List<String> = emptyList(),
    val material: String? = null,
    val duration: String,
    val concentration: Boolean = false,
    val ritual: Boolean = false,
    val saving_throw: String? = null,
    val attack_type: String? = null,
    val damage: SpellDamage? = null,
    val area_of_effect: AreaOfEffect? = null,
    val higher_levels: LocalizedString? = null,
    val classes: List<String> = emptyList(),
    val subclasses: List<String> = emptyList(),
    val source_class: String? = null,
    val table: Table? = null,
    val description2: LocalizedString? = null
)

@Serializable
data class SpellRange(
    val type: String,
    val distance: Int? = null,
    val text: String? = null
)

@Serializable
data class SpellDamage(
    val damage_type: String,
    val damage_at_slot_level: Map<String, String> = emptyMap(),
    val damage_at_character_level: Map<String, String>? = null,
    val save: String? = null
)

@Serializable
data class AreaOfEffect(
    val type: String,
    val size: Int
)