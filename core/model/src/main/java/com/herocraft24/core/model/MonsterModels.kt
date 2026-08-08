package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Monster(
    val id: String,
    val type: String = "monster",
    val format_version: Int = 1,
    val name: LocalizedString,
    val short_description: LocalizedString? = null,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val image: ImageInfo? = null,
    val size: String,
    val creature_type: String,
    val subtype: String? = null,
    val alignment: String,
    val armor_class: Int,
    val armor_class_description: String? = null,
    val hit_points: String,
    val hit_dice: String,
    val initiative: String? = null,
    val speed: MonsterSpeed? = null,
    val ability_scores: Map<String, Int> = emptyMap(),
    val saving_throws: Map<String, Int>? = null,
    val skills: Map<String, Int>? = null,
    val damage_vulnerabilities: List<String>? = null,
    val damage_resistances: List<String>? = null,
    val damage_immunities: List<String>? = null,
    val condition_immunities: List<String>? = null,
    val senses: Map<String, String>? = null,
    val languages: String,
    val challenge_rating: Double,
    val xp: Int,
    val proficiency_bonus: Int,
    val traits: List<MonsterAbility> = emptyList(),
    val actions: List<MonsterAbility> = emptyList(),
    val bonus_actions: List<MonsterAbility>? = null,
    val reactions: List<MonsterAbility>? = null,
    val legendary_actions: List<MonsterAbility>? = null,
    val mythic_actions: List<MonsterAbility>? = null,
    val lair_actions: List<MonsterAbility>? = null,
    val environment: List<String> = emptyList(),
    val treasure: String? = null,
    val equipment: String? = null,
    val table: Table? = null,
    val description2: LocalizedString? = null
)

@Serializable
data class MonsterSpeed(
    val walk: Int,
    val burrow: Int? = null,
    val climb: Int? = null,
    val fly: Int? = null,
    val swim: Int? = null,
    val hover: Boolean? = null
)

@Serializable
data class MonsterAbility(
    val name: LocalizedString,
    val description: LocalizedString,
    val attack: MonsterAttack? = null
)

@Serializable
data class MonsterAttack(
    val type: String,
    val attack_bonus: Int? = null,
    val reach: Int? = null,
    val range_normal: Int? = null,
    val range_long: Int? = null,
    val targets: String? = null,
    val damage: List<MonsterDamage>? = null,
    val save_dc: Int? = null,
    val save_type: String? = null
)

@Serializable
data class MonsterDamage(
    val damage_dice: String,
    val damage_type: String,
    val damage_bonus: Int? = null
)