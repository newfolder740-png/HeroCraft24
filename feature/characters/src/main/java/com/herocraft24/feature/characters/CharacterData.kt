package com.herocraft24.feature.characters

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CharacterData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val classId: String = "",
    val level: Int = 1,
    val subclassId: String? = null,
    val speciesId: String = "",
    val backgroundId: String = "",
    val alignment: String = "",
    val abilityScoreMode: String = "custom", // "custom" | "4d6less" | "buy"
    val abilityScorePointsUsed: Int = 0,
    val subspeciesId: String? = null,
    val classSkillChoices: List<String> = emptyList(),
    val classEquipmentChoice: Int = 0,
    val bgAbilityPlus2: String? = null,
    val bgAbilityPlus1: String? = null,
    val bgAbilityMode: Boolean? = null, // true = +2/+1, false = all +1, null = not chosen
    val bgEquipmentChoice: Int = 0,
    val equipmentChoiceIndex: Int = 0,
    val featureChoices: Map<String, String> = emptyMap(),
    val featureMultiChoices: Map<String, List<String>> = emptyMap(),
    val expertiseSkills: Set<String> = emptySet(),
    val classLevels: Map<String, Int> = emptyMap(),
    val speciesSpellAbility: String? = null,
    val asiChoices: Map<String, AsiChoice> = emptyMap(),
    val selectedFeats: List<String> = emptyList(),
    val featureResources: Map<String, FeatureResourceState> = emptyMap(),
    val equippedArmor: String? = null,
    val equippedShield: String? = null,
    val equippedWeapon1: String? = null,
    val equippedWeapon2: String? = null,
    val equippedMagicItem1: String? = null,
    val equippedMagicItems: List<String> = emptyList(),
    val passivePerception: Int = 10,
    val experience: Int = 0,
    val abilityScores: Map<String, Int> = mapOf(
        "strength" to 10, "dexterity" to 10, "constitution" to 10,
        "intelligence" to 10, "wisdom" to 10, "charisma" to 10
    ),
    val hitPoints: HitPoints = HitPoints(),
    val hitDice: HitDiceState = HitDiceState(),
    val armorClass: Int = 10,
    val initiative: Int = 0,
    val speed: Int = 30,
    val proficiencyBonus: Int = 2,
    val skills: List<SkillState> = emptyList(),
    val savingThrows: List<String> = emptyList(),
    val feats: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val equipment: List<CharacterItem> = emptyList(),
    val spells: CharacterSpells? = null,
    val spellSlots: Map<String, SpellSlotState> = emptyMap(),
    val spellcastingAbilityOverride: String? = null,
    val currency: Currency = Currency(),
    val appearance: String = "",
    val backstory: String = "",
    val notes: String = "",
    val inspiration: Boolean = false,
    val deathSaves: DeathSaves = DeathSaves(),
    val conditions: List<String> = emptyList(),
    val exhaustion: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class HitPoints(
    val max: Int = 10,
    val current: Int = 10,
    val temporary: Int = 0
)

@Serializable
data class HitDiceState(
    val total: String = "1d6",
    val remaining: Int = 1
)

@Serializable
data class SkillState(
    val skill: String,
    val proficient: Boolean = false,
    val expertise: Boolean = false
)

@Serializable
data class CharacterItem(
    val itemId: String,
    val quantity: Int = 1,
    val equipped: Boolean = false,
    val notes: String = "",
    val variantItemId: String? = null
)

@Serializable
data class CharacterSpells(
    val cantrips: List<String> = emptyList(),
    val prepared: List<String> = emptyList(),
    val known: List<String> = emptyList(),
    val preparedByAbility: Map<String, List<String>> = emptyMap(),
    val innateSpells: Map<String, List<String>> = emptyMap()
)

@Serializable
data class SpellSlotState(
    val total: Int,
    val used: Int = 0
)

@Serializable
data class Currency(
    val cp: Int = 0,
    val sp: Int = 0,
    val gp: Int = 0,
    val pp: Int = 0
)

@Serializable
data class DeathSaves(
    val successes: Int = 0,
    val failures: Int = 0
)

@Serializable
data class AsiChoice(
    val mode: String = "plus1x2", // "plus2" or "plus1x2"
    val ability1: String = "",     // e.g. "strength"
    val ability2: String = ""      // only used in "plus1x2" mode
)

@Serializable
data class FeatureResourceState(
    val total: Int = 0,
    val used: Int = 0
)