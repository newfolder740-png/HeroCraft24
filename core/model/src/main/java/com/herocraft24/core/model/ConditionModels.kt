package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Condition(
    val id: String,
    val type: String = "condition",
    val format_version: Int = 1,
    val name: LocalizedString,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val effects: List<LocalizedString> = emptyList(),
    val mechanical: Boolean = true
)