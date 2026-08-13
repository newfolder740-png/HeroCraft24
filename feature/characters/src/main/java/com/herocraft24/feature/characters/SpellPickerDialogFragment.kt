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

class SpellPickerDialogFragment : DialogFragment() {

    private var _binding: DialogSpellPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var charId: String? = null
    private var ability: String? = null

    private var allSpells: List<SpellSummary> = emptyList()
    private var searchQuery: String = ""
    private var sortMode: SortMode = SortMode.LEVEL_ASC
    private var activeFilters: PreparedSpellFilters = PreparedSpellFilters()

    private lateinit var adapter: SpellPickerAdapter

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
        private const val ARG_CHAR_ID = "characterId"
        private const val ARG_ABILITY = "ability"

        fun newInstance(characterId: String, ability: String): SpellPickerDialogFragment {
            return SpellPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAR_ID, characterId)
                    putString(ARG_ABILITY, ability)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        charId = arguments?.getString(ARG_CHAR_ID)
        ability = arguments?.getString(ARG_ABILITY)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val char = charId?.let { vm.getCharacter(it) } ?: run { dismiss(); return }
        val ab = ability ?: vm.getEffectiveSpellcastingAbility(char)

        adapter = SpellPickerAdapter(
            onItemClick = { spell ->
                SpellDetailSheetDialog.newInstance(spell.fullId, char.id, ab)
                    .show(childFragmentManager, "SpellDetail")
            },
            onAddClick = { spell ->
                vm.addPreparedSpell(char.id, spell.fullId, ab)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchBar.setOnQueryListener { query ->
            searchQuery = query.lowercase().trim()
            refreshList()
        }
        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.btnFilter.setOnClickListener { showFilterDialog() }

        loadSpells()

        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                charId?.let { id ->
                    list.find { it.id == id }?.let { loadSpells() }
                }
            }
        }
    }

    private fun loadSpells() {
        lifecycleScope.launch {
            allSpells = vm.getAllSpellSummaries()
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
