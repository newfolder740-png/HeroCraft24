package com.herocraft24.feature.equipment

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
import com.herocraft24.core.ui.widget.FilterBottomSheet
import com.herocraft24.core.ui.widget.FilterGroup
import com.herocraft24.core.ui.widget.FilterOption
import com.herocraft24.core.ui.widget.StateViewBinder
import com.herocraft24.feature.equipment.databinding.FragmentEquipmentListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EquipmentListFragment : Fragment() {

    private var _binding: FragmentEquipmentListBinding? = null
    private val binding get() = _binding!!
    private val vm: EquipmentViewModel by viewModels()
    private lateinit var stateViewBinder: StateViewBinder
    private lateinit var adapter: EquipmentListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEquipmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stateViewBinder = StateViewBinder(binding.contentContainer)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EquipmentListAdapter(
            favoriteIds = emptySet(),
            onItemClick = { item ->
                val b = Bundle().apply { putString("itemId", item.fullId) }
                findNavController().navigate(R.id.equipmentDetail, b)
            },
            onFavoriteClick = { item -> vm.toggleFavorite(item.fullId) }
        )
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            vm.favoriteIds.collectLatest { favorites ->
                adapter.updateFavorites(favorites)
            }
        }

        binding.searchBar.setOnQueryListener { vm.setSearchQuery(it) }

        // System back: if search/filters are active, clear them instead of leaving the tab.
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (vm.hasActiveSearchOrFilters()) {
                    vm.clearSearchAndFilters()
                    binding.searchBar.clear()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

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
            vm.filters.collectLatest { filters ->
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

        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnClearFilters.setOnClickListener { vm.setFilters(EquipmentFilters()) }

        lifecycleScope.launch {
            vm.filteredItems.collectLatest { items ->
                adapter.submitList(items, vm.favoriteIds.value)
                if (items.isEmpty() && !vm.isLoading.value) {
                    stateViewBinder.showEmpty(title = "Нет предметов", subtitle = "Измените фильтры или запрос")
                } else stateViewBinder.hideAll()
            }
        }
        lifecycleScope.launch {
            vm.isLoading.collectLatest { if (it) stateViewBinder.showLoading() }
        }
    }

    private fun showFilterDialog() {
        val currentFilters = vm.filters.value
        val sheet = FilterBottomSheet()
        val groups = buildEquipmentFilterGroups().toMutableList()
        groups.add(
            FilterGroup("favorites", "Избранное", listOf(
                FilterOption("show_favorites", "Показывать только избранное")
            ))
        )
        val selectedMap = mutableMapOf<String, Set<String>>(
            "categories" to currentFilters.categories,
            "rarities" to currentFilters.rarities,
            "weapon_categories" to currentFilters.weaponCategories,
            "armor_categories" to currentFilters.armorCategories
        )
        if (currentFilters.showFavoritesOnly) selectedMap["favorites"] = setOf("show_favorites")
        sheet.setGroups(groups)
        sheet.setSelected(selectedMap)
        sheet.setCallbacks(
            onApply = { result -> vm.setFilters(filtersFromMap(result)) },
            onReset = { vm.setFilters(EquipmentFilters()) }
        )
        sheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun buildEquipmentFilterGroups(): List<FilterGroup> {
        return listOf(
            FilterGroup("categories", "Тип", listOf(
                FilterOption("weapon", "Оружие"),
                FilterOption("armor", "Доспех"),
                FilterOption("shield", "Щит"),
                FilterOption("adventuring_gear", "Снаряжение приключений"),
                FilterOption("pack", "Набор"),
                FilterOption("tool", "Ремесленный инструмент"),
                FilterOption("instrument", "Инструмент"),
                FilterOption("focus", "Фокусировка"),
                FilterOption("wand", "Волшебная палочка"),
                FilterOption("rod", "Жезл"),
                FilterOption("potion", "Зелье"),
                FilterOption("ring", "Кольцо"),
                FilterOption("staff", "Посох"),
                FilterOption("scroll", "Свиток"),
                FilterOption("wondrous_item", "Чудесная вещь"),
                FilterOption("ammunition", "Боеприпасы")
            )),
            FilterGroup("rarities", "Редкость", listOf(
                FilterOption("non-magic", "Немагический"),
                FilterOption("common", "Обычный"),
                FilterOption("uncommon", "Необычный"),
                FilterOption("rare", "Редкий"),
                FilterOption("very-rare", "Очень редкий"),
                FilterOption("legendary", "Легендарный"),
                FilterOption("artifact", "Артефакт"),
                FilterOption("varies", "Редкость варьируется")
            )),
            FilterGroup("weapon_categories", "Категория оружия", listOf(
                FilterOption("simple_melee", "Простое Рукопашное оружие"),
                FilterOption("martial_melee", "Воинское Рукопашное оружие"),
                FilterOption("simple_ranged", "Простое Дальнобойное оружие"),
                FilterOption("martial_ranged", "Воинское Дальнобойное оружие"),
                FilterOption("ammunition", "Боеприпас")
            )),
            FilterGroup("armor_categories", "Категория доспеха", listOf(
                FilterOption("light_armor", "Лёгкий"),
                FilterOption("medium_armor", "Средний"),
                FilterOption("heavy_armor", "Тяжёлый"),
                FilterOption("shield", "Щит")
            )),
            FilterGroup("all_subcategories", "Подкатегории", listOf(
                FilterOption("simple_melee", "Простое рукопашное"),
                FilterOption("martial_melee", "Воинское рукопашное"),
                FilterOption("simple_ranged", "Простое дальнобойное"),
                FilterOption("martial_ranged", "Воинское дальнобойное"),
                FilterOption("ammunition", "Боеприпас"),
                FilterOption("light_armor", "Лёгкий"),
                FilterOption("medium_armor", "Средний"),
                FilterOption("heavy_armor", "Тяжёлый"),
                FilterOption("shield", "Щит")
            ))
        )
    }

    private fun filtersFromMap(result: Map<String, Set<String>>): EquipmentFilters {
        return EquipmentFilters(
            categories = result["categories"] ?: emptySet(),
            rarities = result["rarities"] ?: emptySet(),
            weaponCategories = result["weapon_categories"] ?: emptySet(),
            armorCategories = result["armor_categories"] ?: emptySet(),
            allSubcategories = result["all_subcategories"] ?: emptySet(),
            showFavoritesOnly = "show_favorites" in (result["favorites"] ?: emptySet())
        )
    }

    private fun showSortDialog() {
        val options = EquipmentSortMode.values()
        val labels = options.map { it.label }.toTypedArray()
        val current = vm.sortMode.value.ordinal
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Сортировка")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                vm.setSortMode(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        stateViewBinder.release()
        super.onDestroyView()
        _binding = null
    }
}
