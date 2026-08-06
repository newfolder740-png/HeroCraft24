package com.herocraft24.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedString(
    val en: String,
    val ru: String? = null
) {
    fun get(locale: String = AppLocale.current): String = when (locale) {
        "ru" -> ru ?: en
        else -> en
    }
}

@Serializable
data class SourceInfo(
    val book: LocalizedString,
    val abbreviation: String,
    val page: Int? = null,
    val url: String? = null,
    val release_date: String? = null
)

@Serializable
data class Reference(
    val type: String,
    val id: String,
    val relationship: String? = null,
    val context: String? = null
)

@Serializable
data class ImageInfo(
    val path: String,
    val artist: String? = null,
    val license: String? = null,
    val caption: LocalizedString? = null
)