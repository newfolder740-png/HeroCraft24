package com.herocraft24.feature.spells.util

import android.content.Context
import com.herocraft24.feature.spells.R

object SpellComponentLocalizer {

    fun localizeComponent(context: Context, component: String): String = when (component.trim().uppercase()) {
        "V" -> context.getString(R.string.spell_comp_verbal)
        "S" -> context.getString(R.string.spell_comp_somatic)
        "M" -> context.getString(R.string.spell_comp_material)
        else -> component
    }
}
