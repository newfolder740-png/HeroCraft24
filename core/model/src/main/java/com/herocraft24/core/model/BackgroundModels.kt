package com.herocraft24.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Background(
    val id: String,
    val type: String = "background",
    val format_version: Int = 1,
    val name: LocalizedString,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val ability_score_increases: List<AbilityScoreIncrease> = emptyList(),
    val ability_score_choice: Boolean = false,
    val skill_proficiencies: List<String> = emptyList(),
    val tool_proficiencies: List<String> = emptyList(),
    val tool_item_ids: List<String> = emptyList(),
    val languages: LanguageChoice? = null,
    @SerialName("equipment_choices")
    val equipment: List<EquipmentChoice> = emptyList(),
    val equipment_items: List<String> = emptyList(),
    val feat: String? = null,
    val feature: BackgroundFeature? = null,
    val characteristics: Characteristics? = null,
    val table: Table? = null,
    val description2: LocalizedString? = null
)

@Serializable
data class BackgroundFeature(
    val name: LocalizedString,
    val description: LocalizedString
)

@Serializable
data class Characteristics(
    val personality_traits: Map<String, List<String>> = emptyMap(),
    val ideals: Map<String, List<String>> = emptyMap(),
    val bonds: Map<String, List<String>> = emptyMap(),
    val flaws: Map<String, List<String>> = emptyMap()
)