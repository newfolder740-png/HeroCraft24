package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.Species
import com.herocraft24.core.model.SpeciesTrait
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.ExpandableCard
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardSpeciesCreateBinding

class SpeciesCreateAdapter(
    private val onSpeciesSelected: (String) -> Unit,
    private val onSubspeciesSelected: (String, String?) -> Unit,
    private val initialSubspeciesId: String? = null
) : RecyclerView.Adapter<SpeciesCreateAdapter.ViewHolder>() {

    private var items: List<Species> = emptyList()
    private var selectedId: String? = null
    private var expandedPosition: Int = -1
    private val openTraitIds = mutableSetOf<String>()
    // Track selected subspecies per species
    private val selectedSubspecies = mutableMapOf<String, String?>()

    fun submitList(list: List<Species>, selected: String? = selectedId) {
        items = list
        selectedId = selected
        // Restore initial subspecies selection
        if (selected != null && initialSubspeciesId != null) {
            selectedSubspecies[selected] = initialSubspeciesId
        }
        notifyDataSetChanged()
    }

    fun setSelected(id: String?) {
        val oldSelected = selectedId
        selectedId = id
        if (oldSelected != null) {
            val oldIndex = items.indexOfFirst { it.id == oldSelected }
            if (oldIndex >= 0) notifyItemChanged(oldIndex)
        }
        if (id != null) {
            val newIndex = items.indexOfFirst { it.id == id }
            if (newIndex >= 0) notifyItemChanged(newIndex)
        }
    }

    fun collapseAll() {
        val prevExpanded = expandedPosition
        expandedPosition = -1
        if (prevExpanded >= 0) notifyItemChanged(prevExpanded)
    }

    fun clearSubspecies(speciesId: String) {
        selectedSubspecies.remove(speciesId)
    }

    class ViewHolder(val binding: CardSpeciesCreateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardSpeciesCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val species = items[position]
        val isExpanded = position == expandedPosition
        val isSelected = species.id == selectedId

        holder.binding.speciesName.text = species.name.get()
        holder.binding.radioButton.isChecked = isSelected
        holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.binding.headerRow.setOnClickListener {
            if (!isSelected) {
                onSpeciesSelected(species.id)
                setSelected(species.id)
            }
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            if (prevExpanded >= 0) notifyItemChanged(prevExpanded)
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
        }

        if (isExpanded) {
            buildExpandedContent(holder.binding.expandedContent, species, position)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun buildExpandedContent(container: LinearLayout, species: Species, position: Int) {
        container.removeAllViews()
        val ctx = container.context
        val selectedSubId = selectedSubspecies[species.id]

        // Quick info section
        val quickInfoCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }
        quickInfoCard.addView(TextView(ctx).apply {
            text = "Быстрая информация"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })
        quickInfoCard.addView(makeRow(ctx, "Тип", UiLocalizer.type(species.creature_type)))
        quickInfoCard.addView(makeRow(ctx, "Размер", UiLocalizer.size(species.size)))
        quickInfoCard.addView(makeRow(ctx, "Скорость", "${species.speed} фт."))
        species.darkvision?.let { if (it > 0) quickInfoCard.addView(makeRow(ctx, "Тёмное зрение", "$it фт.")) }
        container.addView(quickInfoCard)

        // Description (expandable)
        val (descCard, _) = ExpandableCard.createExpandableCard(
            ctx,
            title = "Описание",
            openId = "desc_${species.id}",
            openIdsSet = openTraitIds
        ) { body ->
            body.addView(TextView(ctx).apply {
                text = species.description.get()
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        }
        container.addView(descCard)

        // Subspecies dropdown
        val subspeciesList = species.subspecies
        if (!subspeciesList.isNullOrEmpty()) {
            val subContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
            }
            subContainer.addView(TextView(ctx).apply {
                text = "Род:"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setPadding(0, 0, 0, 4.dp(ctx))
            })
            val names = subspeciesList.map { it.name.get() }
            val dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL
                threshold = 0
                isFocusableInTouchMode = false
                hint = "Выберите род"
                setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
                setOnClickListener { showDropDown() }
                val currentSubId = selectedSubspecies[species.id]
                if (currentSubId != null) {
                    val idx = subspeciesList.indexOfFirst { it.id == currentSubId }
                    if (idx >= 0) setText(names[idx], false)
                }
                setOnItemClickListener { _, _, pos, _ ->
                    val subId = subspeciesList.getOrNull(pos)?.id
                    val prevSubId = selectedSubspecies[species.id]
                    if (subId != prevSubId) {
                        selectedSubspecies[species.id] = subId
                        onSubspeciesSelected(species.id, subId)
                        notifyItemChanged(position)
                    }
                }
            }
            subContainer.addView(dropdown)
            container.addView(subContainer)
        }

        // Traits section - rebuild based on selected subspecies
        val traitsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }
        traitsContainer.addView(TextView(ctx).apply {
            text = "Черты вида:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        // Get effective traits (considering subspecies)
        val selectedSub = selectedSubId?.let { id -> subspeciesList?.find { it.id == id } }
        val effectiveTraits = buildEffectiveTraits(species, selectedSub)

        for (trait in effectiveTraits) {
            traitsContainer.addView(createTraitCard(ctx, trait))
        }
        container.addView(traitsContainer)
    }

    private fun buildEffectiveTraits(species: Species, selectedSub: com.herocraft24.core.model.SubspeciesInfo?): List<SpeciesTrait> {
        val result = mutableListOf<SpeciesTrait>()
        for (trait in species.traits) {
            if (trait.is_placeholder && selectedSub != null) {
                // Replace placeholder with subspecies traits
                result.addAll(selectedSub.traits)
            } else {
                result.add(trait)
            }
        }
        return result
    }

    private fun createTraitCard(ctx: android.content.Context, trait: SpeciesTrait): View {
        val levelSuffix = trait.level?.let { "Уровень $it: " } ?: ""
        val (card, _) = ExpandableCard.createExpandableCard(
            ctx,
            title = "$levelSuffix${trait.name.get()}",
            openId = "trait_${trait.name.get()}",
            openIdsSet = openTraitIds
        ) { body ->
            body.addView(TextView(ctx).apply {
                text = trait.description.get()
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        }
        return card
    }

    private fun makeRow(ctx: android.content.Context, label: String, value: String): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
            addView(TextView(ctx).apply {
                text = "$label: "
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(ctx).apply {
                text = value
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
        }
    }
}
