package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Feat(
    val id: String,
    val type: String = "feat",
    val format_version: Int = 1,
    val name: LocalizedString,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val category: String,
    val prerequisite: LocalizedString? = null,
    val ability_score_increase: List<AbilityScoreIncrease> = emptyList(),
    val repeatable: Boolean = false,
    val benefits: List<FeatBenefit> = emptyList(),
    val choice: FeatureChoice? = null,
    // Фиксированные владения от черты: id навыков/инструментов/доспехов/оружия/спасбросков
    val proficiencies: List<String> = emptyList(),
    // Фиксированная экспертность в навыках от черты
    val expertise: List<String> = emptyList(),
    // Числовые пассивные эффекты, учитываемые листом персонажа
    val effects: List<FeatEffect> = emptyList()
)

@Serializable
data class FeatEffect(
    val type: String, // hp_per_level | max_hp_flat | initiative_proficiency | speed_bonus | ac_bonus_armored | ac_medium_dex_cap
    val value: Int = 0
)

@Serializable
data class FeatBenefit(
    val name: LocalizedString? = null,
    val description: LocalizedString
)