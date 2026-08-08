package com.herocraft24.core.model

/**
 * Flat UI-facing summary of an equipment item.
 */
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

/**
 * Flat UI-facing summary of a spell.
 */
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

/**
 * List item shown in the reference catalogue.
 */
data class ReferenceListItem(
    val fullId: String,
    val name: String,
    val subtitle: String,
    val category: String = "",
    val source: String = "",
    val subcategory: List<String> = emptyList(),
    val rarity: String = "",
    val materialHasCost: Boolean = false,
    val materialConsumable: Boolean = false,
    val size: String = "",
    val creatureType: String = "",
    val challengeRating: Double = 0.0,
    val environment: List<String> = emptyList(),
    val isSwarm: Boolean = false,
    val hitDie: Int? = null
)
