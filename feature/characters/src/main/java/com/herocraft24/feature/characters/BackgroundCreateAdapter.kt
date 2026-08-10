package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Background
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.ExpandableCard
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardBackgroundCreateBinding

class BackgroundCreateAdapter(
    private val onBackgroundSelected: (String) -> Unit,
    private val onAbilityChoiceChanged: (String, String?, String?) -> Unit,
    private val onEquipmentChoiceChanged: (String, Int) -> Unit
) : RecyclerView.Adapter<BackgroundCreateAdapter.ViewHolder>() {

    private var items: List<Background> = emptyList()
    private var selectedId: String? = null
    private var expandedPosition: Int = -1
    private val openIds = mutableSetOf<String>()
    private val abilityChoices = mutableMapOf<String, Pair<String?, String?>>()
    private val equipmentChoices = mutableMapOf<String, Int>()

    fun submitList(list: List<Background>, selected: String? = selectedId) {
        items = list
        selectedId = selected
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

    class ViewHolder(val binding: CardBackgroundCreateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardBackgroundCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bg = items[position]
        val isExpanded = position == expandedPosition
        val isSelected = bg.id == selectedId

        holder.binding.backgroundName.text = bg.name.get()
        holder.binding.radioButton.isChecked = isSelected
        holder.binding.chevron.rotation = if (isExpanded) 180f else 0f
        holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.binding.root.setOnClickListener {
            if (!isSelected) {
                onBackgroundSelected(bg.id)
                setSelected(bg.id)
            }
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            if (prevExpanded >= 0) notifyItemChanged(prevExpanded)
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
        }

        if (isExpanded) {
            buildExpandedContent(holder.binding.expandedContent, bg, position)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun buildExpandedContent(container: LinearLayout, bg: Background, position: Int) {
        container.removeAllViews()
        val ctx = container.context

        // Description
        val (descCard, _) = ExpandableCard.createExpandableCard(
            ctx, title = "Описание", openId = "bg_desc_${bg.id}", openIdsSet = openIds
        ) { body ->
            body.addView(TextView(ctx).apply {
                text = bg.description.get()
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        }
        container.addView(descCard)

        // Ability score increases
        if (bg.ability_score_increases.isNotEmpty()) {
            buildAbilitySection(container, bg, ctx)
        }

        // Skills
        if (bg.skill_proficiencies.isNotEmpty()) {
            container.addView(makeSection(ctx, "Навыки:",
                bg.skill_proficiencies.joinToString(", ") { UiLocalizer.skill(it) }))
        }

        // Tools
        if (bg.tool_proficiencies.isNotEmpty()) {
            container.addView(makeSection(ctx, "Инструменты:",
                bg.tool_proficiencies.joinToString(", ")))
        }

        // Equipment
        if (bg.equipment.isNotEmpty()) {
            buildEquipmentSection(container, bg, ctx)
        }
    }

    private fun buildAbilitySection(container: LinearLayout, bg: Background, ctx: android.content.Context) {
        val asiContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }
        asiContainer.addView(TextView(ctx).apply {
            text = "Увеличение характеристик:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        val abilities = bg.ability_score_increases.map { it.ability }
        val currentChoice = abilityChoices[bg.id]

        if (bg.ability_score_mode == "all_plus_one") {
            val allText = abilities.joinToString(", ") { "+1 ${localizeAbility(it)}" }
            asiContainer.addView(TextView(ctx).apply {
                text = allText
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        } else {
            val radioGroup = RadioGroup(ctx).apply { orientation = RadioGroup.VERTICAL }

            val optionAId = View.generateViewId()
            val optionBId = View.generateViewId()

            // Option A: +2 and +1
            val optionA = RadioButton(ctx).apply {
                id = optionAId
                text = "Две характеристики: +2 и +1"
            }
            radioGroup.addView(optionA)

            // Dropdowns container
            val dropdownsContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(ctx), 0, 0, 0)
                visibility = View.GONE
            }

            val plus2Label = TextView(ctx).apply {
                text = "+2:"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            }
            dropdownsContainer.addView(plus2Label)

            val plus2Spinner = Spinner(ctx)
            dropdownsContainer.addView(plus2Spinner)

            val plus1Label = TextView(ctx).apply {
                text = "+1:"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            }
            dropdownsContainer.addView(plus1Label)

            val plus1Spinner = Spinner(ctx)
            dropdownsContainer.addView(plus1Spinner)

            radioGroup.addView(dropdownsContainer)

            // Option B: all +1
            val optionB = RadioButton(ctx).apply {
                id = optionBId
                text = "Все три: +1 к каждой"
            }
            radioGroup.addView(optionB)

            // Set initial state
            if (currentChoice != null) {
                radioGroup.check(optionAId)
                dropdownsContainer.visibility = View.VISIBLE
                updateSpinnerAdapters(plus2Spinner, plus1Spinner, abilities, currentChoice.first, currentChoice.second)
            } else {
                radioGroup.check(optionBId)
            }

            // Update spinner adapters based on selections
            fun refreshSpinners() {
                val choice = abilityChoices[bg.id]
                updateSpinnerAdapters(plus2Spinner, plus1Spinner, abilities, choice?.first, choice?.second)
            }

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == optionAId) {
                    dropdownsContainer.visibility = View.VISIBLE
                    if (abilityChoices[bg.id] == null) {
                        abilityChoices[bg.id] = Pair(null, null)
                    }
                    refreshSpinners()
                } else if (checkedId == optionBId) {
                    dropdownsContainer.visibility = View.GONE
                    abilityChoices.remove(bg.id)
                    onAbilityChoiceChanged(bg.id, null, null)
                }
            }

            plus2Spinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val plus2Ability = if (pos == 0) null else getAbilityAtPosition(plus2Spinner, abilities, pos)
                    val plus1Ability = abilityChoices[bg.id]?.second
                    abilityChoices[bg.id] = Pair(plus2Ability, plus1Ability)
                    onAbilityChoiceChanged(bg.id, plus2Ability, plus1Ability)
                    // Refresh +1 spinner to exclude +2 selection
                    refreshSpinners()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            })

            plus1Spinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val plus2Ability = abilityChoices[bg.id]?.first
                    val plus1Ability = if (pos == 0) null else getAbilityAtPosition(plus1Spinner, abilities, pos)
                    abilityChoices[bg.id] = Pair(plus2Ability, plus1Ability)
                    onAbilityChoiceChanged(bg.id, plus2Ability, plus1Ability)
                    // Refresh +2 spinner to exclude +1 selection
                    refreshSpinners()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            })

            asiContainer.addView(radioGroup)
        }
        container.addView(asiContainer)
    }

    private fun updateSpinnerAdapters(
        plus2Spinner: Spinner, plus1Spinner: Spinner,
        abilities: List<String>, selectedPlus2: String?, selectedPlus1: String?
    ) {
        val ctx = plus2Spinner.context

        // +2 options: exclude selected +1
        val plus2Options = abilities.filter { it != selectedPlus1 }
        val plus2Names = listOf("+2: выберите характеристику") + plus2Options.map { localizeAbility(it) }
        plus2Spinner.adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_item, plus2Names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        if (selectedPlus2 != null) {
            val idx = plus2Options.indexOf(selectedPlus2)
            if (idx >= 0) plus2Spinner.setSelection(idx + 1)
        }

        // +1 options: exclude selected +2
        val plus1Options = abilities.filter { it != selectedPlus2 }
        val plus1Names = listOf("+1: выберите характеристику") + plus1Options.map { localizeAbility(it) }
        plus1Spinner.adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_item, plus1Names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        if (selectedPlus1 != null) {
            val idx = plus1Options.indexOf(selectedPlus1)
            if (idx >= 0) plus1Spinner.setSelection(idx + 1)
        }
    }

    private fun getAbilityAtPosition(spinner: Spinner, allAbilities: List<String>, pos: Int): String? {
        // Get the current adapter's list to find the actual ability at this position
        val adapter = spinner.adapter as? android.widget.ArrayAdapter<String> ?: return null
        val item = adapter.getItem(pos) ?: return null
        // Reverse-localize to find the ability key
        return allAbilities.find { localizeAbility(it) == item }
    }

    private fun buildEquipmentSection(container: LinearLayout, bg: Background, ctx: android.content.Context) {
        val equipContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }
        equipContainer.addView(TextView(ctx).apply {
            text = "Снаряжение:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        val radioGroup = RadioGroup(ctx).apply { orientation = RadioGroup.VERTICAL }
        val currentEquipChoice = equipmentChoices[bg.id] ?: 0
        val contentRepo = ContentRepository.get(ctx)

        bg.equipment.forEachIndexed { index, choice ->
            val optionLabels = mutableListOf<String>()

            choice.options.forEach { option ->
                if (option.items.isNotEmpty()) {
                    // List all items
                    val itemNames = option.items.mapNotNull { itemOpt ->
                        val itemId = itemOpt.item_id
                        if (itemId != null) {
                            contentRepo.resolveName(itemId) ?: itemId
                        } else null
                    }
                    optionLabels.add(itemNames.joinToString(", "))
                } else if (option.gold != null) {
                    optionLabels.add("${option.gold} зм")
                }
            }

            val label = if (optionLabels.isNotEmpty()) {
                optionLabels.joinToString(" или ")
            } else {
                "Вариант ${index + 1}"
            }

            radioGroup.addView(RadioButton(ctx).apply {
                text = label
                isChecked = index == currentEquipChoice
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        equipmentChoices[bg.id] = index
                        onEquipmentChoiceChanged(bg.id, index)
                    }
                }
            })
        }
        equipContainer.addView(radioGroup)
        container.addView(equipContainer)
    }

    private fun makeSection(ctx: android.content.Context, title: String, content: String): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
            addView(TextView(ctx).apply {
                text = title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setPadding(0, 0, 0, 4.dp(ctx))
            })
            addView(TextView(ctx).apply {
                text = content
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        }
    }

    private fun localizeAbility(ability: String): String = when (ability.lowercase()) {
        "strength" -> "Сила"
        "dexterity" -> "Ловкость"
        "constitution" -> "Телосложение"
        "intelligence" -> "Интеллект"
        "wisdom" -> "Мудрость"
        "charisma" -> "Харизма"
        else -> ability
    }
}
