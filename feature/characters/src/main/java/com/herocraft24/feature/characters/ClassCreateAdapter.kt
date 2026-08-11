package com.herocraft24.feature.characters

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.GameClass
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.ExpandableCard
import com.herocraft24.core.ui.util.dp
import com.herocraft24.core.ui.util.resolveColor
import com.herocraft24.feature.characters.databinding.CardClassCreateBinding

class ClassCreateAdapter(
    private val onClassSelected: (String) -> Unit,
    private val onSkillChoiceChanged: (String, List<String>) -> Unit,
    private val onEquipmentChoiceChanged: (String, Int) -> Unit,
    private val onSubclassSelected: (String, String?) -> Unit,
    private val initialSkillChoices: List<String> = emptyList(),
    private val initialEquipmentChoice: Int = 0,
    private val initialSubclassId: String? = null
) : RecyclerView.Adapter<ClassCreateAdapter.ViewHolder>() {

    private var items: List<GameClass> = emptyList()
    private var selectedId: String? = null
    private var expandedPosition: Int = -1
    private val openIds = mutableSetOf<String>()
    private val skillChoices = mutableMapOf<String, MutableList<String?>>()
    private val equipmentChoices = mutableMapOf<String, Int>()
    private val selectedSubclassIds = mutableMapOf<String, String?>()

    fun submitList(list: List<GameClass>, selected: String? = selectedId) {
        items = list
        selectedId = selected
        // Restore initial state for the selected class
        if (selected != null) {
            val cls = items.find { it.id == selected }
            val skills = cls?.skills
            if (skills != null) {
                val count = skills.count
                val restored = MutableList(count) { null as String? }
                initialSkillChoices.forEachIndexed { idx, skill ->
                    if (idx < count) restored[idx] = skill
                }
                skillChoices[selected] = restored
            }
            equipmentChoices[selected] = initialEquipmentChoice
            if (initialSubclassId != null) {
                selectedSubclassIds[selected] = initialSubclassId
            }
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

    fun areAllSkillsSelected(): Boolean {
        val clsId = selectedId ?: return true
        val cls = items.find { it.id == clsId } ?: return true
        val skills = cls.skills ?: return true
        val choices = skillChoices[clsId] ?: return false
        return choices.size == skills.count && choices.all { it != null }
    }

    class ViewHolder(val binding: CardClassCreateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardClassCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cls = items[position]
        val isExpanded = position == expandedPosition
        val isSelected = cls.id == selectedId

        holder.binding.className.text = cls.name.get()
        holder.binding.hitDieLabel.text = "Кость хитов d${cls.hit_die}"
        holder.binding.radioButton.isChecked = isSelected
        holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.binding.headerRow.setOnClickListener {
            if (!isSelected) {
                onClassSelected(cls.id)
                setSelected(cls.id)
            }
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            if (prevExpanded >= 0) notifyItemChanged(prevExpanded)
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
        }

        if (isExpanded) {
            buildExpandedContent(holder.binding.expandedContent, cls, position)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun buildExpandedContent(container: LinearLayout, cls: GameClass, position: Int) {
        container.removeAllViews()
        val ctx = container.context

        // Description
        val (descCard, _) = ExpandableCard.createExpandableCard(
            ctx, title = "Описание", openId = "class_desc_${cls.id}", openIdsSet = openIds
        ) { body ->
            body.addView(TextView(ctx).apply {
                text = cls.description.get()
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        }
        container.addView(descCard)

        // Class table (progression)
        cls.class_table?.let { table ->
            val (tableCard, _) = ExpandableCard.createExpandableCard(
                ctx, title = "Таблица прогрессии", openId = "class_table_${cls.id}", openIdsSet = openIds
            ) { body ->
                body.addView(buildTable(ctx, table.columns, table.rows))
            }
            container.addView(tableCard)
        }

        // Key attributes
        if (cls.key_attributes.isNotEmpty()) {
            val attrContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
            }
            attrContainer.addView(TextView(ctx).apply {
                text = "Ключевые атрибуты:"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setPadding(0, 0, 0, 4.dp(ctx))
            })
            for ((key, value) in cls.key_attributes) {
                attrContainer.addView(makeAttributeRow(ctx, key, value))
            }
            container.addView(attrContainer)
        }

        // Skill selection
        cls.skills?.let { skillChoice ->
            val effectiveFrom = if (skillChoice.from.size == 1 && skillChoice.from[0] == "any") {
                ALL_SKILLS
            } else {
                skillChoice.from
            }
            buildSkillSection(container, cls, ctx, skillChoice.count, effectiveFrom)
        }

        // Starting equipment
        if (cls.starting_equipment.isNotEmpty()) {
            buildEquipmentSection(container, cls, ctx)
        }

        // Class features
        if (cls.features.isNotEmpty()) {
            buildFeaturesSection(container, cls, ctx, position)
        }
    }

    // ─── Table with fixed left column + alternating row colors ─────────────

    private fun buildTable(
        ctx: android.content.Context,
        columns: List<com.herocraft24.core.model.ClassTableColumn>,
        rows: List<com.herocraft24.core.model.ClassTableRow>
    ): View {
        val surface = ctx.resolveColor(com.google.android.material.R.attr.colorSurface)
        val surfaceVariant = ctx.resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val onSurface = ctx.resolveColor(com.google.android.material.R.attr.colorOnSurface)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Fixed left column: level
        val leftTable = android.widget.TableLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(8.dp(ctx), 8.dp(ctx), 0, 8.dp(ctx))
        }
        root.addView(leftTable)

        // Scrollable right columns
        val scroll = android.widget.HorizontalScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }
        val rightTable = android.widget.TableLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 8.dp(ctx), 8.dp(ctx), 8.dp(ctx))
        }
        scroll.addView(rightTable)
        root.addView(scroll)

        // Header
        val headerHeight = 48.dp(ctx)
        val leftHeader = android.widget.TableRow(ctx).apply {
            layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, headerHeight)
            setBackgroundColor(surfaceVariant)
        }
        leftHeader.addView(makeTableCell(ctx, "Ур.", headerHeight, onSurface, isHeader = true))
        leftTable.addView(leftHeader)

        val rightHeader = android.widget.TableRow(ctx).apply {
            layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, headerHeight)
            setBackgroundColor(surfaceVariant)
        }
        for (col in columns) {
            rightHeader.addView(makeTableCell(ctx, breakColumnName(col.name.get()), headerHeight, onSurface, isHeader = true))
        }
        rightTable.addView(rightHeader)

        // Body rows
        val rowHeight = 40.dp(ctx)
        val sortedRows = rows.sortedBy { it.level }
        sortedRows.forEachIndexed { index, row ->
            val rowColor = if (index % 2 == 0) surface else surfaceVariant

            val leftRow = android.widget.TableRow(ctx).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight)
                setBackgroundColor(rowColor)
            }
            leftRow.addView(makeTableCell(ctx, row.level.toString(), rowHeight, onSurface))
            leftTable.addView(leftRow)

            val rightRow = android.widget.TableRow(ctx).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight)
                setBackgroundColor(rowColor)
            }
            for (col in columns) {
                rightRow.addView(makeTableCell(ctx, row.values[col.key] ?: "—", rowHeight, onSurface))
            }
            rightTable.addView(rightRow)
        }

        return root
    }

    private fun makeTableCell(ctx: android.content.Context, text: String, height: Int, textColor: Int, isHeader: Boolean = false): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(textColor)
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(8.dp(ctx), 4.dp(ctx), 8.dp(ctx), 4.dp(ctx))
            if (isHeader) setTypeface(null, Typeface.BOLD)
            layoutParams = android.widget.TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height)
            minWidth = 40.dp(ctx)
        }
    }

    private fun breakColumnName(name: String): String {
        if (name.length <= 4) return name
        val words = name.split(" ")
        if (words.size <= 1) return name
        return words.joinToString("\n")
    }

    // ─── Skill selection ───────────────────────────────────────────────────

    private fun buildSkillSection(
        container: LinearLayout,
        cls: GameClass,
        ctx: android.content.Context,
        count: Int,
        from: List<String>
    ) {
        val skillContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }
        skillContainer.addView(TextView(ctx).apply {
            text = "Владение навыками (выберите $count):"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        // Initialize skill choices tracking
        if (!skillChoices.containsKey(cls.id)) {
            skillChoices[cls.id] = MutableList(count) { null }
        }
        val currentChoices = skillChoices[cls.id]!!

        val dropdowns = mutableListOf<com.google.android.material.textfield.MaterialAutoCompleteTextView>()

        fun availableOptionsFor(dropdownIndex: Int): List<String> {
            val otherSelected = currentChoices.mapIndexed { idx, s -> if (idx != dropdownIndex && s != null) s else null }.filterNotNull()
            return from.filter { it !in otherSelected }.map { UiLocalizer.skill(it) }
        }

        fun updateDropdown(dropdownIndex: Int) {
            val dropdown = dropdowns.getOrNull(dropdownIndex) ?: return
            val opts = availableOptionsFor(dropdownIndex)
            dropdown.setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, opts))
            val currentSkillId = currentChoices[dropdownIndex]
            if (currentSkillId != null) {
                dropdown.setText(UiLocalizer.skill(currentSkillId), false)
            } else {
                dropdown.setText("", false)
            }
        }

        for (i in 0 until count) {
            val label = if (count == 1) "Выберите навык:" else "Навык ${i + 1}:"
            skillContainer.addView(TextView(ctx).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 4.dp(ctx), 0, 0)
            })

            val dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL
                threshold = 0
                isFocusableInTouchMode = false
                hint = "Выберите навык"
                setOnClickListener { showDropDown() }
            }

            dropdown.setOnItemClickListener { _, _, pos, _ ->
                val opts = availableOptionsFor(i)
                val selectedLocalized = opts.getOrNull(pos) ?: return@setOnItemClickListener
                val selectedSkillId = from.find { UiLocalizer.skill(it) == selectedLocalized } ?: return@setOnItemClickListener
                currentChoices[i] = selectedSkillId
                skillChoices[cls.id] = currentChoices
                onSkillChoiceChanged(cls.id, currentChoices.filterNotNull())
                // Update all other dropdowns
                for (j in 0 until count) {
                    if (j != i) dropdown.post { updateDropdown(j) }
                }
            }

            dropdowns.add(dropdown)
            updateDropdown(i)
            skillContainer.addView(dropdown)
        }

        container.addView(skillContainer)
    }

    // ─── Equipment selection ───────────────────────────────────────────────

    private fun buildEquipmentSection(
        container: LinearLayout,
        cls: GameClass,
        ctx: android.content.Context
    ) {
        val equipContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }
        equipContainer.addView(TextView(ctx).apply {
            text = "Начальное снаряжение:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 0, 0, 4.dp(ctx))
        })

        val contentRepo = ContentRepository.get(ctx)

        for (choice in cls.starting_equipment) {
            val choiceLabel = choice.description?.get() ?: "Выберите:"
            equipContainer.addView(TextView(ctx).apply {
                text = choiceLabel
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 4.dp(ctx), 0, 4.dp(ctx))
            })

            val radioGroup = RadioGroup(ctx).apply { orientation = RadioGroup.VERTICAL }
            val currentEquipChoice = equipmentChoices[cls.id] ?: 0
            val radioButtons = mutableListOf<RadioButton>()

            for ((idx, option) in choice.options.withIndex()) {
                val label = buildOptionLabel(option, contentRepo)
                val rb = RadioButton(ctx).apply {
                    text = label
                    isChecked = idx == currentEquipChoice
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            radioButtons.forEachIndexed { i, other ->
                                if (i != idx) other.isChecked = false
                            }
                            equipmentChoices[cls.id] = idx
                            onEquipmentChoiceChanged(cls.id, idx)
                        }
                    }
                }
                radioButtons.add(rb)
                radioGroup.addView(rb)
            }
            equipContainer.addView(radioGroup)
        }

        container.addView(equipContainer)
    }

    private fun buildOptionLabel(option: com.herocraft24.core.model.EquipmentOption, repo: ContentRepository): String {
        val parts = mutableListOf<String>()
        option.description?.get()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (option.items.isNotEmpty()) {
            val itemNames = option.items.mapNotNull { itemOpt ->
                val itemId = itemOpt.item_id
                if (itemId != null) {
                    val name = repo.resolveName(itemId) ?: itemId
                    if (itemOpt.quantity > 1) "$name ×${itemOpt.quantity}" else name
                } else {
                    itemOpt.description?.get()
                }
            }
            if (itemNames.isNotEmpty()) parts.add(itemNames.joinToString(", "))
        }
        if (option.options.isNotEmpty()) {
            val subNames = option.options.mapNotNull { subOpt ->
                val itemId = subOpt.item_id
                if (itemId != null) {
                    val name = repo.resolveName(itemId) ?: itemId
                    if (subOpt.quantity > 1) "$name ×${subOpt.quantity}" else name
                } else {
                    subOpt.description?.get()
                }
            }
            if (subNames.isNotEmpty()) parts.add(subNames.joinToString(", "))
        }
        option.gold?.let { parts.add("$it ЗМ") }
        return if (parts.isNotEmpty()) parts.joinToString(": ") else "Вариант"
    }

    // ─── Key attributes ────────────────────────────────────────────────────

    private fun makeAttributeRow(ctx: android.content.Context, label: String, value: String): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
            addView(TextView(ctx).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(ctx).apply {
                text = value
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 0, 0, 4.dp(ctx))
            })
        }
    }

    // ─── Class features with subclass dropdown ─────────────────────────────

    private fun buildFeaturesSection(
        container: LinearLayout,
        cls: GameClass,
        ctx: android.content.Context,
        position: Int
    ) {
        val contentRepo = ContentRepository.get(ctx)
        val baseFeatures = cls.features.mapNotNull { contentRepo.getFeature(it) }
        if (baseFeatures.isEmpty()) return

        val selectedSub = selectedSubclassIds[cls.id]?.let { contentRepo.getSubclass(it) }
        val effectiveFeatures = buildFeatureList(baseFeatures, selectedSub, contentRepo)

        val (featuresCard, _) = ExpandableCard.createExpandableCard(
            ctx, title = "Умения класса", openId = "class_features_${cls.id}", openIdsSet = openIds
        ) { body ->
            for (feature in effectiveFeatures) {
                val levelSuffix = feature.level?.let { "Ур. $it: " } ?: ""
                val (featureCard, _) = ExpandableCard.createExpandableCard(
                    ctx,
                    title = "$levelSuffix${feature.name.get()}",
                    openId = "class_feat_${feature.id}",
                    openIdsSet = openIds
                ) { fb ->
                    // Subclass choice dropdown
                    if (feature.is_subclass_choice && cls.subclasses.isNotEmpty()) {
                        fb.addView(createSubclassDropdown(ctx, cls, position))
                    }
                    fb.addView(TextView(ctx).apply {
                        text = feature.description.get()
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    })
                }
                body.addView(featureCard)
            }
        }
        container.addView(featuresCard)
    }

    private fun buildFeatureList(
        baseFeatures: List<com.herocraft24.core.model.Feature>,
        selectedSubclass: com.herocraft24.core.model.Subclass?,
        contentRepo: ContentRepository
    ): List<com.herocraft24.core.model.Feature> {
        if (selectedSubclass == null) {
            return baseFeatures
        }

        val remainingByLevel = selectedSubclass.features
            .mapNotNull { contentRepo.getFeature(it) }
            .groupBy { it.level }
            .mapValues { entry -> entry.value.toMutableList() }
            .toMutableMap()

        val result = mutableListOf<com.herocraft24.core.model.Feature>()

        for (feature in baseFeatures) {
            if (feature.is_placeholder) {
                // Replace placeholder with subclass features at this level
                val subs = remainingByLevel.remove(feature.level)
                if (!subs.isNullOrEmpty()) {
                    result.addAll(subs)
                }
                continue
            }

            result.add(feature)

            // If the base feature shares a level with subclass features, append after it
            val subs = remainingByLevel.remove(feature.level)
            if (!subs.isNullOrEmpty()) {
                result.addAll(subs)
            }
        }

        // Any leftover subclass features go at the end
        val remaining = remainingByLevel.values.flatten().sortedBy { it.level }
        result.addAll(remaining)

        return result
    }

    private fun createSubclassDropdown(ctx: android.content.Context, cls: GameClass, position: Int): View {
        val contentRepo = ContentRepository.get(ctx)
        val subclassRefs = cls.subclasses.mapNotNull { fullId ->
            contentRepo.getSubclass(fullId)?.let { fullId to it }
        }
        val names = subclassRefs.map { it.second.name.get() }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }

        val dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            inputType = android.text.InputType.TYPE_NULL
            threshold = 0
            isFocusableInTouchMode = false
            hint = "Выберите подкласс"
            setOnClickListener { showDropDown() }
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))

            // Restore selection
            selectedSubclassIds[cls.id]?.let { id ->
                val index = subclassRefs.indexOfFirst { it.first == id }
                if (index >= 0) setText(names[index], false)
            }

            setOnItemClickListener { _, _, pos, _ ->
                val newId = subclassRefs.getOrNull(pos)?.first
                if (newId != selectedSubclassIds[cls.id]) {
                    selectedSubclassIds[cls.id] = newId
                    onSubclassSelected(cls.id, newId)
                    // Rebuild expanded content to reflect subclass change
                    notifyItemChanged(position)
                }
            }
        }
        container.addView(dropdown)
        return container
    }

    companion object {
        private val ALL_SKILLS = listOf(
            "acrobatics", "animal_handling", "arcana", "athletics",
            "deception", "history", "insight", "intimidation",
            "investigation", "medicine", "nature", "perception",
            "performance", "persuasion", "religion", "sleight_of_hand",
            "stealth", "survival"
        )
    }
}
