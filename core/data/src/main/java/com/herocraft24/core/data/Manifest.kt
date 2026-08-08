package com.herocraft24.core.data

import kotlinx.serialization.Serializable
import com.herocraft24.core.model.StringOrListSerializer
import com.herocraft24.core.model.LocalizedString

@Serializable
data class Manifest(
    val pack_id: String,
    val name: LocalizedString,
    val version: String,
    val format_version: Int,
    val objects: ManifestObjects,
    val description: LocalizedString? = null,
    val authors: List<String> = emptyList(),
    val license: String? = null,
    val dependencies: List<String> = emptyList(),
    val language: String = "en",
    val locales: List<String> = emptyList(),
    val rules_version: String = "dnd2024",
    val total_objects: Int = 0
)

@Serializable
data class ManifestObjects(
    val spells: List<ManifestEntry> = emptyList(),
    val items: List<ManifestEntry> = emptyList(),
    val classes: List<ManifestEntry> = emptyList(),
    val species: List<ManifestEntry> = emptyList(),
    val backgrounds: List<ManifestEntry> = emptyList(),
    val feats: List<ManifestEntry> = emptyList(),
    val monsters: List<ManifestEntry> = emptyList(),
    val conditions: List<ManifestEntry> = emptyList(),
    val mechanics: List<ManifestEntry> = emptyList(),
    val glossary: List<ManifestEntry> = emptyList(),
    val features: List<ManifestEntry> = emptyList(),
    val subclasses: List<ManifestEntry> = emptyList(),
    val invocations: List<ManifestEntry> = emptyList(),
    val metamagics: List<ManifestEntry> = emptyList(),
    val maneuvers: List<ManifestEntry> = emptyList(),
    val schems: List<ManifestEntry> = emptyList()
)

@Serializable
data class ManifestEntry(
    val id: String,
    val name: LocalizedString,
    val level: Int? = null,
    val school: String? = null,
    val ritual: Boolean? = null,
    val concentration: Boolean? = null,
    val classes: List<String>? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    @Serializable(with = StringOrListSerializer::class)
    val subcategory: List<String> = emptyList(),
    val rarity: String? = null,
    val hit_die: Int? = null,
    val primary_ability: String? = null,
    val type: String? = null,
    val size: String? = null,
    val speed: Int? = null,
    val challenge_rating: Double? = null,
    val material: String? = null,
    val material_has_cost: Boolean? = null,
    val material_consumable: Boolean? = null
)