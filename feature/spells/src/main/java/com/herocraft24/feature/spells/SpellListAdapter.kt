package com.herocraft24.feature.spells

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.feature.spells.databinding.CardSpellBinding

class SpellListAdapter(
    private var favoriteIds: Set<String>,
    private val onItemClick: (SpellSummary) -> Unit,
    private val onFavoriteClick: (SpellSummary) -> Unit
) : RecyclerView.Adapter<SpellListAdapter.ViewHolder>() {

    private var items: List<SpellSummary> = emptyList()

    fun submitList(list: List<SpellSummary>, favorites: Set<String> = favoriteIds) {
        items = list
        favoriteIds = favorites
        notifyDataSetChanged()
    }

    fun updateFavorites(favorites: Set<String>) {
        favoriteIds = favorites
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: CardSpellBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardSpellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val spell = items[position]
        holder.binding.spellName.text = spell.name
        holder.binding.spellSubtitle.text = buildSubtitle(spell, holder.itemView.context)
        holder.binding.schoolColor.setBackgroundColor(schoolColor(spell.school))
        holder.binding.root.setOnClickListener { onItemClick(spell) }

        val isFav = spell.fullId in favoriteIds
        holder.binding.favoriteIcon.setImageResource(
            if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        holder.binding.favoriteIcon.setOnClickListener { onFavoriteClick(spell) }

        buildBadges(holder.binding.badges, spell)
    }

    override fun getItemCount(): Int = items.size

    private fun buildSubtitle(s: SpellSummary, context: android.content.Context): String {
        val levelStr = if (s.level == 0) {
            context.getString(R.string.spell_cantrip)
        } else {
            "${s.level} уровень"
        }
        return "$levelStr • ${localizeSchool(s.school, context)}"
    }

    private fun localizeSchool(school: String, context: android.content.Context): String = when (school.lowercase()) {
        "abjuration" -> context.getString(R.string.school_abjuration)
        "conjuration" -> context.getString(R.string.school_conjuration)
        "divination" -> context.getString(R.string.school_divination)
        "enchantment" -> context.getString(R.string.school_enchantment)
        "evocation" -> context.getString(R.string.school_evocation)
        "illusion" -> context.getString(R.string.school_illusion)
        "necromancy" -> context.getString(R.string.school_necromancy)
        "transmutation" -> context.getString(R.string.school_transmutation)
        else -> school.replaceFirstChar { it.uppercase() }
    }

    private fun buildBadges(container: LinearLayout, s: SpellSummary) {
        container.removeAllViews()
        val ctx = container.context
        val badges = mutableListOf<String>()
        if (s.concentration) badges.add(ctx.getString(R.string.spell_badge_concentration))
        if (s.ritual) badges.add(ctx.getString(R.string.spell_badge_ritual))
        if (s.components.isNotEmpty()) {
            badges.add(s.components.joinToString("/") { localizeComponent(ctx, it) })
        }
        if (s.damageType != null) {
            badges.add(s.damageType.replaceFirstChar { it.uppercase() })
        }
        if (badges.isNotEmpty()) {
            container.addView(TextView(ctx).apply {
                text = badges.joinToString(" • ")
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(resolveColor(ctx, android.R.attr.textColorSecondary))
            })
        }
    }

    private fun resolveColor(context: android.content.Context, attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    private fun localizeComponent(context: android.content.Context, component: String): String =
        when (component.trim().uppercase()) {
            "V" -> "В"
            "S" -> "С"
            "M" -> "М"
            else -> component
        }

    private fun schoolColor(school: String): Int = when (school) {
        "abjuration" -> Color.parseColor("#2196F3")
        "conjuration" -> Color.parseColor("#FFC107")
        "divination" -> Color.parseColor("#00BCD4")
        "enchantment" -> Color.parseColor("#E91E63")
        "evocation" -> Color.parseColor("#F44336")
        "illusion" -> Color.parseColor("#9C27B0")
        "necromancy" -> Color.parseColor("#4CAF50")
        "transmutation" -> Color.parseColor("#FF9800")
        else -> Color.parseColor("#6750A4")
    }
}