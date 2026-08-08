package com.herocraft24.feature.spells

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.resolveColor
import com.herocraft24.core.ui.util.schoolColor
import com.herocraft24.feature.spells.databinding.CardSpellBinding
import com.herocraft24.feature.spells.util.SpellComponentLocalizer

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
        holder.binding.schoolColor.setBackgroundColor(holder.itemView.context.schoolColor(spell.school))
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
        return "$levelStr • ${UiLocalizer.school(s.school)}"
    }

    private fun buildBadges(container: LinearLayout, s: SpellSummary) {
        container.removeAllViews()
        val ctx = container.context
        val badges = mutableListOf<String>()
        if (s.concentration) badges.add(ctx.getString(R.string.spell_badge_concentration))
        if (s.ritual) badges.add(ctx.getString(R.string.spell_badge_ritual))
        if (s.components.isNotEmpty()) {
            badges.add(s.components.joinToString("/") { SpellComponentLocalizer.localizeComponent(ctx, it) })
        }
        s.damageType?.let { dt ->
            badges.add(dt.replaceFirstChar { it.uppercase() })
        }
        if (badges.isNotEmpty()) {
            container.addView(TextView(ctx).apply {
                text = badges.joinToString(" • ")
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(ctx.resolveColor(android.R.attr.textColorSecondary))
            })
        }
    }

}