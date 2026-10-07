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
import com.herocraft24.core.model.ProficiencyPart
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
    private val onPickManeuvers: (featureId: String, title: String, current: List<String>, required: Int) -> Unit = { _, _, _, _ -> },
    private val onPickFeatSpells: (featureId: String, current: List<String>, choice: com.herocraft24.core.model.FeatureChoice, selectedClass: String, selectedAbility: String) -> Unit = { _, _, _, _, _ -> },
    private val onPickFeatSpellPart: (featureId: String, partIndex: Int, spec: com.herocraft24.core.model.SpellPickSpec, current: List<String>, required: Int) -> Unit = { _, _, _, _, _ -> },
    private val initialFeatureChoices: Map<String, String> = emptyMap(),
    private val initialFeatureMultiChoices: Map<String, List<String>> = emptyMap(),
    private val initialAsiChoices: Map<String, AsiChoice> = emptyMap(),
    private val proficientSkills: Set<String> = emptySet(),
    // Навыки, в которых у персонажа уже есть Экспертность (из любого источника)
    private val expertiseSkills: Set<String> = emptySet(),
    // Характеристики, спасбросками которыми персонаж уже владеет (от класса)
    private val classSavingThrows: Set<String> = emptySet(),
    private val characterLevel: Int = 1,
    // Уровень класса, за который берётся уровень (для count_steps приёмов); 0 = использовать characterLevel
    private val classLevelForChoices: Int = 0,
    private val selectedFeats: Set<String> = emptySet(),
    private val classId: String = "",
    private val allowEpicBoons: Boolean = false
) : RecyclerView.Adapter<FeaturesCreateAdapter.ViewHolder>() {

    private var baseItems: List<Feature> = emptyList()
    // featCards: parentFeatureId → synthetic Feature from Feat
    private val featCards = mutableMapOf<String, Feature>()
    // Features whose feat choice spawns a feat card (asi_or_feat)
    private val asiOrFeatFeatures = mutableSetOf<String>()
    // Features, чей выбор является чертой (asi_or_feat и feat_category) — нужны,
    // чтобы черты, взятые на этом же экране, исключались из следующих пикеров
    private val featBearingFeatures = mutableSetOf<String>()
    private val extraSelectedFeats = mutableSetOf<String>()
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
                "maneuvers" -> {
                    val choices = featureMultiChoices[feature.id] ?: return false
                    val required = requiredManeuverCount(choice)
                    if (choices.count { it != null } < required) return false
                }
                "magic_initiate" -> {
                    if (featureChoices["${feature.id}_list"] == null) return false
                    if (featureChoices["${feature.id}_ability"] == null) return false
                    val choices = featureMultiChoices[feature.id] ?: return false
                    val selectedCount = choices.count { it != null }
                    if (selectedCount < choice.cantrips + choice.spells) return false
                }
                "class_spells", "wizard_spells", "prepared_spells", "known_spell" -> {
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
                "feat_multi" -> {
                    if (choice.asi > 0 && choice.abilities.size != 1 && featureChoices["${feature.id}_asi"] == null) return false
                    choice.proficiency_parts.forEachIndexed { index, part ->
                        val selections = featureMultiChoices["${feature.id}_prof$index"] ?: return false
                        if (selections.count { it != null } < part.count) return false
                    }
                    if (choice.spell_abilities.size > 1 && featureChoices["${feature.id}_ability"] == null) return false
                    choice.pick_spells.forEachIndexed { index, spec ->
                        val required = requiredSpellPickCount(spec)
                        val selections = featureMultiChoices["${feature.id}_spell$index"] ?: return false
                        if (selections.count { it != null } < required) return false
                    }
                }
                "grants" -> {
                    choice.proficiency_parts.forEachIndexed { index, part ->
                        val selections = featureMultiChoices["${feature.id}_prof$index"] ?: return false
                        if (selections.count { it != null } < part.count) return false
                    }
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
            "maneuvers" -> buildManeuversChoice(container, feature, choice)
            "class_spells", "wizard_spells", "prepared_spells", "known_spell" -> buildClassSpellsChoice(container, feature, choice)
            "magic_initiate" -> buildMagicInitiateChoice(container, feature, choice)
            "feat_asi" -> buildFeatAsiChoice(container, feature, choice)
            "feat_spells" -> buildFeatSpellsChoice(container, feature, choice)
            "feat_multi" -> buildFeatMultiChoice(container, feature, choice)
            "grants" -> buildGrantsChoice(container, feature, choice)
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
        // choice.options сужает список («Академические знания», «Благословенные знания»);
        // навыки, в которых Экспертность уже есть, не предлагаются
        val allowedSkills = proficientSkills
            .filter { choice.options.isEmpty() || it in choice.options }
            .filter { it !in expertiseSkills }
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = if (count > 1) "Выберите $count навыка:" else "Выберите навык:"
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
                    val available = allowedSkills.filter { it !in otherSelected }.sortedBy { UiLocalizer.skill(it) }
                    val selected = available.getOrNull(pos) ?: return@setOnItemClickListener
                    currentChoices[i] = selected; featureMultiChoices[feature.id] = currentChoices
                    onFeatureMultiChoiceChanged(feature.id, currentChoices.filterNotNull())
                    dropdowns.forEachIndexed { idx, dd ->
                        if (idx != i) {
                            val otherExclude = currentChoices.mapIndexedNotNull { j, s -> if (j != idx) s else null }.toSet()
                            dd.setAdapter(android.widget.ArrayAdapter(dd.context, android.R.layout.simple_dropdown_item_1line,
                                allowedSkills.filter { it !in otherExclude }.sortedBy { UiLocalizer.skill(it) }.map { UiLocalizer.skill(it) }))
                        }
                    }
                }
            }
            dropdowns.add(dropdown); choiceContainer.addView(dropdown)
        }
        dropdowns.forEachIndexed { i, dropdown ->
            val exclude = (featureMultiChoices[feature.id] ?: return@forEachIndexed).mapIndexedNotNull { idx, s -> if (idx != i) s else null }.toSet()
            dropdown.setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line,
                allowedSkills.filter { it !in exclude }.sortedBy { UiLocalizer.skill(it) }.map { UiLocalizer.skill(it) }))
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

    // ── Maneuvers (Battle Master) ──

    /** Сколько приёмов должно быть известно: по порогам count_steps ("3=3,7=5,10=7,15=9") или count. */
    private fun requiredManeuverCount(choice: FeatureChoice): Int {
        val steps = choice.count_steps
        if (steps.isNullOrBlank()) return choice.count
        val level = if (classLevelForChoices > 0) classLevelForChoices else characterLevel
        var result = 0
        for (part in steps.split(",")) {
            val kv = part.split("=")
            if (kv.size != 2) continue
            val threshold = kv[0].trim().toIntOrNull() ?: continue
            val value = kv[1].trim().toIntOrNull() ?: continue
            if (level >= threshold && value > result) result = value
        }
        return if (result > 0) result else choice.count
    }

    private fun buildManeuversChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val required = requiredManeuverCount(choice)
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        choiceContainer.addView(TextView(ctx).apply {
            text = "Выберите приёмы ($required):"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        if (!featureMultiChoices.containsKey(feature.id)) {
            featureMultiChoices[feature.id] = MutableList(required) { null }
        }

        val selectedContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun refreshSelected() {
            selectedContainer.removeAllViews()
            val selected = featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList()
            if (selected.isEmpty()) {
                selectedContainer.addView(TextView(ctx).apply {
                    text = "Приёмы не выбраны"
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
            text = "Выбрать приёмы"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onPickManeuvers(
                    feature.id,
                    feature.name.get(),
                    featureMultiChoices[feature.id]?.filterNotNull() ?: emptyList(),
                    required
                )
            }
        }

        choiceContainer.addView(selectedContainer)
        choiceContainer.addView(pickButton)
        container.addView(choiceContainer)
    }

    fun updateManeuvers(featureId: String, selected: List<String>) {
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
            text = if (choice.type == "known_spell") {
                "Выберите заклинание ${choice.spell_level}-го уровня"
            } else {
                "Выберите ${choice.cantrips} заговора и ${choice.spells} заклинания 1-го уровня"
            }
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
        // Черту можно взять повторно, но каждый раз — новый список заклинаний
        val takenLists = featureChoices
            .filterKeys { it != listKey && it.endsWith("_list") }
            .values.filterNotNull().toSet()
        val availableLists = choice.spell_lists.filter { it !in takenLists }
        val classNames = availableLists.map { contentRepo.resolveName(it) ?: it.substringAfterLast(":") }
        choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
            hint = "Выберите список"; setOnClickListener { showDropDown() }
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, classNames))
            featureChoices[listKey]?.let { val idx = availableLists.indexOf(it); if (idx >= 0) setText(classNames[idx], false) }
            setOnItemClickListener { _, _, pos, _ ->
                val selected = availableLists.getOrNull(pos) ?: return@setOnItemClickListener
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

    // ── Feat Multi (ASI + выборы владений/экспертности/типов урона) ──

    private val ALL_SKILL_IDS = listOf(
        "athletics", "acrobatics", "sleight_of_hand", "stealth", "arcana", "history", "investigation",
        "nature", "religion", "animal_handling", "insight", "medicine", "perception", "survival",
        "deception", "intimidation", "performance", "persuasion"
    )

    private fun partOptions(part: ProficiencyPart, repo: ContentRepository? = null, partKey: String? = null): List<String> {
        val base = when {
            part.options.isNotEmpty() -> part.options
            part.kind == "skill" || part.kind == "skill_tool" -> ALL_SKILL_IDS
            part.kind == "save" -> allAbilityKeys
            else -> emptyList()
        }
        var result = base
        val isSkillPart = part.kind == "skill" || part.kind == "skill_tool"
        if (part.grant == "expertise" && isSkillPart && repo != null) {
            // Собственный текущий выбор части остаётся доступным, иначе он пропадёт из списка
            val own = partKey?.let { featureMultiChoices[it]?.filterNotNull()?.toSet() } ?: emptySet()
            val taken = effectiveExpertiseSkills(repo)
            result = result.filter { it !in taken || it in own }
            if (part.require_proficiency) {
                val known = effectiveProficientSkills(repo)
                result = result.filter { it in known }
            }
        }
        // Тип урона нельзя выбрать дважды между разными экземплярами одной черты
        if (part.kind == "damage_type" && partKey != null) {
            val taken = choicesTakenElsewhere(partKey, base)
            result = result.filter { it !in taken }
        }
        return result
    }

    /** Навыки, которыми персонаж владеет, включая выданные чертами, выбранными на этом экране. */
    private fun effectiveProficientSkills(repo: ContentRepository): Set<String> {
        val result = proficientSkills.toMutableSet()
        forEachChosenFeat(repo) { parentKey, feat ->
            feat.proficiencies.forEach { if (it in ALL_SKILL_IDS) result.add(it) }
            val parts = feat.choice?.takeIf { it.type == "feat_multi" }?.proficiency_parts ?: return@forEachChosenFeat
            parts.forEachIndexed { partIndex, part ->
                if (part.grant != "proficiency") return@forEachIndexed
                for (v in featureMultiChoices["featcard_${parentKey}_prof$partIndex"].orEmpty()) {
                    if (v != null && v in ALL_SKILL_IDS) result.add(v)
                }
            }
        }
        return result
    }

    /** Навыки с Экспертностью: от персонажа и от черт, выбранных на этом экране. */
    private fun effectiveExpertiseSkills(repo: ContentRepository): Set<String> {
        val result = expertiseSkills.toMutableSet()
        forEachChosenFeat(repo) { parentKey, feat ->
            feat.expertise.forEach { if (it in ALL_SKILL_IDS) result.add(it) }
            val parts = feat.choice?.takeIf { it.type == "feat_multi" }?.proficiency_parts ?: return@forEachChosenFeat
            parts.forEachIndexed { partIndex, part ->
                if (part.grant != "expertise") return@forEachIndexed
                for (v in featureMultiChoices["featcard_${parentKey}_prof$partIndex"].orEmpty()) {
                    if (v != null && v in ALL_SKILL_IDS) result.add(v)
                }
            }
        }
        return result
    }

    /** Перебирает черты, выбранные в карточках этого экрана (parentKey → Feat). */
    private inline fun forEachChosenFeat(repo: ContentRepository, block: (String, Feat) -> Unit) {
        for ((key, id) in featureChoices) {
            if (id == null || key.startsWith("featcard_")) continue
            val feat = repo.getFeat(id) ?: continue
            block(key, feat)
        }
    }

    /**
     * Значения из [allowed], уже выбранные в ДРУГОЙ карточке черты.
     * Выборы той же карточки не учитываются: например, «Эксперт в навыке» разрешает
     взять Экспертность в навыке, только что выбранном этой же чертой.
     */
    private fun choicesTakenElsewhere(partKey: String, allowed: List<String>): Set<String> {
        val allowedSet = allowed.toSet()
        val cardKey = partKey.substringBeforeLast("_prof")
        val taken = mutableSetOf<String>()
        for ((key, values) in featureMultiChoices) {
            if (key.substringBeforeLast("_prof") == cardKey) continue
            for (v in values) if (v != null && v in allowedSet) taken.add(v)
        }
        return taken
    }

    /** Сбрасывает выборы частей, ставшие недопустимыми после изменения другой части. */
    private fun dropInvalidPartSelections(repo: ContentRepository, cardId: String, parts: List<ProficiencyPart>) {
        parts.forEachIndexed { index, part ->
            val partKey = "${cardId}_prof$index"
            val values = featureMultiChoices[partKey] ?: return@forEachIndexed
            val allowed = partOptions(part, repo, partKey).toSet()
            var changed = false
            for (i in values.indices) {
                val v = values[i]
                if (v != null && v !in allowed) {
                    values[i] = null
                    changed = true
                }
            }
            if (changed) onFeatureMultiChoiceChanged(partKey, values.filterNotNull())
        }
    }

    private fun optionDisplayName(repo: ContentRepository, kind: String, id: String): String = when (kind) {
        "skill" -> UiLocalizer.skill(id)
        "skill_tool" -> if (id in ALL_SKILL_IDS) UiLocalizer.skill(id) else (repo.resolveName(id) ?: id.substringAfterLast(":"))
        "save" -> abilityNames[id] ?: id
        "damage_type" -> UiLocalizer.damageType(id)
        else -> repo.resolveName(id) ?: id.substringAfterLast(":")
    }

    private fun partLabel(part: ProficiencyPart): String {
        val what = when (part.kind) {
            "skill" -> "навыков"
            "skill_tool" -> "навыков или инструментов"
            "tool" -> "ремесленных инструментов"
            "instrument" -> "музыкальных инструментов"
            "save" -> "спасбросок"
            "damage_type" -> "тип урона"
            else -> "вариантов"
        }
        val action = when (part.grant) {
            "expertise" -> "Экспертность"
            "proficiency_and_expertise" -> "Владение и Экспертность"
            "expertise_or_proficiency" -> "Владение или Экспертность"
            "display" -> "Выберите"
            else -> "Владение"
        }
        val countStr = if (part.count > 1) " (${part.count})" else ""
        return if (part.grant == "display") "$action $what$countStr:" else "$action — $what$countStr:"
    }

    /**
     * Рендер частей владений/экспертности/отображаемых выборов.
     * Используется и чертами (feat_multi), и умениями (grants).
     * Выборы хранятся в featureMultiChoices["<keyPrefix>_prof<index>"].
     */
    private fun renderProficiencyParts(
        choiceContainer: LinearLayout,
        repo: ContentRepository,
        keyPrefix: String,
        parts: List<ProficiencyPart>
    ) {
        val ctx = choiceContainer.context
        // Выбор в одной части может менять допустимые значения в другой
        // («Эксперт в навыке»: экспертность — в только что выбранном навыке)
        val needsRebindOnChange = parts.any {
            it.require_proficiency || it.grant == "expertise" || it.kind == "damage_type"
        }
        parts.forEachIndexed { index, part ->
            val partKey = "${keyPrefix}_prof$index"
            val optionIds = partOptions(part, repo, partKey)
            choiceContainer.addView(TextView(ctx).apply {
                text = partLabel(part)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
            })
            if (!featureMultiChoices.containsKey(partKey)) {
                featureMultiChoices[partKey] = MutableList(part.count) { null }
            }
            fun namesFor(excluded: Set<String?>): List<String> =
                optionIds.filter { it !in excluded }.map { optionDisplayName(repo, part.kind, it) }

            val dropdowns = mutableListOf<com.google.android.material.textfield.MaterialAutoCompleteTextView>()
            for (i in 0 until part.count) {
                val dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                    hint = "Выберите"; setOnClickListener { showDropDown() }
                    featureMultiChoices[partKey]?.getOrNull(i)?.let {
                        setText(optionDisplayName(repo, part.kind, it), false)
                    }
                    setOnItemClickListener { _, _, pos, _ ->
                        val current = featureMultiChoices[partKey] ?: return@setOnItemClickListener
                        val otherSelected = current.mapIndexedNotNull { idx, s -> if (idx != i) s else null }.toSet()
                        val selected = optionIds.filter { it !in otherSelected }.getOrNull(pos) ?: return@setOnItemClickListener
                        current[i] = selected
                        featureMultiChoices[partKey] = current
                        onFeatureMultiChoiceChanged(partKey, current.filterNotNull())
                        dropdowns.forEachIndexed { idx, dd ->
                            if (idx != i) {
                                val excl = current.mapIndexedNotNull { j, s -> if (j != idx) s else null }.toSet()
                                dd.setAdapter(android.widget.ArrayAdapter(dd.context, android.R.layout.simple_dropdown_item_1line, namesFor(excl)))
                            }
                        }
                        if (needsRebindOnChange) {
                            dropInvalidPartSelections(repo, keyPrefix, parts)
                            notifyDataSetChanged()
                        }
                    }
                }
                dropdowns.add(dropdown)
                choiceContainer.addView(dropdown)
            }
            dropdowns.forEachIndexed { i, dd ->
                val excl = (featureMultiChoices[partKey] ?: return@forEachIndexed)
                    .mapIndexedNotNull { idx, s -> if (idx != i) s else null }.toSet()
                dd.setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, namesFor(excl)))
            }
        }
    }

    /** Выбор владений/экспертности, который даёт умение класса или подкласса. */
    private fun buildGrantsChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }
        renderProficiencyParts(choiceContainer, ContentRepository.get(ctx), feature.id, choice.proficiency_parts)
        container.addView(choiceContainer)
    }

    private fun buildFeatMultiChoice(container: LinearLayout, feature: Feature, choice: FeatureChoice) {
        val ctx = container.context
        val contentRepo = ContentRepository.get(ctx)
        val choiceContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(ctx), 0, 8.dp(ctx)) }

        // ASI part
        if (choice.asi > 0) {
            val asiKey = "${feature.id}_asi"
            val allowed = (if (choice.abilities.isEmpty()) allAbilityKeys else choice.abilities)
                // «Стойкий»: характеристику, спасброском которой уже владеешь, выбрать нельзя
                .let { list ->
                    if (choice.asi_grants_save && classSavingThrows.isNotEmpty())
                        list.filter { it !in classSavingThrows }.ifEmpty { list }
                    else list
                }
            choiceContainer.addView(TextView(ctx).apply {
                text = if (choice.asi_grants_save) "Повышение характеристики (+${choice.asi}) — также даёт владение её спасброском:"
                else "Повышение характеристики (+${choice.asi}):"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setPadding(0, 0, 0, 4.dp(ctx))
            })
            if (allowed.size == 1) {
                choiceContainer.addView(TextView(ctx).apply {
                    text = "+${choice.asi} к «${abilityNames[allowed[0]] ?: allowed[0]}»"
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
        }

        renderProficiencyParts(choiceContainer, contentRepo, feature.id, choice.proficiency_parts)

        // Отдельный выбор заклинательной характеристики (например, Boon of Revelry)
        if (choice.spell_abilities.isNotEmpty()) {
            val abilityKey = "${feature.id}_ability"
            choiceContainer.addView(TextView(ctx).apply {
                text = "Заклинательная характеристика:"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
            })
            val abilityNamesList = choice.spell_abilities.map { abilityNames[it] ?: it }
            choiceContainer.addView(com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL; threshold = 0; isFocusableInTouchMode = false
                hint = "Выберите характеристику"; setOnClickListener { showDropDown() }
                setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, abilityNamesList))
                featureChoices[abilityKey]?.let { val idx = choice.spell_abilities.indexOf(it); if (idx >= 0) setText(abilityNamesList[idx], false) }
                setOnItemClickListener { _, _, pos, _ ->
                    val selected = choice.spell_abilities.getOrNull(pos)
                    featureChoices[abilityKey] = selected
                    onFeatureChoiceChanged(abilityKey, selected)
                }
            })
        }

        // Фиксированные всегда подготовленные заклинания
        if (choice.fixed_spells.isNotEmpty()) {
            val names = choice.fixed_spells.joinToString(", ") { contentRepo.resolveName(it) ?: it.substringAfterLast(":") }
            choiceContainer.addView(TextView(ctx).apply {
                text = "Всегда подготовлены: $names"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 8.dp(ctx), 0, 0)
            })
        }

        // Выбор заклинаний (части pick_spells)
        choice.pick_spells.forEachIndexed { index, spec ->
            val partKey = "${feature.id}_spell$index"
            val selected = featureMultiChoices[partKey]?.filterNotNull() ?: emptyList()
            val required = requiredSpellPickCount(spec)
            choiceContainer.addView(TextView(ctx).apply {
                text = spellPickLabel(spec) + " (${selected.size}/$required):"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
            })
            for (spellId in selected) {
                val name = contentRepo.resolveName(spellId) ?: spellId.substringAfterLast(":")
                choiceContainer.addView(TextView(ctx).apply {
                    text = "• $name"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                })
            }
            choiceContainer.addView(com.google.android.material.button.MaterialButton(ctx).apply {
                text = "Выбрать заклинания"
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    onPickFeatSpellPart(feature.id, index, spec, featureMultiChoices[partKey]?.filterNotNull() ?: emptyList(), required)
                }
            })
        }

        container.addView(choiceContainer)
    }

    private fun requiredSpellPickCount(spec: com.herocraft24.core.model.SpellPickSpec): Int =
        if (spec.proficiency_count) 2 + (characterLevel - 1) / 4 else spec.cantrips + spec.spells

    private fun spellPickLabel(spec: com.herocraft24.core.model.SpellPickSpec): String = when {
        spec.ritual_only -> "Ритуальные заклинания 1-го уровня"
        spec.options.isNotEmpty() -> "Заклинание из списка"
        spec.cantrips > 0 && spec.spells > 0 -> "Заговор и заклинание 1-го уровня"
        spec.schools.isNotEmpty() -> "Заклинание 1-го уровня (${spec.schools.joinToString("/") { UiLocalizer.school(it) }})"
        spec.spell_level == 0 -> "Заговор"
        else -> "Заклинание ${spec.spell_level}-го уровня"
    }

    fun updateFeatSpellPart(featureId: String, partIndex: Int, selected: List<String>) {
        featureMultiChoices["${featureId}_spell$partIndex"] = selected.toMutableList()
        onFeatureMultiChoiceChanged("${featureId}_spell$partIndex", selected)
        notifyDataSetChanged()
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
        featBearingFeatures.add(feature.id)
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
                val taken = takenFeatLocalIds(feature.id)
                val candidates = (if (choice.options.isNotEmpty()) choice.options
                else contentRepo.getFeatIds().filter { contentRepo.getFeat(it)?.category == choice.category })
                    .filter { isFeatPickable(it, contentRepo.getFeat(it), taken) }
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
        val previous = featureChoices[featureId]
        featureChoices[featureId] = selectedId
        onFeatureChoiceChanged(featureId, selectedId)
        if (featureId in asiOrFeatFeatures) onFeatSelected(featureId, selectedId)
        if (featureId in featBearingFeatures) {
            previous?.let { extraSelectedFeats.remove(it) }
            selectedId?.let { extraSelectedFeats.add(it) }
        }
        notifyDataSetChanged()
    }

    /**
     * Локальные id (без префикса пака) черт, которые уже заняты: взятые персонажем
     * плюс выбранные на текущем экране. [excludeFeatureId] — умение, для которого
     * строится список: его собственный текущий выбор занятым не считается.
     */
    private fun takenFeatLocalIds(excludeFeatureId: String? = null): Set<String> {
        val own = if (excludeFeatureId != null) featureChoices[excludeFeatureId] else null
        return (selectedFeats + extraSelectedFeats)
            .filter { it != own }
            .map { it.substringAfterLast(":") }
            .toSet()
    }

    /** Черта доступна для выбора, если она повторяемая или ещё не занята. */
    private fun isFeatPickable(featId: String, feat: Feat?, taken: Set<String>): Boolean {
        if (feat == null) return false
        if (feat.repeatable) return true
        return featId.substringAfterLast(":") !in taken
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
        featBearingFeatures.add(feature.id)
        val allFeatIds = contentRepo.getFeatIds()
        // Пересчитывается при каждом открытии пикера: черты, выбранные на этом же
        // экране ранее, должны переставать быть доступными
        fun availableFeatIds(): List<String> {
            val taken = takenFeatLocalIds(feature.id)
            return allFeatIds.filter { featId ->
                val feat = contentRepo.getFeat(featId) ?: return@filter false
                if (feat.category == "epic_boon" && !allowEpicBoons) return@filter false
                isFeatPickable(featId, feat, taken)
            }
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
                    availableFeatIds(),
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
