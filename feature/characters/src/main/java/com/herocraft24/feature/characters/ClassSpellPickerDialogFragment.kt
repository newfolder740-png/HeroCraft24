package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.herocraft24.core.model.SpellSchool
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.widget.FilterBottomSheet
import com.herocraft24.core.ui.widget.FilterGroup
import com.herocraft24.core.ui.widget.FilterOption
import com.herocraft24.feature.characters.databinding.DialogSpellPickerBinding
import kotlinx.coroutines.launch

/**
 * Dialog for selecting class spells during feature creation/level-up.
 * Supports selecting a fixed number of cantrips and level 1+ spells from a specific class list.
 */
class ClassSpellPickerDialogFragment : DialogFragment() {

    private var _binding: DialogSpellPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var classFilter: String = ""
    private var cantripsRequired: Int = 0
    private var spellsRequired: Int = 0
    private var charId: String = ""
    private var ability: String = "intelligence"
    private var allSpells: List<SpellSummary> = emptyList()
    private var searchQuery: String = ""
    private var sortMode: SortMode = SortMode.LEVEL_ASC
    private var activeFilters: PreparedSpellFilters = PreparedSpellFilters()

    private val selectedIds = mutableSetOf<String>()
    private lateinit var adapter: SpellPickerAdapter

    private var onResultListener: ((List<String>) -> Unit)? = null

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

