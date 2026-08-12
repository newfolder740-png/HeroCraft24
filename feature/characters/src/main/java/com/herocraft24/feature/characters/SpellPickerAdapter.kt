package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.SpellSchool
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.resolveColor
import com.herocraft24.core.ui.util.schoolColor
import com.herocraft24.feature.characters.databinding.CardPreparedSpellBinding

class SpellPickerAdapter(
    private val onItemClick: (SpellSummary) -> Unit,
    private val onAddClick: (SpellSummary) -> Unit
) : RecyclerView.Adapter<SpellPickerAdapter.VH>() {

    private var items: List<SpellSummary> = emptyList()

    fun submitList(list: List<SpellSummary>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: CardPreparedSpellBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = CardPreparedSpellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val spell = items[position]
        val ctx = holder.itemView.context

        holder.binding.spellName.text = spell.name
        holder.binding.spellSubtitle.text = buildSubtitle(spell, ctx)
        holder.binding.schoolColor.setBackgroundColor(ctx.schoolColor(SpellSchool.fromValue(spell.school)))

        holder.binding.actionButton.text = "+"
        holder.binding.actionButton.setOnClickListener { onAddClick(spell) }
        holder.binding.root.setOnClickListener { onItemClick(spell) }

        buildBadges(holder.binding.badges, spell)
    }

    private fun buildSubtitle(s: SpellSummary, ctx: android.content.Context): String {
        val levelStr = if (s.level == 0) "Заговор" else "${s.level} уровень"
        return "$levelStr • ${UiLocalizer.school(s.school)}"
    }

    private fun buildBadges(container: LinearLayout, s: SpellSummary) {
        container.removeAllViews()
        val ctx = container.context
        val badges = mutableListOf<String>()
        if (s.concentration) badges.add("Концентрация")
        if (s.ritual) badges.add("Ритуал")
        if (s.components.isNotEmpty()) {
            badges.add(s.components.joinToString("/") { localizeComponent(it) })
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

    private fun localizeComponent(c: String): String = when (c.uppercase()) {
        "V" -> "В"
        "S" -> "Ж"
        "M" -> "М"
        else -> c
    }
}
