package com.herocraft24.feature.spells

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.herocraft24.core.model.SpellSchool
import com.herocraft24.core.ui.widget.FilterBottomSheet
import com.herocraft24.core.ui.widget.FilterGroup
import com.herocraft24.core.ui.widget.FilterOption
import com.herocraft24.core.ui.widget.StateViewBinder
import com.herocraft24.feature.spells.databinding.FragmentSpellListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SpellListFragment : Fragment() {

    private var _binding: FragmentSpellListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SpellsViewModel by viewModels()
    private lateinit var stateViewBinder: StateViewBinder
    private lateinit var adapter: SpellListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpellListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateViewBinder = StateViewBinder(binding.contentContainer)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SpellListAdapter(
            favoriteIds = emptySet(),
            onItemClick = { spell ->
                val bundle = Bundle().apply { putString("spellId", spell.fullId) }
                findNavController().navigate(R.id.spellDetail, bundle)
            },
            onFavoriteClick = { spell -> viewModel.toggleFavorite(spell.fullId) }
        )
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.favoriteIds.collectLatest { favorites ->
                adapter.updateFavorites(favorites)
            }
        }

        binding.searchBar.setOnQueryListener { query -> viewModel.setSearchQuery(query) }

        // System back: if search/filters are active, clear them instead of leaving the tab.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.hasActiveSearchOrFilters()) {
                    viewModel.clearSearchAndFilters()
                    binding.searchBar.clear()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnClearFilters.setOnClickListener { viewModel.setFilters(SpellFilters()) }

        // Set sort button to always have transparent background (never changes)
        val theme = requireContext().theme
        val typedValue = android.util.TypedValue()
        val colorOnSurface = if (theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, typedValue, true
            )
        ) {
            typedValue.data
        } else {
            Color.BLACK
        }

        binding.btnSort.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        binding.btnSort.strokeColor = ColorStateList.valueOf(colorOnSurface)
        binding.btnSort.setTextColor(colorOnSurface)

        // Update filter button appearance when filters are applied
        lifecycleScope.launch {
            viewModel.filters.collectLatest { filters ->
                val active = filters.isActive

                val colorPrimary = if (theme.resolveAttribute(
                        com.google.android.material.R.attr.colorPrimary, typedValue, true
                    )
                ) {
                    typedValue.data
                } else {
                    Color.BLUE
                }

                val colorOnPrimary = if (theme.resolveAttribute(
                        com.google.android.material.R.attr.colorOnPrimary, typedValue, true
                    )
                ) {
                    typedValue.data
                } else {
                    Color.WHITE
                }

                binding.btnFilter.backgroundTintList =
                    ColorStateList.valueOf(if (active) colorPrimary else Color.TRANSPARENT)
                binding.btnFilter.strokeColor =
                    ColorStateList.valueOf(if (active) colorPrimary else colorOnSurface)
                binding.btnFilter.setTextColor(if (active) colorOnPrimary else colorOnSurface)
                binding.btnClearFilters.alpha = if (active) 1f else 0f
            }
        }

        lifecycleScope.launch {
            viewModel.filteredSpells.collectLatest { spells ->
                adapter.submitList(spells, viewModel.favoriteIds.value)
                if (spells.isEmpty() && !viewModel.isLoading.value) {
                    stateViewBinder.showEmpty(title = "Нет заклинаний", subtitle = "Измените фильтры или запрос")
                } else {
                    stateViewBinder.hideAll()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                if (loading) stateViewBinder.showLoading()
            }
        }

        lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                error?.let { stateViewBinder.showError(title = it) }
            }
        }
    }

    private fun showFilterDialog() {
        val currentFilters = viewModel.filters.value
        val sheet = FilterBottomSheet()
        val groups = buildSpellFilterGroups().toMutableList()
        groups.add(
            FilterGroup("favorites", "Избранное", listOf(
                FilterOption("show_favorites", "Показывать только избранное")
            ))
        )
        val selectedMap = mutableMapOf<String, Set<String>>(
            "levels" to currentFilters.levels.map { it.toString() }.toSet(),
            "classes" to currentFilters.classes,
            "subclasses" to currentFilters.subclasses,
            "schools" to currentFilters.schools.map { it.raw }.toSet(),
            "ritual" to (if (currentFilters.ritual != null) setOf(if (currentFilters.ritual) "yes" else "no") else emptySet()),
            "concentration" to (if (currentFilters.concentration != null) setOf(if (currentFilters.concentration) "yes" else "no") else emptySet()),
            "components" to currentFilters.components,
            "casting_times" to currentFilters.castingTimes
        )
        if (currentFilters.showFavoritesOnly) selectedMap["favorites"] = setOf("show_favorites")
        sheet.setGroups(groups)
        sheet.setSelected(selectedMap)
        sheet.setCallbacks(
            onApply = { result -> viewModel.setFilters(filtersFromMap(result)) },
            onReset = { viewModel.setFilters(SpellFilters()) }
        )
        sheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun showSortDialog() {
        val options = SpellSortMode.values()
        val labels = options.map { it.label }.toTypedArray()
        val current = viewModel.sortMode.value.ordinal
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Сортировка")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                viewModel.setSortMode(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun buildSpellFilterGroups(): List<FilterGroup> {
        return listOf(
            FilterGroup("levels", "Уровень", listOf(
                FilterOption("0", "Заговор"),
                FilterOption("1", "1"), FilterOption("2", "2"), FilterOption("3", "3"),
                FilterOption("4", "4"), FilterOption("5", "5"), FilterOption("6", "6"),
                FilterOption("7", "7"), FilterOption("8", "8"), FilterOption("9", "9")
            )),
            FilterGroup("classes", "Класс", listOf(
                FilterOption("artificer", "Артефактор"),
                FilterOption("bard", "Бард"),
                FilterOption("wizard", "Волшебник"),
                FilterOption("druid", "Друид"),
                FilterOption("cleric", "Жрец"),
                FilterOption("warlock", "Колдун"),
                FilterOption("paladin", "Паладин"),
                FilterOption("ranger", "Следопыт"),
                FilterOption("sorcerer", "Чародей")
            )),
            FilterGroup("subclasses", "Подкласс", buildSubclassOptions()),
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
            FilterGroup("ritual", "Ритуал", listOf(
                FilterOption("yes", "Да"),
                FilterOption("no", "Нет")
            )),
            FilterGroup("concentration", "Концентрация", listOf(
                FilterOption("yes", "Да"),
                FilterOption("no", "Нет")
            )),
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

    private fun buildSubclassOptions(): List<FilterOption> {
        val opts = mutableListOf<FilterOption>()
        val classSubclasses = linkedMapOf(
            "artificer" to listOf("Алхимик", "Артиллерист", "Боевой кузнец", "Бронник", "Картограф", "Реаниматор"),
            "bard" to listOf("Коллегия очарования", "Коллегия луны", "Коллегия духов"),
            "wizard" to listOf("Иллюзионист", "Оградитель"),
            "druid" to listOf("Круг звёзд", "Круг земли", "Круг луны", "Круг моря"),
            "cleric" to listOf("Домен войны", "Домен жизни", "Домен обмана", "Домен света", "Домен упокоения"),
            "warlock" to listOf("Исчадье", "Великий Древний", "Архифея", "Небожитель", "Нежить"),
            "paladin" to listOf("Клятва возмездия", "Клятва древних", "Клятва преданности", "Клятва славы", "Клятва благородных гениев"),
            "ranger" to listOf("Странник фей", "Сумрачный охотник", "Зимний ходок", "Страж пустошей"),
            "sorcerer" to listOf("Аберрантное чародейство", "Драконье чародейство", "Заводное чародейство", "Чародейство чаропламени", "Теневое чародейство"),
            "barbarian" to listOf("Путь дикого сердца"),
            "fighter" to listOf("Пси воин"),
            "monk" to listOf("Воин тени", "Воин стихий"),
            "rogue" to listOf("Мистический ловкач", "Наследник троицы")
        )
        val nameToId = viewModel.subclassNameToIdMap
        for ((cls, subs) in classSubclasses) {
            opts.add(FilterOption(cls, classNameRu(cls), isParent = true))
            for (sub in subs) {
                val fullId = nameToId[sub]
                if (fullId != null) {
                    opts.add(FilterOption(fullId, sub, indent = 1, parentKey = cls))
                }
            }
        }
        return opts
    }

    private fun classNameRu(key: String): String = when (key) {
        "artificer" -> "Артефактор"
        "bard" -> "Бард"
        "wizard" -> "Волшебник"
        "druid" -> "Друид"
        "cleric" -> "Жрец"
        "warlock" -> "Колдун"
        "paladin" -> "Паладин"
        "ranger" -> "Следопыт"
        "sorcerer" -> "Чародей"
        "barbarian" -> "Варвар"
        "fighter" -> "Воин"
        "monk" -> "Монах"
        "rogue" -> "Плут"
        else -> key
    }

    private fun filtersFromMap(result: Map<String, Set<String>>): SpellFilters {
        val levels = (result["levels"] ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        val classes = result["classes"] ?: emptySet()
        val subclasses = result["subclasses"] ?: emptySet()
        val schools = (result["schools"] ?: emptySet()).map { SpellSchool.fromValue(it) }.toSet()
        val ritualSet = result["ritual"] ?: emptySet()
        val ritual = when {
            "yes" in ritualSet -> true
            "no" in ritualSet -> false
            else -> null
        }
        val concSet = result["concentration"] ?: emptySet()
        val concentration = when {
            "yes" in concSet -> true
            "no" in concSet -> false
            else -> null
        }
        val components = result["components"] ?: emptySet()
        val castingTimes = result["casting_times"] ?: emptySet()
        return SpellFilters(
            levels = levels,
            schools = schools,
            classes = classes,
            subclasses = subclasses,
            concentration = concentration,
            ritual = ritual,
            components = components,
            castingTimes = castingTimes,
            showFavoritesOnly = "show_favorites" in (result["favorites"] ?: emptySet())
        )
    }

    override fun onDestroyView() {
        stateViewBinder.release()
        super.onDestroyView()
        _binding = null
    }
}
