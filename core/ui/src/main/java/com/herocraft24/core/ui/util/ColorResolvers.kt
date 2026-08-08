package com.herocraft24.core.ui.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.herocraft24.core.ui.R

fun Context.schoolColor(school: String): Int = when (school) {
    "abjuration" -> ContextCompat.getColor(this, R.color.school_abjuration)
    "conjuration" -> ContextCompat.getColor(this, R.color.school_conjuration)
    "divination" -> ContextCompat.getColor(this, R.color.school_divination)
    "enchantment" -> ContextCompat.getColor(this, R.color.school_enchantment)
    "evocation" -> ContextCompat.getColor(this, R.color.school_evocation)
    "illusion" -> ContextCompat.getColor(this, R.color.school_illusion)
    "necromancy" -> ContextCompat.getColor(this, R.color.school_necromancy)
    "transmutation" -> ContextCompat.getColor(this, R.color.school_transmutation)
    else -> ContextCompat.getColor(this, R.color.school_default)
}

fun Context.rarityColor(rarity: String): Int = when (rarity) {
    "non-magic" -> ContextCompat.getColor(this, R.color.rarity_non_magic)
    "common" -> ContextCompat.getColor(this, R.color.rarity_common)
    "uncommon" -> ContextCompat.getColor(this, R.color.rarity_uncommon)
    "rare" -> ContextCompat.getColor(this, R.color.rarity_rare)
    "very-rare" -> ContextCompat.getColor(this, R.color.rarity_very_rare)
    "legendary" -> ContextCompat.getColor(this, R.color.rarity_legendary)
    "artifact" -> ContextCompat.getColor(this, R.color.rarity_artifact)
    else -> ContextCompat.getColor(this, R.color.rarity_default)
}
