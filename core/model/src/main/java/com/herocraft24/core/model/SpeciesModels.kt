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
    val description2: LocalizedString? = null,
    val choice: FeatureChoice? = null,
    val spell: String? = null,
    // Несколько заклинаний/заговоров от одной черты (напр. гном: Малая иллюзия + Разговор с животными)
    val spells: List<String> = emptyList(),
    val always_prepared: Boolean = false,
    val spellcasting_ability: String? = null,
    // Фиксированные владения, которые даёт черта вида: навыки, инструменты, оружие
    val proficiencies: List<String> = emptyList(),
    val expertise: List<String> = emptyList(),
    val saves: List<String> = emptyList(),
    // Сопротивления типам урона (id из UiLocalizer.damageType)
    val resistances: List<String> = emptyList(),
    // Числовые пассивы (те же типы, что у черт): hp_per_level, speed_bonus, ac_bonus и т.д.
    val effects: List<FeatEffect> = emptyList(),
    // Счётчик использований «раз в отдых» (тот же механизм, что у умений класса)
    val resource: FeatureResource? = null
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