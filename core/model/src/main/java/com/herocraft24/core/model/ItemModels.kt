package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val id: String,
    val type: String = "item",
    val format_version: Int = 1,
    val name: LocalizedString,
    val short_description: LocalizedString? = null,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val category: String,
    @Serializable(with = StringOrListSerializer::class)
    val subcategory: List<String> = emptyList(),
    @Serializable(with = StringOrListSerializer::class)
    val view: List<String> = emptyList(),
    val rarity: String,
    val magic: Boolean = false,
    val attunement: Boolean = false,
    val attunement_requirements: LocalizedString? = null,
    val cost: Cost? = null,
    val weight: Weight? = null,
    val properties: List<String> = emptyList(),
    val damage: WeaponDamage? = null,
    val armor_class: ArmorClass? = null,
    val effects: List<LocalizedString> = emptyList(),
    val table: Table? = null,
    val description2: LocalizedString? = null
)

@Serializable
data class Cost(
    val amount: Double,
    val unit: String
)

@Serializable
data class Weight(
    val amount: Double,
    val unit: String = "lb"
)

@Serializable
data class WeaponDamage(
    val damage_dice: String,
    val damage_type: String,
    val versatile_dice: String? = null
)

@Serializable
data class ArmorClass(
    val base: Int,
    val dex_bonus: Boolean = false,
    val max_dex: Int? = null,
    val min_strength: Int? = null,
    val stealth_disadvantage: Boolean = false
)