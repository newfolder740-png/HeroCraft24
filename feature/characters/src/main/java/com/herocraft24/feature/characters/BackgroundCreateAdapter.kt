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
    private val onAbilityModeChanged: (Boolean?) -> Unit,
    private val onEquipmentChoiceChanged: (String, Int) -> Unit,
    private val initialAbilityPlus2: String? = null,
    private val initialAbilityPlus1: String? = null,
    private val initialAbilityMode: Boolean? = null,
    private val initialEquipmentChoice: Int = 0
) : RecyclerView.Adapter<BackgroundCreateAdapter.ViewHolder>() {

    private var items: List<Background> = emptyList()
    private var selectedId: String? = null
    private var expandedPosition: Int = -1
    private val openIds = mutableSetOf<String>()
    private val abilityChoices = mutableMapOf<String, Pair<String?, String?>>()
    private val equipmentChoices = mutableMapOf<String, Int>()
    // true = +2/+1 option selected, false = all +1 selected, null = not yet chosen
    private val abilityModeChoices = mutableMapOf<String, Boolean>()

    fun submitList(list: List<Background>, selected: String? = selectedId) {
        items = list
        selectedId = selected
        // Restore initial state for the selected background
        if (selected != null) {
            if (initialAbilityMode != null) {
                abilityModeChoices[selected] = initialAbilityMode
            }
            if (initialAbilityPlus2 != null || initialAbilityPlus1 != null) {
                abilityChoices[selected] = Pair(initialAbilityPlus2, initialAbilityPlus1)
            }
            equipmentChoices[selected] = initialEquipmentChoice
        }
        notifyDataSetChanged()
    }

    fun setSelected(id: String?) {
        val oldSelected = selectedId
        selectedId = id
        // Default ability mode to "all +1" when a new background is selected
        if (id != null && !abilityModeChoices.containsKey(id)) {
            val bg = items.find { it.id == id }
            if (bg != null && bg.ability_score_increases.isNotEmpty()) {
                abilityModeChoices[id] = false
                onAbilityModeChanged(false)
            }
        }
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

    fun areAllAbilitiesSelected(): Boolean {
        val bgId = selectedId ?: return true
        val bg = items.find { it.id == bgId } ?: return true
        if (bg.ability_score_increases.isEmpty()) return true
        val mode = abilityModeChoices[bgId]
        if (mode == null) return false // not yet chosen
        if (!mode) return true // "all +1" — no dropdowns needed
        // +2/+1 mode: both must be filled
        val choice = abilityChoices[bgId] ?: return false
        return choice.first != null && choice.second != null
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
        holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.binding.headerRow.setOnClickListener {
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

            fun createDropdown(label: String): Pair<View, com.google.android.material.textfield.MaterialAutoCompleteTextView> {
                val container = LinearLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = 8.dp(ctx)
                    }
                    orientation = LinearLayout.VERTICAL
                }
                val labelView = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = label
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                }
                container.addView(labelView)
                val dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    inputType = android.text.InputType.TYPE_NULL
                    threshold = 0
                    dropDownWidth = 600
                    minWidth = 0
                    isFocusableInTouchMode = false
                    setOnClickListener { showDropDown() }
                }
                container.addView(dropdown)
                return Pair(container, dropdown)
            }

            val (plus2Layout, plus2Dropdown) = createDropdown("+2: выберите характеристику")
            dropdownsContainer.addView(plus2Layout)

            val (plus1Layout, plus1Dropdown) = createDropdown("+1: выберите характеристику")
            dropdownsContainer.addView(plus1Layout)

            radioGroup.addView(dropdownsContainer)

            // Option B: all +1
            val optionB = RadioButton(ctx).apply {
                id = optionBId
                text = "Все три: +1 к каждой"
            }
            radioGroup.addView(optionB)

            fun optionsForPlus2() = abilities.filter { it != abilityChoices[bg.id]?.second }
            fun optionsForPlus1() = abilities.filter { it != abilityChoices[bg.id]?.first }

            fun setDropdownText(dropdown: com.google.android.material.textfield.MaterialAutoCompleteTextView, selected: String?) {
                dropdown.setText(selected?.let { localizeAbility(it) } ?: "", false)
            }

            fun updatePlus2Dropdown() {
                val options = optionsForPlus2()
                plus2Dropdown.setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, options.map { localizeAbility(it) }))
                setDropdownText(plus2Dropdown, abilityChoices[bg.id]?.first)
            }

            fun updatePlus1Dropdown() {
                val options = optionsForPlus1()
                plus1Dropdown.setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, options.map { localizeAbility(it) }))
                setDropdownText(plus1Dropdown, abilityChoices[bg.id]?.second)
            }

            plus2Dropdown.setOnItemClickListener { _, _, position, _ ->
                val selected = optionsForPlus2().getOrNull(position)
                var plus1Ability = abilityChoices[bg.id]?.second
                if (plus1Ability == selected) plus1Ability = null
                abilityChoices[bg.id] = Pair(selected, plus1Ability)
                onAbilityChoiceChanged(bg.id, selected, plus1Ability)
                plus1Dropdown.post { updatePlus1Dropdown() }
            }

            plus1Dropdown.setOnItemClickListener { _, _, position, _ ->
                val selected = optionsForPlus1().getOrNull(position)
                abilityChoices[bg.id] = Pair(abilityChoices[bg.id]?.first, selected)
                onAbilityChoiceChanged(bg.id, abilityChoices[bg.id]?.first, selected)
                plus2Dropdown.post { updatePlus2Dropdown() }
            }

            // Set initial state
            if (currentChoice != null) {
                radioGroup.check(optionAId)
                dropdownsContainer.visibility = View.VISIBLE
                abilityModeChoices[bg.id] = true
                updatePlus2Dropdown()
                updatePlus1Dropdown()
            } else if (abilityModeChoices[bg.id] == false) {
                radioGroup.check(optionBId)
            } else {
                radioGroup.check(optionBId)
            }

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                if (checkedId == optionAId) {
                    dropdownsContainer.visibility = View.VISIBLE
                    abilityModeChoices[bg.id] = true
                    onAbilityModeChanged(true)
                    if (abilityChoices[bg.id] == null) {
                        abilityChoices[bg.id] = Pair(null, null)
                    }
                    plus2Dropdown.post { updatePlus2Dropdown() }
                    plus1Dropdown.post { updatePlus1Dropdown() }
                } else if (checkedId == optionBId) {
                    dropdownsContainer.visibility = View.GONE
                    abilityModeChoices[bg.id] = false
                    onAbilityModeChanged(false)
                    abilityChoices.remove(bg.id)
                    onAbilityChoiceChanged(bg.id, null, null)
                }
            }

            asiContainer.addView(radioGroup)
        }
        container.addView(asiContainer)
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

        var radioIndex = 0
        val radioButtons = mutableListOf<RadioButton>()
        for (choice in bg.equipment) {
            for (option in choice.options) {
                val label = buildOptionLabel(option, contentRepo)
                val idx = radioIndex
                val rb = RadioButton(ctx).apply {
                    text = label
                    isChecked = idx == currentEquipChoice
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            // Uncheck all other radio buttons
                            radioButtons.forEachIndexed { i, other ->
                                if (i != idx) other.isChecked = false
                            }
                            equipmentChoices[bg.id] = idx
                            onEquipmentChoiceChanged(bg.id, idx)
                        }
                    }
                }
                radioButtons.add(rb)
                radioGroup.addView(rb)
                radioIndex++
            }
        }
        equipContainer.addView(radioGroup)
        container.addView(equipContainer)
    }

    private fun buildOptionLabel(option: com.herocraft24.core.model.EquipmentOption, repo: com.herocraft24.core.data.ContentRepository): String {
        val parts = mutableListOf<String>()
        if (option.items.isNotEmpty()) {
            val itemNames = option.items.mapNotNull { itemOpt ->
                val itemId = itemOpt.item_id
                if (itemId != null) repo.resolveName(itemId) ?: itemId else null
            }
            if (itemNames.isNotEmpty()) parts.add(itemNames.joinToString(", "))
        }
        if (option.gold != null) {
            parts.add("${option.gold} зм")
        }
        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Вариант"
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
