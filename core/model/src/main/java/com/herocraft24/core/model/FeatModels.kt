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
    val choice: FeatureChoice? = null
)

@Serializable
data class FeatBenefit(
    val name: LocalizedString? = null,
    val description: LocalizedString
)