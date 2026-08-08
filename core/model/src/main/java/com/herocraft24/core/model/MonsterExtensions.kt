package com.herocraft24.core.model

/**
 * Null-safe accessors for Monster's nullable collections.
 */

val Monster.effectiveVulnerabilities: List<String>
    get() = damage_vulnerabilities ?: emptyList()

val Monster.effectiveResistances: List<String>
    get() = damage_resistances ?: emptyList()

val Monster.effectiveImmunities: List<String>
    get() = damage_immunities ?: emptyList()

val Monster.effectiveConditionImmunities: List<String>
    get() = condition_immunities ?: emptyList()

val Monster.effectiveBonusActions: List<MonsterAbility>
    get() = bonus_actions ?: emptyList()

val Monster.effectiveReactions: List<MonsterAbility>
    get() = reactions ?: emptyList()

val Monster.effectiveLegendaryActions: List<MonsterAbility>
    get() = legendary_actions ?: emptyList()

val Monster.effectiveMythicActions: List<MonsterAbility>
    get() = mythic_actions ?: emptyList()

val Monster.effectiveLairActions: List<MonsterAbility>
    get() = lair_actions ?: emptyList()

val Monster.effectiveSenses: Map<String, String>
    get() = senses ?: emptyMap()
