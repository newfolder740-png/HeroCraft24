package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Feature
import com.herocraft24.core.model.FeatureChoice
import com.herocraft24.core.model.Feat
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardFeatureCreateBinding

class FeaturesCreateAdapter(
    private val onFeatureChoiceChanged: (String, String?) -> Unit,
    private val onFeatureMultiChoiceChanged: (String, List<String>) -> Unit = { _, _ -> },
    private val onAsiChoiceChanged: (String, AsiChoice?) -> Unit = { _, _ -> },
    private val onFeatSelected: (String, String?) -> Unit = { _, _ -> },
    private val onSubclassSelected: (String, String?) -> Unit = { _, _ -> },
    private val onPickClassSpells: (String, List<String>, com.herocraft24.core.model.FeatureChoice) -> Unit = { _, _, _ -> },
    private val onPickFeatOptions: (featureId: String, title: String, candidates: List<String>, selected: List<String>, count: Int) -> Unit = { _, _, _, _, _ -> },
    private val onPickMetamagicOptions: (featureId: String, title: String, candidates: List<String>, selected: List<String>, count: Int) -> Unit = { _, _, _, _, _ -> },
    private val onPickInvocations: (featureId: String, current: List<String>, choice: com.herocraft24.core.model.FeatureChoice) -> Unit = { _, _, _ -> },
    private val onPickFeatSpells: (featureId: String, current: List<String>, choice: com.herocraft24.core.model.FeatureChoice, selectedClass: String, selectedAbility: String) -> Unit = { _, _, _, _, _ -> },
    private val initialFeatureChoices: Map<String, String> = emptyMap(),
    private val initialFeatureMultiChoices: Map<String, List<String>> = emptyMap(),
    private val initialAsiChoices: Map<String, AsiChoice> = emptyMap(),
    private val proficientSkills: Set<String> = emptySet(),
    private val characterLevel: Int = 1,
    private val selectedFeats: Set<String> = emptySet(),
    private val classId: String = "",
    private val allowEpicBoons: Boolean = false
) : RecyclerView.Adapter<FeaturesCreateAdapter.ViewHolder>() {

    private var baseItems: List<Feature> = emptyList()
    // featCards: parentFeatureId → synthetic Feature from Feat
    private val featCards = mutableMapOf<String, Feature>()
    // Features whose feat choice spawns a feat card (asi_or_feat)
    private val asiOrFeatFeatures = mutableSetOf<String>()
    // Subclass features added dynamically when subclass is selected
    private var subclassFeatures: List<Feature> = emptyList()
    // Combined display list (base items + feat cards + subclass features)
    private var displayItems: List<Feature> = emptyList()

    private var expandedPosition = -1
    private val featureChoices = mutableMapOf<String, String?>().apply { putAll(initialFeatureChoices) }
    private val featureMultiChoices = mutableMapOf<String, MutableList<String?>>().apply {
        for ((key, list) in initialFeatureMultiChoices) { this[key] = list.map { it as String? }.toMutableList() }
    }
    private val asiChoices = mutableMapOf<String, AsiChoice>().apply { putAll(initialAsiChoices) }
    private val featuresNeedingAsi = mutableSetOf<String>()

    val currentBaseItems: List<Feature> get() = baseItems

    fun submitList(list: List<Feature>) {
        baseItems = list
        rebuildDisplayItems()
    }

    /** Add a feat card (synthetic Feature from a Feat) for a given parent feature ID */
    fun addFeatCard(parentFeatureId: String, featFeature: Feature) {
        featCards[parentFeatureId] = featFeature
        if (featFeature.choice != null) {
            rebuildDisplayItems()
        } else {
            rebuildDisplayItems()
        }
    }

    /** Remove a feat card for a given parent feature ID */
    fun removeFeatCard(parentFeatureId: String) {
        featCards.remove(parentFeatureId)
        featuresNeedingAsi.remove(parentFeatureId)
        asiChoices.remove(parentFeatureId)
        onAsiChoiceChanged(parentFeatureId, null)
        rebuildDisplayItems()
    }

    /** Add subclass features (displayed after the subclass choice feature) */
    fun addSubclassFeatures(features: List<Feature>) {
        subclassFeatures = features
        rebuildDisplayItems()
    }

    /** Remove subclass features (when subclass choice is cleared) */
    fun removeSubclassFeatures() {
        subclassFeatures = emptyList()
        rebuildDisplayItems()
    }

    private fun rebuildDisplayItems() {
        val result = mutableListOf<Feature>()
        val attachedParents = mutableSetOf<String>()
        for (item in baseItems) {
            result.add(item)
            featCards[item.id]?.let { featCard ->
                result.add(featCard)
                attachedParents.add(item.id)
            }
        }
        // Карточки черт без родительского умения в списке (например, черта от происхождения)
        for ((parentId, featCard) in featCards) {
            if (parentId !in attachedParents) result.add(featCard)
        }
        result.addAll(subclassFeatures)
        displayItems = result
        notifyDataSetChanged()
    }

    fun areAllChoicesMade(): Boolean {
        for (feature in displayItems) {
            val choice = feature.choice ?: continue
            when (choice.type) {
                "skill_expertise" -> {
                    val choices = featureMultiChoices[feature.id] ?: return false
                    if (choices.size < choice.count || choices.any { it == null }) return false
                }
                "metamagic" -> {
                    val choices = featureMultiChoices[feature.id] ?: return false
                    if (choices.size < choice.count || choices.any { it == null }) return false
                }
                "invocations" -> {
                    val choices = featureMultiChoices[feature.id] ?: return false
                    if (choices.size < choice.count || choices.any { it == null }) return false
                }
                "magic_initiate" -> {
                    if (featureChoices["${feature.id}_list"] == null) return false
                    if (featureChoices["${feature.id}_ability"] == null) return false
                    val choices = featureMultiChoices[feature.id] ?: return false
                    val selectedCount = choices.count { it != null }
                    if (selectedCount < choice.cantrips + choice.spells) return false
                }
                "class_spells", "wizard_spells" -> {
                    val choices = featureMultiChoices[feature.id] ?: return false
                    val selectedCount = choices.count { it != null }
                    if (selectedCount < choice.cantrips + choice.spells) return false
                }
                "spellcasting_ability" -> {
                    if (featureChoices[feature.id] == null) return false
                }
                "feat_asi" -> {
                    // Фиксированная характеристика (одна в списке) выбора не требует
                    if (choice.abilities.size != 1 && featureChoices["${feature.id}_asi"] == null) return false
                }
                "feat_spells" -> {
                    if (choice.abilities.size > 1 && featureChoices["${feature.id}_ability"] == null) return false
                }
                "asi_or_feat" -> {
                    if (featureChoices[feature.id] == null) return false
                    if (feature.id in featuresNeedingAsi) {
                        val asi = asiChoices[feature.id] ?: return false
                        if (asi.mode == "plus1x2") {
                            if (asi.ability1.isEmpty() || asi.ability2.isEmpty()) return false
                        } else {
                            if (asi.ability1.isEmpty()) return false
                        }
                    }
                }
                "subclass" -> {
                    if (featureChoices[feature.id] == null) return false
                }
                "asi" -> {
                    val parentFeatureId = featCards.entries.find { it.value.id == feature.id }?.key ?: feature.id
                    val asi = asiChoices[parentFeatureId] ?: return false
                    if (asi.mode == "plus1x2") {
                        if (asi.ability1.isEmpty() || asi.ability2.isEmpty()) return false
                    } else {
                        if (asi.ability1.isEmpty()) return false
                    }
                }
                else -> {
                    if (choice.options.isNotEmpty() && featureChoices[feature.id] == null) return false
                }
            }
        }
        return true
    }

    class ViewHolder(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feature = displayItems[position]
        val isExpanded = position == expandedPosition
        val isFeatCard = featCards.values.any { it.id == feature.id }

        val levelSuffix = if (isFeatCard) "" else feature.level?.let { "Ур. $it: " } ?: ""
        val prefix = if (isFeatCard) "Черта: " else ""
        holder.binding.featureTitle.text = "$levelSuffix$prefix${feature.name.get()}"
        holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.binding.headerRow.setOnClickListener {
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            if (prevExpanded >= 0) notifyItemChanged(prevExpanded)
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
        }

        if (isExpanded) {
            buildExpandedContent(holder.binding.expandedContent, feature, isFeatCard)
        }
    }

    override fun getItemCount(): Int = displayItems.size

    private fun buildExpandedContent(container: LinearLayout, feature: Feature, isFeatCard: Boolean) {
        container.removeAllViews()
        val ctx = container.context

        container.addView(TextView(ctx).apply {
            text = feature.description.get()
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, 8.dp(ctx))
        })

        val choice = feature.choice ?: return
        when (choice.type) {
            "skill_expertise" -> buildSkillExpertiseChoice(container, feature, choice)
            "spellcasting_ability" -> buildSpellcastingAbilityChoice(container, feature, choice)
            "metamagic" -> buildMetamagicChoice(container, feature, choice)
            "invocations" -> buildInvocationsChoice(container, feature, choice)
            "class_spells", "wizard_spells" -> buildClassSpellsChoice(container, feature, choice)
            "magic_initiate" -> buildMagicInitiateChoice(container, feature, choice)
            "feat_asi" -> buildFeatAsiChoice(container, feature, choice)
            "feat_spells" -> buildFeatSpellsChoice(container, feature, choice)
            "asi_or_feat" -> buildAsiOrFeatChoice(container, feature)
            "asi" -> {
                // For feat cards, use the parent feature ID as the asiChoices key
                val parentFeatureId = featCards.entries.find { it.value.id == feature.id }?.key ?: feature.id
                buildAsiChoice(container, parentFeatureId, choice)
            }
            "subclass" -> buildSubclassChoice(container, feature)
            else -> if (choice.options.isNotEmpty()) buildFeatCategoryChoice(container, feature, choice)
        }
    }

    // ── Skill Expertise ──

    private fun buildSkillExpertiseChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val count = choice.count
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите $count навыка:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        if (!featureMultiChoices.containsKey(feature.id)) {
            featureMultiChoices[feature.id] = MutableList(count) { null }
        }

        val dropdowns = mutableListOf<com.google.android.material.textfield.MaterialAutoCompleteTextView>()
        for (i in 0 until count) {
            val dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                hint = "Выберите навык"; setOnClickListener { showDropDown() }
                featureMultiChoices[feature.id]?.getOrNull(i)?.let { setText(UiLocalizer.skill(it), false) }
                setOnItemClickListener { _, _, pos, _ ->
                    val currentChoices = featureMultiChoices[feature.id] ?: return@setOnItemClickListener
                    val otherSelected = currentChoices.mapIndexedNotNull { idx, s -> if (idx != i) s else null }.toSet()
                    val available = proficientSkills.filter { it !in otherSelected }.sortedBy { UiLocalizer.skill(it) }
                    val selected = available.getOrNull(pos) ?: return@setOnItemClickListener
                    currentChoices[i] = selected; featureMultiChoices[feature.id] = currentChoices
                    onFeatureMultiChoiceChanged(feature.id, currentChoices.filterNotNull())
                    dropdowns.forEachIndexed { idx, dd ->
                        if (idx != i) {
                            val otherExclude = currentChoices.mapIndexedNotNull { j, s -> if (j != idx) s else null }.toSet()
                            dd.setAdapter(android.widget.ArrayAdapter(dd.context, android.R.layout.simple_dropdown_item_1line,
                                proficientSkills.filter { it !in otherExclude }.sortedBy { UiLocalizer.skill(it) }.map { UiLocalizer.skill(it) }))
                        }
                    }
                }
            }
            dropdowns.add(dropdown); choiceContainer.addView(dropdown)
        }
        dropdowns.forEachIndexed { i, dropdown ->
            val exclude = (featureMultiChoices[feature.id] ?: return@forEachIndexed).mapIndexedNotNull { idx, s -> if (idx != i) s else null }.toSet()
            dropdown.setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line,
                proficientSkills.filter { it !in exclude }.sortedBy { UiLocalizer.skill(it) }.map { UiLocalizer.skill(it) }))
        }
        container.addView(choiceContainer)
    }

    // ── Metamagic ──

    private fun buildMetamagicChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val count = choice.count
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите $count варианта метамагии:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        if (!featureMultiChoices.containsKey(feature.id)) {
            featureMultiChoices[feature.id] = MutableList(count) { null }
        }

        val selectedContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun refreshSelected() {
            selectedContainer.removeAllViews()
            val selected = featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList()
            if (selected.isEmpty()) {
                selectedContainer.addView(TextView(ctx).apply {
                    text = "Метамагия не выбрана"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(0xFF666666.toInt())
                })
            } else {
                for (id in selected) {
                    val name = contentRepo.resolveName(id) ?: id.substringAfterLast(":")
                    selectedContainer.addView(TextView(ctx).apply {
                        text = "• $name"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                    })
                }
            }
        }
        refreshSelected()

        val pickButton = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Выбрать метамагию"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val candidates = if (choice.options.isNotEmpty()) choice.options else contentRepo.getMetamagicIds()
                onPickMetamagicOptions(
                    feature.id,
                    feature.name.get(),
                    candidates,
                    featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList(),
                    count
                )
            }
        }

        choiceContainer.addView(selectedContainer)
        choiceContainer.addView(pickButton)
        container.addView(choiceContainer)
    }

    fun updateMetamagicChoice(featureId: String, selected: List<String>) {
        featureMultiChoices[featureId] = selected.toMutableList()
        onFeatureMultiChoiceChanged(featureId, selected)
        notifyDataSetChanged()
    }

    // ── Invocations (Warlock) ──

    private fun buildInvocationsChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val count = choice.count
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите воззвания ($count):"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        if (!featureMultiChoices.containsKey(feature.id)) {
            featureMultiChoices[feature.id] = MutableList(count) { null }
        }

        val selectedContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun refreshSelected() {
            selectedContainer.removeAllViews()
            val selected = featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList()
            if (selected.isEmpty()) {
                selectedContainer.addView(TextView(ctx).apply {
                    text = "Воззвания не выбраны"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(0xFF666666.toInt())
                })
            } else {
                for (id in selected) {
                    val name = contentRepo.resolveName(id) ?: id.substringAfterLast(":")
                    selectedContainer.addView(TextView(ctx).apply {
                        text = "• $name"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                    })
                }
            }
        }
        refreshSelected()

        val pickButton = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Выбрать воззвания"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onPickInvocations(
                    feature.id,
                    featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList(),
                    choice
                )
            }
        }

        choiceContainer.addView(selectedContainer)
        choiceContainer.addView(pickButton)
        container.addView(choiceContainer)
    }

    fun updateInvocations(featureId: String, selected: List<String>) {
        featureMultiChoices[featureId] = selected.toMutableList()
        onFeatureMultiChoiceChanged(featureId, selected)
        notifyDataSetChanged()
    }

    // ── Class Spells ──

    private fun buildClassSpellsChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите ${choice.cantrips} заговора и ${choice.spells} заклинания 1-го уровня"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        if (!featureMultiChoices.containsKey(feature.id)) {
            featureMultiChoices[feature.id] = mutableListOf()
        }

        val selectedContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun refreshSelected() {
            selectedContainer.removeAllViews()
            val selected = featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList()
            if (selected.isEmpty()) {
                selectedContainer.addView(TextView(ctx).apply {
                    text = "Заклинания не выбраны"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(0xFF666666.toInt())
                })
            } else {
                for (spellId in selected) {
                    val name = contentRepo.resolveName(spellId) ?: spellId.substringAfterLast(":")
                    selectedContainer.addView(TextView(ctx).apply {
                        text = "• $name"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                    })
                }
            }
        }
        refreshSelected()

        val pickButton = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Выбрать заклинания"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onPickClassSpells(feature.id, featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList(), choice)
            }
        }

        choiceContainer.addView(selectedContainer)
        choiceContainer.addView(pickButton)
        container.addView(choiceContainer)
    }

    fun updateClassSpells(featureId: String, selected: List<String>) {
        featureMultiChoices[featureId] = selected.toMutableList()
        onFeatureMultiChoiceChanged(featureId, selected)
        notifyDataSetChanged()
    }

    // ── Magic Initiate (feat spells) ──

    private fun buildMagicInitiateChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val listKey = "${feature.id}_list"
        val abilityKey = "${feature.id}_ability"
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        if (!featureMultiChoices.containsKey(feature.id)) {
            featureMultiChoices[feature.id] = mutableListOf()
        }

        lateinit var pickButton: com.google.android.material.button.MaterialButton

        // 1) Spell list (class) dropdown
        choiceContainer.addView(TextView(ctx).apply {
            text = "Список заклинаний:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
        })
        val classNames = choice.spell_lists.map { contentRepo.resolveName(it) ?: it.substringAfterLast(":") }
        choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
            hint = "Выберите список"; setOnClickListener { showDropDown() }
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, classNames))
            featureChoices[listKey]?.let { val idx = choice.spell_lists.indexOf(it); if (idx >= 0) setText(classNames[idx], false) }
            setOnItemClickListener { _, _, pos, _ ->
                val selected = choice.spell_lists.getOrNull(pos) ?: return@setOnItemClickListener
                val prev = featureChoices[listKey]
                featureChoices[listKey] = selected
                onFeatureChoiceChanged(listKey, selected)
                if (selected != prev && featureMultiChoices[feature.id]?.isNotEmpty() == true) {
                    featureMultiChoices[feature.id] = mutableListOf()
                    onFeatureMultiChoiceChanged(feature.id, emptyList())
                }
                pickButton.isEnabled = true
                notifyDataSetChanged()
            }
        })

        // 2) Spellcasting ability dropdown
        choiceContainer.addView(TextView(ctx).apply {
            text = "Заклинательная характеристика:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
        })
        val abilityOptionNames = choice.abilities.map { abilityNames[it] ?: it }
        choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
            hint = "Выберите характеристику"; setOnClickListener { showDropDown() }
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, abilityOptionNames))
            featureChoices[abilityKey]?.let { val idx = choice.abilities.indexOf(it); if (idx >= 0) setText(abilityOptionNames[idx], false) }
            setOnItemClickListener { _, _, pos, _ ->
                val selected = choice.abilities.getOrNull(pos)
                featureChoices[abilityKey] = selected
                onFeatureChoiceChanged(abilityKey, selected)
            }
        })

        // 3) Selected spells + picker button
        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите ${choice.cantrips} заговора и ${choice.spells} заклинание 1-го уровня:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
        })

        val selectedContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun refreshSelected() {
            selectedContainer.removeAllViews()
            val selected = featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList()
            if (selected.isEmpty()) {
                selectedContainer.addView(TextView(ctx).apply {
                    text = "Заклинания не выбраны"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(0xFF666666.toInt())
                })
            } else {
                for (spellId in selected) {
                    val name = contentRepo.resolveName(spellId) ?: spellId.substringAfterLast(":")
                    selectedContainer.addView(TextView(ctx).apply {
                        text = "• $name"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                    })
                }
            }
        }
        refreshSelected()

        pickButton = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Выбрать заклинания"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isEnabled = featureChoices[listKey] != null
            setOnClickListener {
                val selectedClass = featureChoices[listKey] ?: return@setOnClickListener
                val selectedAbility = featureChoices[abilityKey] ?: "intelligence"
                onPickFeatSpells(
                    feature.id,
                    featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList(),
                    choice,
                    selectedClass,
                    selectedAbility
                )
            }
        }

        choiceContainer.addView(selectedContainer)
        choiceContainer.addView(pickButton)
        container.addView(choiceContainer)
    }

    // ── Spellcasting Ability ──

    private fun buildSpellcastingAbilityChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val optionNames = choice.options.map { abilityNames[it] ?: it }
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Заклинательная характеристика:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
        })

        choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
            hint = "Выберите характеристику"; setOnClickListener { showDropDown() }
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, optionNames))
            featureChoices[feature.id]?.let { val idx = choice.options.indexOf(it); if (idx >= 0) setText(optionNames[idx], false) }
            setOnItemClickListener { _, _, pos, _ ->
                val selectedAbility = choice.options.getOrNull(pos)
                featureChoices[feature.id] = selectedAbility; onFeatureChoiceChanged(feature.id, selectedAbility)
            }
        })
        container.addView(choiceContainer)
    }

    // ── Feat ASI (+1 к ограниченной характеристике) ──

    private fun buildFeatAsiChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val asiKey = "${feature.id}_asi"
        val allowed = if (choice.abilities.isEmpty()) allAbilityKeys else choice.abilities
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Повышение характеристики (+1):"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
        })

        if (allowed.size == 1) {
            choiceContainer.addView(TextView(ctx).apply {
                text = "+1 к «${abilityNames[allowed[0]] ?: allowed[0]}»"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        } else {
            val names = allowed.map { abilityNames[it] ?: it }
            choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                hint = "Выберите характеристику"; setOnClickListener { showDropDown() }
                setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
                featureChoices[asiKey]?.let { val idx = allowed.indexOf(it); if (idx >= 0) setText(names[idx], false) }
                setOnItemClickListener { _, _, pos, _ ->
                    val selected = allowed.getOrNull(pos)
                    featureChoices[asiKey] = selected
                    onFeatureChoiceChanged(asiKey, selected)
                }
            })
        }
        container.addView(choiceContainer)
    }

    // ── Feat Spells (фиксированные всегда подготовленные заклинания + характеристика) ──

    private fun buildFeatSpellsChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val abilityKey = "${feature.id}_ability"
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        val allowed = choice.abilities
        if (allowed.size == 1) {
            choiceContainer.addView(TextView(ctx).apply {
                text = "Заклинательная характеристика: ${abilityNames[allowed[0]] ?: allowed[0]}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 0, 0, 4.dp(ctx))
            })
        } else if (allowed.size > 1) {
            choiceContainer.addView(TextView(ctx).apply {
                text = "Заклинательная характеристика:"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
            })
            val names = allowed.map { abilityNames[it] ?: it }
            choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                hint = "Выберите характеристику"; setOnClickListener { showDropDown() }
                setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
                featureChoices[abilityKey]?.let { val idx = allowed.indexOf(it); if (idx >= 0) setText(names[idx], false) }
                setOnItemClickListener { _, _, pos, _ ->
                    val selected = allowed.getOrNull(pos)
                    featureChoices[abilityKey] = selected
                    onFeatureChoiceChanged(abilityKey, selected)
                }
            })
        }

        fun spellNames(ids: List<String>) = ids.joinToString(", ") { contentRepo.resolveName(it) ?: it.substringAfterLast(":") }
        if (choice.fixed_spells.isNotEmpty()) {
            choiceContainer.addView(TextView(ctx).apply {
                text = "Всегда подготовлены: ${spellNames(choice.fixed_spells)}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 4.dp(ctx), 0, 0)
            })
        }
        for ((lvl, spells) in choice.char_level_spells) {
            choiceContainer.addView(TextView(ctx).apply {
                text = "С $lvl-го уровня также: ${spellNames(spells)}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 4.dp(ctx), 0, 0)
            })
        }
        container.addView(choiceContainer)
    }

    // ── Feat Category ──

    private fun buildFeatCategoryChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val areFeatOptions = choice.options.any { contentRepo.getFeat(it) != null }
        if (!areFeatOptions) {
            buildGenericOptionsChoice(container, feature, choice)
            return
        }
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = if (choice.count > 1) "Выберите ${choice.count} варианта:" else "Выберите вариант:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
        })

        val selectedContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun refreshSelected() {
            selectedContainer.removeAllViews()
            val selectedId = featureChoices[feature.id]
            if (selectedId == null) {
                selectedContainer.addView(TextView(ctx).apply {
                    text = "Черта не выбрана"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(0xFF666666.toInt())
                })
            } else {
                val name = contentRepo.resolveName(selectedId) ?: selectedId.substringAfterLast(":")
                selectedContainer.addView(TextView(ctx).apply {
                    text = "• $name"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                })
            }
        }
        refreshSelected()

        val pickButton = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Выбрать черту"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val candidates = if (choice.options.isNotEmpty()) choice.options
                else contentRepo.getFeatIds().filter { contentRepo.getFeat(it)?.category == choice.category }
                onPickFeatOptions(
                    feature.id,
                    feature.name.get(),
                    candidates,
                    listOfNotNull(featureChoices[feature.id]),
                    choice.count
                )
            }
        }

        choiceContainer.addView(selectedContainer)
        choiceContainer.addView(pickButton)
        container.addView(choiceContainer)
    }

    private fun buildGenericOptionsChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val optionNames = choice.options.mapNotNull { contentRepo.resolveName(it) ?: it.substringAfterLast(":") }
        val choiceContainer2 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer2.addView(TextView(ctx).apply {
            text = if (choice.count > 1) "Выберите ${choice.count} варианта:" else "Выберите вариант:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
        })

        choiceContainer2.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
            hint = "Выберите вариант"; setOnClickListener { showDropDown() }
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, optionNames))
            featureChoices[feature.id]?.let { val idx = choice.options.indexOf(it); if (idx >= 0) setText(optionNames[idx], false) }
            setOnItemClickListener { _, _, pos, _ ->
                val selectedId = choice.options.getOrNull(pos)
                featureChoices[feature.id] = selectedId; onFeatureChoiceChanged(feature.id, selectedId)
            }
        })
        container.addView(choiceContainer2)
    }

    fun updateFeatChoice(featureId: String, selectedId: String?) {
        featureChoices[featureId] = selectedId
        onFeatureChoiceChanged(featureId, selectedId)
        if (featureId in asiOrFeatFeatures) onFeatSelected(featureId, selectedId)
        notifyDataSetChanged()
    }

    // ── ASI or Feat (feat picker, feat card appears separately) ──

    private fun buildAsiOrFeatChoice(container: LinearLayout, feature: Feature) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите черту:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
        })

        asiOrFeatFeatures.add(feature.id)
        val allFeatIds = contentRepo.getFeatIds()
        val availableFeatIds = mutableListOf<String>()
        for (featId in allFeatIds) {
            val feat = contentRepo.getFeat(featId) ?: continue
            if (feat.category == "epic_boon" && !allowEpicBoons) continue
            val localFeatId = featId.substringAfterLast(":")
            if (localFeatId in selectedFeats && !feat.repeatable) continue
            availableFeatIds.add(featId)
        }

        val selectedContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun refreshSelected() {
            selectedContainer.removeAllViews()
            val selectedId = featureChoices[feature.id]
            if (selectedId == null) {
                selectedContainer.addView(TextView(ctx).apply {
                    text = "Черта не выбрана"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(0xFF666666.toInt())
                })
            } else {
                val name = contentRepo.resolveName(selectedId) ?: selectedId.substringAfterLast(":")
                selectedContainer.addView(TextView(ctx).apply {
                    text = "• $name"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                })
            }
        }
        refreshSelected()

        val pickButton = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Выбрать черту"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onPickFeatOptions(
                    feature.id,
                    feature.name.get(),
                    availableFeatIds,
                    listOfNotNull(featureChoices[feature.id]),
                    1
                )
            }
        }

        choiceContainer.addView(selectedContainer)
        choiceContainer.addView(pickButton)
        container.addView(choiceContainer)
    }


    // ── ASI Choice (rendered inside feat cards) ──

    private fun buildAsiChoice(container: LinearLayout, choiceKey: String, choice: FeatureChoice) {
        val ctx = container.context
        val modes = choice.options

        val radioGroup = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.HORIZONTAL }
        val radioButtons = modes.mapIndexed { _, mode ->
            android.widget.RadioButton(ctx).apply {
                id = android.view.View.generateViewId()
                text = when (mode) { "plus1x2" -> "+1 к двум"; "plus2" -> "+2 к одной"; else -> mode }
                isChecked = (asiChoices[choiceKey]?.mode ?: modes.firstOrNull() ?: "plus1x2") == mode
            }
        }
        radioButtons.forEach { radioGroup.addView(it) }
        container.addView(radioGroup)

        val plus1x2Mode = modes.find { it == "plus1x2" }
        val plus2Mode = modes.find { it == "plus2" }

        val plus1x2Container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (plus1x2Mode != null && asiChoices[choiceKey]?.mode != "plus2") View.VISIBLE else View.GONE
        }
        if (plus1x2Mode != null) {
            var ability2Dropdown: com.google.android.material.textfield.MaterialAutoCompleteTextView? = null

            plus1x2Container.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                hint = "Характеристика +1"; setOnClickListener { showDropDown() }
                val names = allAbilityKeys.map { abilityNames[it] ?: it }
                setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
                asiChoices[choiceKey]?.ability1?.let { val idx = allAbilityKeys.indexOf(it); if (idx >= 0) setText(names[idx], false) }
                setOnItemClickListener { _, _, pos, _ ->
                    val selected = allAbilityKeys.getOrNull(pos) ?: return@setOnItemClickListener
                    val current = asiChoices[choiceKey] ?: AsiChoice(mode = "plus1x2")
                    asiChoices[choiceKey] = current.copy(ability1 = selected)
                    onAsiChoiceChanged(choiceKey, asiChoices[choiceKey]!!)
                    ability2Dropdown?.let { updateAbilityExclusions(it, allAbilityKeys, selected) }
                }
            })

            ability2Dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                hint = "Характеристика +1"; setOnClickListener { showDropDown() }
                asiChoices[choiceKey]?.ability1?.let { updateAbilityExclusions(this, allAbilityKeys, it) }
                    ?: run { val names = allAbilityKeys.map { abilityNames[it] ?: it }; setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names)) }
                asiChoices[choiceKey]?.ability2?.let { val idx = allAbilityKeys.indexOf(it); if (idx >= 0) setText(abilityNames[it] ?: it, false) }
                setOnItemClickListener { _, _, pos, _ ->
                    val currentExclude = asiChoices[choiceKey]?.ability1
                    val available = if (currentExclude != null) allAbilityKeys.filter { it != currentExclude } else allAbilityKeys
                    val selected = available.getOrNull(pos) ?: return@setOnItemClickListener
                    val current = asiChoices[choiceKey] ?: AsiChoice(mode = "plus1x2")
                    asiChoices[choiceKey] = current.copy(ability2 = selected)
                    onAsiChoiceChanged(choiceKey, asiChoices[choiceKey]!!)
                }
            }
            plus1x2Container.addView(ability2Dropdown)
        }

        val plus2Container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (plus2Mode != null && asiChoices[choiceKey]?.mode == "plus2") View.VISIBLE else View.GONE
        }
        if (plus2Mode != null) {
            plus2Container.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                hint = "Характеристика +2"; setOnClickListener { showDropDown() }
                val names = allAbilityKeys.map { abilityNames[it] ?: it }
                setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
                if (asiChoices[choiceKey]?.mode == "plus2") {
                    asiChoices[choiceKey]?.ability1?.let { val idx = allAbilityKeys.indexOf(it); if (idx >= 0) setText(names[idx], false) }
                }
                setOnItemClickListener { _, _, pos, _ ->
                    val selected = allAbilityKeys.getOrNull(pos) ?: return@setOnItemClickListener
                    val current = asiChoices[choiceKey] ?: AsiChoice(mode = "plus2")
                    asiChoices[choiceKey] = current.copy(ability1 = selected)
                    onAsiChoiceChanged(choiceKey, asiChoices[choiceKey]!!)
                }
            })
        }

        container.addView(plus1x2Container)
        container.addView(plus2Container)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedRadio = radioButtons.find { it.id == checkedId } ?: return@setOnCheckedChangeListener
            val selectedIdx = radioButtons.indexOf(selectedRadio)
            val newMode = modes.getOrNull(selectedIdx) ?: return@setOnCheckedChangeListener
            plus1x2Container.visibility = if (newMode == "plus1x2") View.VISIBLE else View.GONE
            plus2Container.visibility = if (newMode == "plus2") View.VISIBLE else View.GONE
            val current = asiChoices[choiceKey] ?: AsiChoice()
            asiChoices[choiceKey] = current.copy(mode = newMode)
            onAsiChoiceChanged(choiceKey, asiChoices[choiceKey]!!)
        }

        if (!asiChoices.containsKey(choiceKey)) {
            val defaultMode = modes.firstOrNull() ?: "plus1x2"
            asiChoices[choiceKey] = AsiChoice(mode = defaultMode)
            onAsiChoiceChanged(choiceKey, asiChoices[choiceKey]!!)
        }
        featuresNeedingAsi.add(choiceKey)
    }

    private fun updateAbilityExclusions(dropdown: com.google.android.material.textfield.MaterialAutoCompleteTextView, allKeys: List<String>, excludeKey: String) {
        val available = allKeys.filter { it != excludeKey }
        dropdown.setAdapter(android.widget.ArrayAdapter(dropdown.context, android.R.layout.simple_dropdown_item_1line, available.map { abilityNames[it] ?: it }))
    }

    private val abilityNames = mapOf(
        "strength" to "Сила", "dexterity" to "Ловкость", "constitution" to "Телосложение",
        "intelligence" to "Интеллект", "wisdom" to "Мудрость", "charisma" to "Харизма"
    )
    private val allAbilityKeys = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")

    // ── Subclass Choice ──

    private fun buildSubclassChoice(container: LinearLayout, feature: Feature) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите подкласс:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
        })

        // Resolve the class ID to get its subclasses
        val fullClassId = if (classId.contains(":")) classId else "phb2024:$classId"
        val gameClass = contentRepo.getClass(fullClassId)
        val subclassIds = gameClass?.subclasses ?: emptyList()
        val subclassNames = subclassIds.mapNotNull { id -> contentRepo.resolveName(id) ?: id.substringAfterLast(":") }

        choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
            hint = "Выберите подкласс"; setOnClickListener { showDropDown() }
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, subclassNames))
            featureChoices[feature.id]?.let { selectedId ->
                val idx = subclassIds.indexOf(selectedId)
                if (idx >= 0) setText(subclassNames[idx], false)
            }
            setOnItemClickListener { _, _, pos, _ ->
                val selectedId = subclassIds.getOrNull(pos)
                featureChoices[feature.id] = selectedId
                onFeatureChoiceChanged(feature.id, selectedId)
                onSubclassSelected(feature.id, selectedId)
            }
        })
        container.addView(choiceContainer)
    }
}