    companion object {
        private const val ARG_CLASS_FILTER = "classFilter"
        private const val ARG_CANTRIPS = "cantrips"
        private const val ARG_SPELLS = "spells"
        private const val ARG_SELECTED = "selected"
        private const val ARG_CHAR_ID = "charId"
        private const val ARG_ABILITY = "ability"

        fun newInstance(
            classFilter: String,
            cantrips: Int,
            spells: Int,
            selected: List<String> = emptyList(),
            charId: String = "",
            ability: String = "intelligence"
        ): ClassSpellPickerDialogFragment {
            return ClassSpellPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CLASS_FILTER, classFilter)
                    putInt(ARG_CANTRIPS, cantrips)
                    putInt(ARG_SPELLS, spells)
                    putStringArrayList(ARG_SELECTED, ArrayList(selected))
                    putString(ARG_CHAR_ID, charId)
                    putString(ARG_ABILITY, ability)
                }
            }
        }
    }

    fun setOnResultListener(listener: (List<String>) -> Unit) {
        onResultListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            classFilter = it.getString(ARG_CLASS_FILTER) ?: ""
            cantripsRequired = it.getInt(ARG_CANTRIPS, 0)
            spellsRequired = it.getInt(ARG_SPELLS, 0)
            charId = it.getString(ARG_CHAR_ID) ?: ""
            ability = it.getString(ARG_ABILITY) ?: "intelligence"
            selectedIds.addAll(it.getStringArrayList(ARG_SELECTED) ?: emptyList())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SpellPickerAdapter(
            onItemClick = { spell ->
                SpellDetailSheetDialog.newInstance(spell.fullId, charId, ability)
                    .show(childFragmentManager, "SpellDetail")
            },
            onAddClick = { spell ->
                if (!canSelect(spell) && spell.fullId !in selectedIds) return@SpellPickerAdapter
                toggleSelection(spell)
            },
            isSelected = { spell -> spell.fullId in selectedIds }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchBar.setOnQueryListener { query ->
            searchQuery = query.lowercase().trim()
            refreshList()
        }
        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.btnFilter.setOnClickListener { showFilterDialog() }

        binding.emptyView.text = "Нет доступных заклинаний"
        binding.confirmButton.visibility = View.VISIBLE
        binding.confirmButton.setOnClickListener {
            onResultListener?.invoke(selectedIds.toList())
            dismiss()
        }

        loadSpells()
    }

    private fun canSelect(spell: SpellSummary): Boolean {
        if (spell.fullId in selectedIds) return true
        val isCantrip = spell.level == 0
        val currentCantrips = selectedIds.count { id -> allSpells.find { it.fullId == id }?.level == 0 }
        val currentSpells = selectedIds.count { id -> allSpells.find { it.fullId == id }?.level?.let { it > 0 } == true }
        val current = if (isCantrip) currentCantrips else currentSpells
        val limit = if (isCantrip) cantripsRequired else spellsRequired
        return current < limit
    }

    private fun toggleSelection(spell: SpellSummary) {
        if (spell.fullId in selectedIds) {
            selectedIds.remove(spell.fullId)
        } else {
            if (!canSelect(spell)) return
            selectedIds.add(spell.fullId)
        }
        updateTitle()
        refreshList()
    }

    private fun updateTitle() {
        val currentCantrips = selectedIds.count { id -> allSpells.find { it.fullId == id }?.level == 0 }
        val currentSpells = selectedIds.count { id -> allSpells.find { it.fullId == id }?.level?.let { it > 0 } == true }
        binding.titleView.text = "Заговоры: $currentCantrips/$cantripsRequired, Заклинания: $currentSpells/$spellsRequired"
    }

    private fun loadSpells() {
        lifecycleScope.launch {
            val raw = vm.getAllSpellSummaries()
            var filtered = raw.filter { it.level in 0..1 }
            val filter = classFilter
            if (filter.isNotBlank()) {
                filtered = filtered.filter { spell ->
                    spell.classes.any { it == filter || it.substringAfterLast(":") == filter.substringAfterLast(":") }
                }
            }
            allSpells = filtered
            updateTitle()
            refreshList()
        }
    }

    private fun refreshList() {
        var filtered = allSpells
        filtered = applySearch(filtered)
        filtered = applyFilters(filtered)
        filtered = applySort(filtered)
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applySearch(spells: List<SpellSummary>): List<SpellSummary> {
        if (searchQuery.isBlank()) return spells
        val tokens = searchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
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
            if (activeFilters.components.isNotEmpty() && !matchesComponentFilter(spell)) return@filter false
            if (activeFilters.castingTimes.isNotEmpty() && !matchesCastingTimeFilter(spell)) return@filter false
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

    private fun matchesComponentFilter(spell: SpellSummary): Boolean {
        val hasV = "V" in spell.components
        val hasS = "S" in spell.components
        val hasM = "M" in spell.components
        val hasMcost = spell.materialHasCost
        val hasMconsumed = spell.materialConsumable
        for (fc in activeFilters.components) {
            when (fc) {
                "V" -> if (!hasV) return false
                "S" -> if (!hasS) return false
                "M" -> if (!hasM) return false
                "M_cost" -> if (!hasMcost) return false
                "M_consumed" -> if (!hasMconsumed) return false
                "no_V" -> if (hasV) return false
                "no_S" -> if (hasS) return false
                "no_M" -> if (hasM) return false
            }
        }
        return true
    }

    private fun matchesCastingTimeFilter(spell: SpellSummary): Boolean {
        val lower = spell.castingTime.lowercase()
        for (ft in activeFilters.castingTimes) {
            when (ft) {
                "action" -> if (!(lower.contains("действие") && !lower.contains("бонус") && !lower.contains("реакция"))) return false
                "bonus" -> if (!lower.contains("бонус")) return false
                "reaction" -> if (!lower.contains("реакция")) return false
                "minutes" -> if (!lower.contains("минут")) return false
                "hours" -> if (!lower.contains("час")) return false
            }
        }
        return true
    }

    private fun showSortDialog() {
        val options = SortMode.entries.toTypedArray()
        val labels = options.map { it.label }.toTypedArray()
        val current = sortMode.ordinal
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Сортировка")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                sortMode = options[which]
                refreshList()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showFilterDialog() {
        val sheet = FilterBottomSheet()
        val groups = buildFilterGroups()
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
                refreshList()
            },
            onReset = {
                activeFilters = PreparedSpellFilters()
                refreshList()
            }
        )
        sheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun buildFilterGroups(): List<FilterGroup> {
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
            levels = (map["levels"] ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet(),
            schools = (map["schools"] ?: emptySet()).mapNotNull { SpellSchool.fromValue(it) }.toSet(),
            ritual = when {
                "yes" in (map["ritual"] ?: emptySet()) -> true
                "no" in (map["ritual"] ?: emptySet()) -> false
                else -> null
            },
            concentration = when {
                "yes" in (map["concentration"] ?: emptySet()) -> true
                "no" in (map["concentration"] ?: emptySet()) -> false
                else -> null
            },
            components = map["components"] ?: emptySet(),
            castingTimes = map["casting_times"] ?: emptySet()
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
