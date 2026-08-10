package com.herocraft24.feature.characters

import kotlinx.serialization.Serializable

@Serializable
data class CharacterCreateState(
    val name: String = "",
    val speciesId: String = "",
    val subspeciesId: String? = null,
    val backgroundId: String = "",
    val classId: String = "",
    val subclassId: String? = null,
    val abilityScoreMode: String = "custom", // "custom" | "4d6less" | "buy"
    val abilityScores: Map<String, Int> = mapOf(
        "strength" to 8, "dexterity" to 8, "constitution" to 8,
        "intelligence" to 8, "wisdom" to 8, "charisma" to 8
    ),
    val abilityScorePointsUsed: Int = 0,
    val classSkillChoices: List<String> = emptyList(),
    val equipmentChoiceIndex: Int = 0,
    val featureChoices: Map<String, String> = emptyMap(),
    val selectedFeats: List<String> = emptyList(),
    val spells: CharacterSpells? = null,
    val currency: Currency = Currency(),
    val equipment: List<CharacterItem> = emptyList()
)
