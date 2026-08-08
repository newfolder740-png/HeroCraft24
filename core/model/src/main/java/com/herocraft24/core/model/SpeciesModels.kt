package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Species(
    val id: String,
    val type: String = "species",
    val format_version: Int = 1,
    val name: LocalizedString,
    val short_description: LocalizedString? = null,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val creature_type: String,
    val size: String,
    val speed: Int,
    val speeds_other: Map<String, Int>? = null,
    val darkvision: Int? = null,
    val traits: List<SpeciesTrait> = emptyList(),
    val ability_score_increases: List<AbilityScoreIncrease> = emptyList(),
    val languages: LanguageChoice? = null,
    val average_lifespan: String? = null,
    val average_height: String? = null,
    val average_weight: String? = null,
    val subspecies: List<SubspeciesInfo>? = null,
    val table: Table? = null,
    val description2: LocalizedString? = null
)

@Serializable
data class SpeciesTrait(
    val name: LocalizedString,
    val description: LocalizedString,
    val level: Int? = null,
    val is_placeholder: Boolean = false,
    val table: Table? = null,
    val description2: LocalizedString? = null
)

@Serializable
data class AbilityScoreIncrease(
    val ability: String,
    val increase: Int,
    val optional: Boolean = false
)

@Serializable
data class LanguageChoice(
    val count: Int = 0,
    val from: List<String>? = null,
    val default: List<String> = emptyList()
)

@Serializable
data class SubspeciesInfo(
    val id: String,
    val name: LocalizedString,
    val description: LocalizedString? = null,
    val traits: List<SpeciesTrait> = emptyList(),
    val ability_score_increases: List<AbilityScoreIncrease> = emptyList()
)