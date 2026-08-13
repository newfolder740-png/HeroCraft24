package com.herocraft24.feature.characters

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.core.model.SpellSchool
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.dp
import com.herocraft24.core.ui.util.schoolColor
import com.herocraft24.core.ui.widget.FilterBottomSheet
import com.herocraft24.core.ui.widget.FilterGroup
import com.herocraft24.core.ui.widget.FilterOption
import com.herocraft24.core.ui.widget.SearchBarView
import kotlinx.coroutines.launch

class SheetSpellsFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()

    private val abNames = mapOf(
        "strength" to "СИЛ", "dexterity" to "ЛОВ", "constitution" to "ТЕЛ",
        "intelligence" to "ИНТ", "wisdom" to "МДР", "charisma" to "ХАР"
    )

    private val spellcastingAbilities = listOf("intelligence", "wisdom", "charisma")

    private enum class SortMode(val label: String) {
        LEVEL_ASC("Уровень ↑"),
        LEVEL_DESC("Уровень ↓"),
        NAME_ASC("Имя А–Я"),
        NAME_DESC("Имя Я–А"),
        SCHOOL_ASC("Школа А–Я")
    }

    private data class PreparedSpellFilters(
        val levels: Set<Int> = emptySet(),
        val schools: Set<SpellSchool> = emptySet(),
        val ritual: Boolean? = null,
        val concentration: Boolean? = null,
        val components: Set<String> = emptySet(),
        val castingTimes: Set<String> = emptySet()
    ) {
        val isActive: Boolean
            get() = levels.isNotEmpty() || schools.isNotEmpty() || ritual != null ||
                concentration != null || components.isNotEmpty() || castingTimes.isNotEmpty()
    }

    private var searchQuery: String = ""
    private var sortMode: SortMode = SortMode.LEVEL_ASC
    private var activeFilters: PreparedSpellFilters = PreparedSpellFilters()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_spells, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val charId = arguments?.getString("characterId") ?: return
        val char = vm.getCharacter(charId) ?: return
        val content = view.findViewById<LinearLayout>(R.id.content)
        render(content, char)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                list.find { it.id == charId }?.let { updated ->
                    content.removeAllViews()
                    render(content, updated)
                }
            }
        }
    }

    private fun render(content: LinearLayout, char: CharacterData) {
        val ctx = requireContext()

        val cls = vm.getClassInfo(char.classId)
        val isCaster = cls?.spellcasting != null ||
            SpellSlotsCounter.isSpellcaster(char.classId, char.subclassId)
        val hasInnateSpells = char.spells?.innateSpells?.any { it.value.isNotEmpty() } == true

        if (!isCaster && !hasInnateSpells) {
            content.addView(TextView(ctx).apply {
                text = "Этот класс не заклинает заклинания"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(32, 64, 32, 32)
            })
            return
        }

        val effectiveAbility = if (isCaster) {
            vm.getEffectiveSpellcastingAbility(char)
        } else {
            char.speciesSpellAbility ?: "charisma"
        }
        val abMod = vm.modifier(char.abilityScores[effectiveAbility] ?: 10)
        val spellAttack = char.proficiencyBonus + abMod
        val spellDC = 8 + char.proficiencyBonus + abMod

        // ── Spell stats table with spellcasting ability dropdown ──
        val statsCard = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8.dp(ctx))
            }
            radius = 16f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
        }
        val statsTable = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(ctx), 8.dp(ctx), 12.dp(ctx), 8.dp(ctx))
        }

        statsTable.addView(buildAbilityDropdownRow(ctx, char, effectiveAbility, abMod))
        statsTable.addView(tableRow("Сл. спасброска", "", "$spellDC"))
        statsTable.addView(tableRow("Бонус атаки", "", formatBonus(spellAttack)))
        statsCard.addView(statsTable)
        content.addView(statsCard)

        // ── Spell slots — dynamic frames with 4-pointed stars ──
        if (isCaster) {
            val slots = vm.getEffectiveSpellSlots(char)
            if (slots.isNotEmpty()) {
                sectionTitle(content, "Ячейки заклинаний")
                val sortedSlots = slots.toSortedMap(compareBy<String> { it.toIntOrNull() ?: 0 })
                for ((level, slot) in sortedSlots) {
                    content.addView(buildSlotFrame(ctx, char.id, level, slot.total, slot.used))
                }
            }
        }

        // ── Prepared spells section ──
        val preparedHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16.dp(ctx), 0, 4.dp(ctx))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        preparedHeader.addView(TextView(ctx).apply {
            text = "Подготовленные заклинания"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (isCaster) {
            preparedHeader.addView(AppCompatButton(ctx).apply {
                text = "+"
                setPadding(16.dp(ctx), 0, 16.dp(ctx), 0)
                minimumWidth = 0
                minHeight = 0
                minWidth = 0
                minimumHeight = 0
                background = null
                textSize = 20f
                setOnClickListener {
                    SpellPickerDialogFragment.newInstance(char.id, effectiveAbility)
                        .show(childFragmentManager, "SpellPicker")
                }
            })
        }
        content.addView(preparedHeader)

        // ── Prepared spells controls ──
        content.addView(buildPreparedControls(ctx))

        // ── Prepared spell cards container ──
        currentPreparedContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        currentChar = char
        currentAbility = effectiveAbility
        content.addView(currentPreparedContainer)
        renderPreparedSpells()
    }

    private var currentPreparedContainer: LinearLayout? = null
    private var currentChar: CharacterData? = null
    private var currentAbility: String = "intelligence"

    private fun buildPreparedControls(ctx: android.content.Context): View {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4.dp(ctx), 0, 8.dp(ctx))
        }

        // Search row
        val searchRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchInput = SearchBarView(ctx).apply {
            setOnQueryListener { query ->
                searchQuery = query.trim()
                renderPreparedSpells()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        searchRow.addView(searchInput)

        val sortBtn = MaterialButton(ctx).apply {
            text = "Сорт."
            setPadding(6.dp(ctx), 0, 6.dp(ctx), 0)
            minWidth = 0
            minimumWidth = 0
            elevation = 0f
            isClickable = true
            setBackgroundColor(ContextCompat.getColor(ctx, com.google.android.material.R.color.m3_sys_color_light_surface_container_high))
            strokeColor = ContextCompat.getColorStateList(ctx, com.google.android.material.R.color.m3_sys_color_light_outline_variant)
            strokeWidth = 1
            setOnClickListener { showSortDialog() }
        }
        sortBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 8.dp(ctx) }

        val filterBtn = MaterialButton(ctx).apply {
            text = "Фильтр"
            setPadding(6.dp(ctx), 0, 6.dp(ctx), 0)
            minWidth = 0
            minimumWidth = 0
            elevation = 0f
            isClickable = true
            setBackgroundColor(ContextCompat.getColor(ctx, com.google.android.material.R.color.m3_sys_color_light_surface_container_high))
            strokeColor = ContextCompat.getColorStateList(ctx, com.google.android.material.R.color.m3_sys_color_light_outline_variant)
            strokeWidth = 1
            setOnClickListener { showFilterDialog() }
        }
        filterBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 8.dp(ctx) }

        val clearFiltersImg = ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(42.dp(ctx), 42.dp(ctx)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            visibility = if (activeFilters.isActive) View.VISIBLE else View.INVISIBLE
            setPadding(6.dp(ctx), 6.dp(ctx), 6.dp(ctx), 6.dp(ctx))
            setOnClickListener {
                activeFilters = PreparedSpellFilters()
                renderPreparedSpells()
            }
        }

        searchRow.addView(sortBtn)
        searchRow.addView(filterBtn)
        clearFiltersImg.visibility = if (activeFilters.isActive) View.VISIBLE else View.INVISIBLE
        searchRow.addView(clearFiltersImg)
        container.addView(searchRow)

        return container
    }

    private fun renderPreparedSpells() {
        val container = currentPreparedContainer ?: return
        val char = currentChar ?: return
        val ability = currentAbility
        val ctx = requireContext()
        container.removeAllViews()

        var preparedSpells = vm.getPreparedSpellSummaries(char, ability)
        preparedSpells = applySearch(preparedSpells)
        preparedSpells = applyFilters(preparedSpells)
        preparedSpells = applySort(preparedSpells)

        val innateSpellIds = vm.getInnateSpellIds(char, ability)
        val alwaysPreparedIds = vm.getAlwaysPreparedSpellIds(char, ability)

        if (preparedSpells.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "Нет подготовленных заклинаний"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(0xFF666666.toInt())
                setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
            })
        } else {
            for (spell in preparedSpells) {
                val alwaysPrepared = spell.fullId in alwaysPreparedIds
                val deletable = spell.fullId !in innateSpellIds && !alwaysPrepared
                container.addView(buildPreparedSpellCard(ctx, char, spell, ability, deletable, alwaysPrepared))
            }
        }

        // Update "clear filters" button visibility if needed
        (currentPreparedContainer?.parent as? ViewGroup)?.let { parent ->
            val controls = parent.getChildAt(parent.indexOfChild(container) - 1) as? LinearLayout
            controls?.let { updateClearFiltersVisibility(it) }
        }
    }

    private fun updateClearFiltersVisibility(controls: LinearLayout) {
        var clearBtn: MaterialButton? = null
        for (i in 0 until controls.childCount) {
            val child = controls.getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val btn = child.getChildAt(j)
                    if (btn is MaterialButton && btn.text == "Сброс") {
                        clearBtn = btn
                    }
                }
            }
        }
        clearBtn?.isVisible = activeFilters.isActive
    }

    private fun applySearch(spells: List<SpellSummary>): List<SpellSummary> {
        if (searchQuery.isBlank()) return spells
        val tokens = searchQuery.lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }
        if (tokens.isEmpty()) return spells
        return spells.filter { spell ->
            tokens.all { token ->
                spell.name.lowercase().contains(token) ||
                spell.tags.any { it.lowercase().contains(token) } ||
                spell.school.lowercase().contains(token)
            }
        }
    }

    private fun applyFilters(spells: List<SpellSummary>): List<SpellSummary> {
        return spells.filter { spell ->
            if (activeFilters.levels.isNotEmpty() && spell.level !in activeFilters.levels) return@filter false
            if (activeFilters.schools.isNotEmpty() && SpellSchool.fromValue(spell.school) !in activeFilters.schools) return@filter false
            if (activeFilters.ritual != null && spell.ritual != activeFilters.ritual) return@filter false
            if (activeFilters.concentration != null && spell.concentration != activeFilters.concentration) return@filter false
            if (activeFilters.components.isNotEmpty() && !matchesComponentFilter(spell, activeFilters.components)) return@filter false
            if (activeFilters.castingTimes.isNotEmpty() && !matchesCastingTimeFilter(spell.castingTime, activeFilters.castingTimes)) return@filter false
            true
        }
    }

    private fun applySort(spells: List<SpellSummary>): List<SpellSummary> {
        return when (sortMode) {
            SortMode.LEVEL_ASC -> spells.sortedWith(compareBy<SpellSummary> { it.level }.thenBy { it.name.lowercase() })
            SortMode.LEVEL_DESC -> spells.sortedWith(compareByDescending<SpellSummary> { it.level }.thenBy { it.name.lowercase() })
            SortMode.NAME_ASC -> spells.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> spells.sortedByDescending { it.name.lowercase() }
            SortMode.SCHOOL_ASC -> spells.sortedWith(compareBy<SpellSummary> { UiLocalizer.school(it.school) }.thenBy { it.name.lowercase() })
        }
    }

    private fun showSortDialog() {
        val options = SortMode.entries.toTypedArray()
        val labels = options.map { it.label }.toTypedArray()
        val current = sortMode.ordinal
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Сортировка")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                sortMode = options[which]
                renderPreparedSpells()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showFilterDialog() {
        val sheet = FilterBottomSheet()
        val groups = buildPreparedFilterGroups()
        val selectedMap = mutableMapOf<String, Set<String>>()
        if (activeFilters.levels.isNotEmpty()) selectedMap["levels"] = activeFilters.levels.map { it.toString() }.toSet()
        if (activeFilters.schools.isNotEmpty()) selectedMap["schools"] = activeFilters.schools.map { it.raw }.toSet()
        activeFilters.ritual?.let { selectedMap["ritual"] = setOf(if (it) "yes" else "no") }
        activeFilters.concentration?.let { selectedMap["concentration"] = setOf(if (it) "yes" else "no") }
        if (activeFilters.components.isNotEmpty()) selectedMap["components"] = activeFilters.components
        if (activeFilters.castingTimes.isNotEmpty()) selectedMap["casting_times"] = activeFilters.castingTimes
        sheet.setGroups(groups)
        sheet.setSelected(selectedMap)
        sheet.setCallbacks(
            onApply = { result ->
                activeFilters = filtersFromMap(result)
                renderPreparedSpells()
            },
            onReset = {
                activeFilters = PreparedSpellFilters()
                renderPreparedSpells()
            }
        )
        sheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun buildPreparedFilterGroups(): List<FilterGroup> {
        return listOf(
            FilterGroup("levels", "Уровень", listOf(
                FilterOption("0", "Заговор"),
                FilterOption("1", "1"), FilterOption("2", "2"), FilterOption("3", "3"),
                FilterOption("4", "4"), FilterOption("5", "5"), FilterOption("6", "6"),
                FilterOption("7", "7"), FilterOption("8", "8"), FilterOption("9", "9")
            )),
            FilterGroup("schools", "Школа", listOf(
                FilterOption("evocation", "Воплощение"),
                FilterOption("illusion", "Иллюзия"),
                FilterOption("necromancy", "Некромантия"),
                FilterOption("abjuration", "Ограждение"),
                FilterOption("enchantment", "Очарование"),
                FilterOption("transmutation", "Преобразование"),
                FilterOption("conjuration", "Призыв"),
                FilterOption("divination", "Прорицание")
            )),
            FilterGroup("ritual", "Ритуал", listOf(FilterOption("yes", "Да"), FilterOption("no", "Нет"))),
            FilterGroup("concentration", "Концентрация", listOf(FilterOption("yes", "Да"), FilterOption("no", "Нет"))),
            FilterGroup("components", "Компоненты", listOf(
                FilterOption("V", "V"),
                FilterOption("S", "S"),
                FilterOption("M", "M"),
                FilterOption("M_cost", "M со стоимостью"),
                FilterOption("M_consumed", "M расходуемый"),
                FilterOption("no_V", "Без V"),
                FilterOption("no_S", "Без S"),
                FilterOption("no_M", "Без M")
            )),
            FilterGroup("casting_times", "Время сотворения", listOf(
                FilterOption("action", "Действие"),
                FilterOption("bonus", "Бонусное действие"),
                FilterOption("reaction", "Реакция"),
                FilterOption("minutes", "Минуты"),
                FilterOption("hours", "Часы")
            ))
        )
    }

    private fun filtersFromMap(map: Map<String, Set<String>>): PreparedSpellFilters {
        return PreparedSpellFilters(
            levels = map["levels"]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            schools = map["schools"]?.mapNotNull { schoolFromString(it) }?.toSet() ?: emptySet(),
            ritual = map["ritual"]?.firstOrNull()?.let { it == "yes" },
            concentration = map["concentration"]?.firstOrNull()?.let { it == "yes" },
            components = map["components"] ?: emptySet(),
            castingTimes = map["casting_times"] ?: emptySet()
        )
    }

    private fun schoolFromString(value: String): SpellSchool? =
        try { SpellSchool.fromValue(value) } catch (_: Exception) { null }

    private fun matchesComponentFilter(spell: SpellSummary, filterComponents: Set<String>): Boolean {
        for (fc in filterComponents) {
            val matches = when (fc) {
                "V" -> "V" in spell.components
                "S" -> "S" in spell.components
                "M" -> "M" in spell.components
                "M_cost" -> "M" in spell.components && spell.materialHasCost
                "M_consumed" -> "M" in spell.components && spell.materialConsumable
                "no_V" -> "V" !in spell.components
                "no_S" -> "S" !in spell.components
                "no_M" -> "M" !in spell.components
                else -> false
            }
            if (!matches) return false
        }
        return true
    }

    private fun matchesCastingTimeFilter(castingTime: String, filterTimes: Set<String>): Boolean {
        for (ft in filterTimes) {
            val matches = when (ft) {
                "action" -> castingTime.startsWith("Действие") || castingTime.contains("Действие")
                "bonus" -> castingTime.contains("Бонусное действие")
                "reaction" -> castingTime.contains("Реакция")
                "minutes" -> castingTime.contains("минут") || castingTime.contains("Минут")
                "hours" -> castingTime.contains("час") || castingTime.contains("Час")
                else -> false
            }
            if (matches) return true
        }
        return false
    }

    private fun buildAbilityDropdownRow(
        ctx: android.content.Context,
        char: CharacterData,
        currentAbility: String,
        abMod: Int
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        row.addView(TextView(ctx).apply {
            text = "Мод. закл. хар."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })

        val dropdownItems = spellcastingAbilities.map { abNames[it] ?: it }
        val dropdown = MaterialAutoCompleteTextView(ctx).apply {
            setText(abNames[currentAbility] ?: currentAbility, false)
            inputType = android.text.InputType.TYPE_NULL
            threshold = 0
            dropDownWidth = 600
            minWidth = 0
            isFocusableInTouchMode = false
            setOnClickListener { showDropDown() }
            setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, dropdownItems))
            setOnItemClickListener { _, _, position, _ ->
                val selectedAbility = spellcastingAbilities[position]
                vm.setSpellcastingAbilityOverride(char.id, selectedAbility)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8.dp(ctx), 0, 8.dp(ctx), 0) }
        }
        row.addView(dropdown)

        row.addView(TextView(ctx).apply {
            text = formatBonus(abMod)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTypeface(typeface, Typeface.BOLD)
        })

        return row
    }

    private fun buildSlotFrame(
        ctx: android.content.Context,
        charId: String,
        level: String,
        total: Int,
        used: Int
    ): View {
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 4.dp(ctx), 0, 4.dp(ctx))
            }
            radius = 12f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainer))
            strokeWidth = 0
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(ctx), 8.dp(ctx), 12.dp(ctx), 8.dp(ctx))
        }
        inner.addView(TextView(ctx).apply {
            text = "Уровень $level"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
        })
        val starsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.dp(ctx), 0, 0)
        }
        for (i in 0 until total) {
            val isFilled = i < (total - used)
            val star = ImageButton(ctx).apply {
                setImageResource(
                    if (isFilled) R.drawable.ic_spell_slot_filled else R.drawable.ic_spell_slot_empty
                )
                background = null
                setPadding(4.dp(ctx), 4.dp(ctx), 4.dp(ctx), 4.dp(ctx))
                layoutParams = LinearLayout.LayoutParams(36.dp(ctx), 36.dp(ctx))
                setOnClickListener {
                    if (isFilled) {
                        vm.decrementSpellSlot(charId, level)
                    } else {
                        vm.incrementSpellSlot(charId, level)
                    }
                }
            }
            starsRow.addView(star)
        }
        inner.addView(starsRow)
        card.addView(inner)
        return card
    }

    private fun buildPreparedSpellCard(
        ctx: android.content.Context,
        char: CharacterData,
        spell: SpellSummary,
        ability: String,
        deletable: Boolean = true,
        alwaysPrepared: Boolean = false
    ): View {
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(16.dp(ctx), 4.dp(ctx), 16.dp(ctx), 4.dp(ctx))
            }
            radius = 36f
            cardElevation = 0f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
            isClickable = true
            isFocusable = true
            foreground = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use { ta ->
                ta.getDrawable(0)
            }
        }
        val cardContent = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dp(ctx), 12.dp(ctx), 12.dp(ctx), 12.dp(ctx))
        }

        val borderColor = ctx.schoolColor(SpellSchool.fromValue(spell.school))
        cardContent.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(4.dp(ctx), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = 12.dp(ctx)
            }
            setBackgroundColor(borderColor)
        })

        val textContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textContainer.addView(TextView(ctx).apply {
            text = spell.name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })

        val levelStr = if (spell.level == 0) "Заговор" else "${spell.level} уровень"
        val schoolRu = UiLocalizer.school(spell.school)
        // Get class from innateSpellSources (the actual source of the spell for this character)
        val sourceClassId = vm.getSpellSource(char, spell.fullId)
        val classStr = if (sourceClassId != null) {
            " • " + (vm.getClassInfo(sourceClassId)?.name?.get() ?: UiLocalizer.className(sourceClassId))
        } else {
            " "
        }
        textContainer.addView(TextView(ctx).apply {
            text = "$levelStr • $schoolRu$classStr"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
        })

        val badges = mutableListOf<String>()
        if (spell.concentration) badges.add("Концентрация")
        if (spell.ritual) badges.add("Ритуал")
        if (spell.components.isNotEmpty()) {
            badges.add(spell.components.joinToString("/") { c ->
                when (c.uppercase()) { "V" -> "В"; "S" -> "С"; "M" -> "М"; else -> c }
            })
        }
        spell.damageType?.let { dt -> badges.add(dt.replaceFirstChar { it.uppercase() }) }
        if (badges.isNotEmpty()) {
            textContainer.addView(TextView(ctx).apply {
                text = badges.joinToString(" • ")
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(resolveColor(android.R.attr.textColorSecondary))
                setPadding(0, 4.dp(ctx), 0, 0)
            })
        }

        cardContent.addView(textContainer)

        if (alwaysPrepared) {
            cardContent.addView(ImageView(ctx).apply {
                setImageResource(android.R.drawable.ic_lock_idle_lock)
                background = null
                setPadding(8.dp(ctx), 8.dp(ctx), 0, 8.dp(ctx))
                layoutParams = LinearLayout.LayoutParams(32.dp(ctx), 32.dp(ctx)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            })
        } else if (deletable) {
            val deleteBtn = ImageButton(ctx).apply {
                setImageResource(R.drawable.ic_delete)
                background = null
                setPadding(8.dp(ctx), 8.dp(ctx), 0, 8.dp(ctx))
                layoutParams = LinearLayout.LayoutParams(32.dp(ctx), 32.dp(ctx)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                setOnClickListener { vm.removePreparedSpell(char.id, spell.fullId, ability) }
            }
            cardContent.addView(deleteBtn)
        }

        card.addView(cardContent)
        card.setOnClickListener {
            SpellDetailSheetDialog.newInstance(spell.fullId, char.id, ability)
                .show(childFragmentManager, "SpellDetail")
        }
        return card
    }

    private fun tableRow(label: String, value2: String, value3: String): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(TextView(ctx).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
            addView(TextView(ctx).apply {
                text = value2
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(8.dp(ctx), 0, 8.dp(ctx), 0)
            })
            addView(TextView(ctx).apply {
                text = value3
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTypeface(typeface, Typeface.BOLD)
            })
        }
    }

    private fun sectionTitle(container: LinearLayout, title: String) {
        container.addView(TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 12.dp(context), 0, 4.dp(context))
        })
    }

    private fun formatBonus(value: Int) = if (value >= 0) "+$value" else "$value"

    private fun resolveColor(attr: Int): Int {
        val ta = requireContext().theme?.obtainStyledAttributes(intArrayOf(attr))
        val color = ta?.getColor(0, 0) ?: 0
        ta?.recycle()
        return color
    }

    private inline fun <T> android.content.res.TypedArray.use(block: (android.content.res.TypedArray) -> T): T {
        try { return block(this) } finally { recycle() }
    }
}
