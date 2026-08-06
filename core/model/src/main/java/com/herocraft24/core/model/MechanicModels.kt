package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Mechanic(
    val id: String,
    val type: String = "mechanic",
    val format_version: Int = 1,
    val name: LocalizedString,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val category: String,
    @Serializable(with = StringOrListSerializer::class)
    val subcategory: List<String> = emptyList(),
    val related: List<String> = emptyList()
)

@Serializable
data class GlossaryEntry(
    val id: String,
    val type: String = "glossary",
    val format_version: Int = 1,
    val name: LocalizedString,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val category: String? = null,
    val see_also: List<String> = emptyList()
)