package com.herocraft24.feature.reference

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
import com.herocraft24.feature.reference.databinding.FragmentReferenceListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ReferenceListFragment : Fragment() {

    private var _binding: FragmentReferenceListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReferenceViewModel by viewModels()

    private var categoryKey: String = ""
    private var categoryLabel: String = ""
    private lateinit var stateViewBinder: StateViewBinder
    private var allItems: List<ReferenceListAdapter.ReferenceListItem> = emptyList<ReferenceListAdapter.ReferenceListItem>()
    private var currentFilterCategories: Set<String> = emptySet()
    private var currentFilterSources: Set<String> = emptySet()
    private var currentFilterArmorTypes: Set<String> = emptySet()
    private var currentFilterRarities: Set<String> = emptySet()
    private var currentFilterMaterials: Set<String> = emptySet()
    private var currentSearchQuery: String = ""
    private var currentSortMode: PreviewSortMode = PreviewSortMode.DEFAULT
    private var currentMonsterSizes: Set<String> = emptySet()
    private var currentMonsterTypes: Set<String> = emptySet()
    private var currentMonsterChallenge: Set<String> = emptySet()
    private var currentMonsterEnvironments: Set<String> = emptySet()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReferenceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryKey = arguments?.getString("categoryKey") ?: "classes"
        categoryLabel = arguments?.getString("categoryLabel") ?: ""

        binding.toolbarTitle.text = categoryLabel
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        stateViewBinder = StateViewBinder(binding.contentContainer)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val showSearchAndFilter = categoryKey in listOf("feats", "species", "backgrounds", "items", "spells", "monsters")
        binding.searchBar.visibility = if (showSearchAndFilter) View.VISIBLE else View.GONE
        binding.btnFilter.visibility = if (showSearchAndFilter) View.VISIBLE else View.GONE
        binding.btnClearFilters.visibility = if (showSearchAndFilter) View.VISIBLE else View.GONE
        val showSort = categoryKey in listOf("species", "backgrounds", "feats", "monsters")
        binding.btnSort.visibility = if (showSort) View.VISIBLE else View.GONE

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

        // Show loading state initially
        stateViewBinder.showLoading()

        // Build the list off the main thread (monster entries may require
        // reading JSON files if the startup preload hasn't finished yet).
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { buildItems() }
            if (_binding == null) return@launch
            allItems = items
            applyFilters()
            updateFilterButtonAppearance()
        }

        if (showSearchAndFilter) {
            binding.searchBar.setOnQueryListener { query ->
                currentSearchQuery = query
                applyFilters()
                updateFilterButtonAppearance()
            }
            binding.btnFilter.setOnClickListener {
                showFilterDialog()
            }
            binding.btnClearFilters.setOnClickListener {
                clearSearchAndFilters()
                binding.searchBar.clear()
                updateFilterButtonAppearance()
            }
        }
        if (showSort) {
            binding.btnSort.setOnClickListener {
                showSortDialog()
            }
        }

        // System back: if search/filters are active, clear them instead of leaving the tab.
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasActiveSearchOrFilters()) {
                    clearSearchAndFilters()
                    binding.searchBar.clear()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun hasActiveFilters(): Boolean =
        currentFilterCategories.isNotEmpty() ||
        currentFilterSources.isNotEmpty() ||
        currentFilterArmorTypes.isNotEmpty() ||
        currentFilterRarities.isNotEmpty() ||
        currentFilterMaterials.isNotEmpty() ||
        currentMonsterSizes.isNotEmpty() ||
        currentMonsterTypes.isNotEmpty() ||
        currentMonsterChallenge.isNotEmpty() ||
        currentMonsterEnvironments.isNotEmpty()

    private fun updateFilterButtonAppearance() {
        val theme = requireContext().theme
        val typedValue = android.util.TypedValue()
        val active = hasActiveFilters()

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

        val colorOnSurface = if (theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, typedValue, true
            )
        ) {
            typedValue.data
        } else {
            Color.BLACK
        }

        binding.btnFilter.backgroundTintList =
            ColorStateList.valueOf(if (active) colorPrimary else Color.TRANSPARENT)
        binding.btnFilter.strokeColor =
            ColorStateList.valueOf(if (active) colorPrimary else colorOnSurface)
        binding.btnFilter.setTextColor(if (active) colorOnPrimary else colorOnSurface)
        binding.btnClearFilters.alpha = if (active) 1f else 0f
    }

    private fun setupFilterDialogCallbacks(sheet: FilterBottomSheet) {
        sheet.setCallbacks(
            onApply = { result ->
                currentFilterCategories = result["category"] ?: emptySet()
                currentFilterSources = result["source"] ?: emptySet()
                currentFilterRarities = result["rarity"] ?: emptySet()
                currentFilterArmorTypes = result["armor_type"] ?: emptySet()
                currentFilterMaterials = result["material"] ?: emptySet()
                currentMonsterSizes = result["monster_size"] ?: emptySet()
                currentMonsterTypes = result["monster_type"] ?: emptySet()
                currentMonsterChallenge = result["monster_challenge"] ?: emptySet()
                currentMonsterEnvironments = result["monster_environment"] ?: emptySet()
                applyFilters()
                updateFilterButtonAppearance()
            },
            onReset = {
                currentFilterCategories = emptySet()
                currentFilterSources = emptySet()
                currentFilterRarities = emptySet()
                currentFilterArmorTypes = emptySet()
                currentFilterMaterials = emptySet()
                currentMonsterSizes = emptySet()
                currentMonsterTypes = emptySet()
                currentMonsterChallenge = emptySet()
                currentMonsterEnvironments = emptySet()
                applyFilters()
                updateFilterButtonAppearance()
            }
        )
    }

    private fun hasActiveSearchOrFilters(): Boolean =
        currentSearchQuery.isNotBlank() ||
        currentFilterCategories.isNotEmpty() ||
        currentFilterSources.isNotEmpty() ||
        currentFilterArmorTypes.isNotEmpty() ||
        currentFilterRarities.isNotEmpty() ||
        currentFilterMaterials.isNotEmpty() ||
        currentMonsterSizes.isNotEmpty() ||
        currentMonsterTypes.isNotEmpty() ||
        currentMonsterChallenge.isNotEmpty() ||
        currentMonsterEnvironments.isNotEmpty()

    private fun clearSearchAndFilters() {
        currentSearchQuery = ""
        currentFilterCategories = emptySet()
        currentFilterSources = emptySet()
        currentFilterArmorTypes = emptySet()
        currentFilterRarities = emptySet()
        currentFilterMaterials = emptySet()
        currentMonsterSizes = emptySet()
        currentMonsterTypes = emptySet()
        currentMonsterChallenge = emptySet()
        currentMonsterEnvironments = emptySet()
        applyFilters()
    }

    private fun buildItems(): List<ReferenceListAdapter.ReferenceListItem> {
        return try {
            val ids = viewModel.getCategoryIds(categoryKey)

            val items = mutableListOf<ReferenceListAdapter.ReferenceListItem>()

            for (fullId in ids) {
                try {
                    val entry = viewModel.getEntryInfo(fullId)
                    if (entry == null) continue

                    val name = entry.name.get()
                    if (name.isBlank()) {
                        continue
                    }

                    val isSwarm = categoryKey == "monsters" && name.startsWith("Рой")
                    // The manifest entry index is keyed by full id, and a class like "druid"
                    // can share its id with a monster. Because monsters are indexed after
                    // classes, the monster entry overwrites the class entry, losing hit_die.
                    // For classes we therefore read the hit die from the full class object.
                    val hitDie = if (categoryKey == "classes") viewModel.getClass(fullId)?.hit_die else null
                    val subcategory = entry.subcategory ?: emptyList()
                    val item = ReferenceListAdapter.ReferenceListItem(
                        fullId = fullId,
                        name = name,
                        subtitle = buildSubtitle(entry, isSwarm, hitDie),
                        category = entry.category ?: "",
                        source = loadSourceAbbreviation(fullId),
                        subcategory = subcategory,
                        rarity = entry.rarity ?: "",
                        materialHasCost = entry.material_has_cost ?: false,
                        materialConsumable = entry.material_consumable ?: false,
                        size = entry.size ?: "",
                        creatureType = entry.type ?: "",
                        challengeRating = entry.challenge_rating ?: 0.0,
                        environment = if (categoryKey == "monsters") {
                            viewModel.getMonster(fullId)?.environment ?: emptyList<String>()
                        } else emptyList<String>(),
                        isSwarm = isSwarm,
                        hitDie = hitDie
                    )
                    items.add(item)
                } catch (e: Exception) {
                    continue
                }
            }

            val collator = java.text.Collator.getInstance(java.util.Locale("ru"))
            if (categoryKey == "monsters") {
                items.sortWith(
                    compareBy<ReferenceListAdapter.ReferenceListItem> { it.challengeRating }
                        .thenComparator { a, b -> collator.compare(a.name, b.name) }
                )
            } else {
                items.sortWith { a, b -> collator.compare(a.name, b.name) }
            }

            return items.toList()
        } catch (e: Exception) {
            throw e
        }
    }

    private fun loadSourceAbbreviation(fullId: String): String {
        try {
            return when (categoryKey) {
                "species" -> viewModel.getSpecies(fullId)?.source?.abbreviation ?: ""
                "backgrounds" -> viewModel.getBackground(fullId)?.source?.abbreviation ?: ""
                "feats" -> viewModel.getFeat(fullId)?.source?.abbreviation ?: ""
                "items" -> viewModel.getItem(fullId)?.source?.abbreviation ?: ""
                "spells" -> viewModel.getSpell(fullId)?.source?.abbreviation ?: ""
                else -> ""
            }
        } catch (e: Exception) {
            println("DEBUG: Error in loadSourceAbbreviation for category '$categoryKey', ID '$fullId': ${e.message}")
            return ""
        }
    }

    private fun applyFilters() {
        var items = allItems

        if (currentSearchQuery.isNotBlank()) {
            val tokens = currentSearchQuery.lowercase().trim().split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                items = items.filter { item ->
                    tokens.all { t ->
                        item.name.lowercase().contains(t) || item.subtitle.lowercase().contains(t)
                    }
                }
            }
        }

        if (currentFilterCategories.isNotEmpty()) {
            items = items.filter { it.category in currentFilterCategories }
        }

        if (currentFilterArmorTypes.isNotEmpty()) {
            items = items.filter { it.subcategory.any { sub -> sub in currentFilterArmorTypes } }
        }

        if (currentFilterRarities.isNotEmpty()) {
            items = items.filter { it.rarity in currentFilterRarities }
        }

        if (currentFilterSources.isNotEmpty()) {
            items = items.filter { it.source in currentFilterSources }
        }

        if ("has_cost" in currentFilterMaterials) {
            items = items.filter { it.materialHasCost }
        }
        if ("consumable" in currentFilterMaterials) {
            items = items.filter { it.materialConsumable }
        }

        if (categoryKey == "monsters") {
            if (currentMonsterSizes.isNotEmpty()) {
                items = items.filter { item ->
                    currentMonsterSizes.any { sizeFilter ->
                        when (sizeFilter) {
                            "swarm_small" -> item.isSwarm && item.size.lowercase() == "small"
                            "swarm_medium" -> item.isSwarm && item.size.lowercase() == "medium"
                            "swarm_large" -> item.isSwarm && item.size.lowercase() == "large"
                            else -> !item.isSwarm && item.size.lowercase() == sizeFilter
                        }
                    }
                }
            }
            if (currentMonsterTypes.isNotEmpty()) {
                items = items.filter { it.creatureType.lowercase() in currentMonsterTypes }
            }
            if (currentMonsterChallenge.isNotEmpty()) {
                items = items.filter { item ->
                    currentMonsterChallenge.any { c -> formatChallengeRating(item.challengeRating) == c }
                }
            }
            if (currentMonsterEnvironments.isNotEmpty()) {
                items = items.filter { item ->
                    item.environment.any { env -> env.lowercase() in currentMonsterEnvironments }
                }
            }
        }

        // Sort per selected preview mode (DEFAULT keeps the order set in loadItems).
        val collator = java.text.Collator.getInstance(java.util.Locale("ru"))
        when (currentSortMode) {
            PreviewSortMode.DEFAULT -> {} // already sorted in loadItems (monsters by CR, others by name)
            is PreviewSortMode.NAME_ASC -> items = items.sortedWith { a, b -> collator.compare(a.name, b.name) }
            is PreviewSortMode.NAME_DESC -> items = items.sortedWith { a, b -> collator.compare(b.name, a.name) }
            is PreviewSortMode.CHALLENGE_ASC -> items = items.sortedWith(
                compareBy<ReferenceListAdapter.ReferenceListItem> { it.challengeRating }
                    .thenComparator { a, b -> collator.compare(a.name, b.name) }
            )
            is PreviewSortMode.CHALLENGE_DESC -> items = items.sortedWith(
                compareByDescending<ReferenceListAdapter.ReferenceListItem> { it.challengeRating }
                    .thenComparator { a, b -> collator.compare(a.name, b.name) }
            )
            is PreviewSortMode.TYPE_ASC -> items = items.sortedWith { a, b -> 
                collator.compare(localizeType(a.creatureType), localizeType(b.creatureType)) 
            }
            is PreviewSortMode.SIZE_ASC -> items = items.sortedBy { sizeOrder(it.size) }
            
            // Сортировка по типу и редкости для снаряжения
            is PreviewSortMode.SUBCATEGORY_ASC -> items = items.sortedWith { a, b ->
                val aSub = if (a.subcategory.isNotEmpty()) a.subcategory.joinToString(", ") else ""
                val bSub = if (b.subcategory.isNotEmpty()) b.subcategory.joinToString(", ") else ""
                collator.compare(localizeSubcategory(aSub), localizeSubcategory(bSub))
            }
            is PreviewSortMode.RARITY_ASC -> items = items.sortedWith { a, b -> 
                collator.compare(localizeRarity(a.rarity), localizeRarity(b.rarity)) 
            }
        }

        binding.recyclerView.adapter = ReferenceListAdapter(items) { item ->
            val bundle = Bundle().apply {
                putString("objectId", item.fullId)
                putString("categoryKey", categoryKey)
            }
            findNavController().navigate(R.id.referenceDetail, bundle)
        }

        // Always show empty state when there are no items
        if (items.isEmpty()) {
            val emptyTitle = when (categoryKey) {
                "classes" -> "Нет классов"
                "species" -> "Нет видов"
                "backgrounds" -> "Нет происхождений"
                "feats" -> "Нет черт"
                "items" -> "Нет снаряжения"
                "conditions" -> "Нет состояний"
                "mechanics" -> "Нет механик"
                "monsters" -> "Нет монстров"
                "spells" -> "Нет заклинаний"
                else -> "Нет элементов"
            }
            val emptyMessage = when (categoryKey) {
                "classes" -> "Откройте вкладку \"Классы\" и добавьте классы."
                "species" -> "Откройте вкладку \"Виды\" и добавьте виды."
                "backgrounds" -> "Откройте вкладку \"Происхождения\" и добавьте происхождения."
                "feats" -> "Откройте вкладку \"Черты\" и добавьте черты."
                "items" -> "Откройте вкладку \"Снаряжение\" и добавьте снаряжение."
                "conditions" -> "Откройте вкладку \"Состояния\" и добавьте состояния."
                "mechanics" -> "Откройте вкладку \"Игровые механики\" и добавьте механики."
                "monsters" -> "Откройте вкладку \"Бестиарий\" и добавьте монстров."
                "spells" -> "Откройте вкладку \"Заклинания\" и добавьте заклинания."
                else -> "Добавьте элементы в соответствующую категорию."
            }
            stateViewBinder.showEmpty(title = emptyTitle, subtitle = emptyMessage)
        } else {
            stateViewBinder.hideAll()
        }
    }

    private fun showFilterDialog() {
        val groups = mutableListOf<FilterGroup>()

        if (categoryKey == "feats") {
            groups.add(FilterGroup("category", "Категория", listOf(
                FilterOption("origin", "Черта происхождения"),
                FilterOption("universal", "Универсальная черта"),
                FilterOption("fighting_style", "Боевой стиль"),
                FilterOption("epic_boon", "Эпический дар"),
                FilterOption("dragonmark", "Драконья метка"),
                FilterOption("dark_gift", "Тёмный дар")
            )))
        }

        if (categoryKey == "items") {
            groups.add(FilterGroup("category", "Категория", listOf(
                FilterOption("weapon", "Оружие"),
                FilterOption("armor", "Броня"),
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
                FilterOption("ammunition", "Боеприпасы"),
                FilterOption("gear", "Снаряжение")
            )))

            groups.add(FilterGroup("armor_type", "Категория доспеха", listOf(
                FilterOption("light_armor", "Лёгкий доспех"),
                FilterOption("medium_armor", "Средний доспех"),
                FilterOption("heavy_armor", "Тяжёлый доспех"),
                FilterOption("shield", "Щит")
            )))

            groups.add(FilterGroup("rarity", "Редкость", listOf(
                FilterOption("non-magic", "Немагический"),
                FilterOption("common", "Обычный"),
                FilterOption("uncommon", "Нестандартный"),
                FilterOption("rare", "Редкий"),
                FilterOption("very-rare", "Очень редкий"),
                FilterOption("legendary", "Легендарный"),
                FilterOption("artifact", "Артефакт"),
                FilterOption("varies", "Редкость варьируется")
            )))
        }

        if (categoryKey == "spells") {
            groups.add(FilterGroup("school", "Школа", listOf(
                FilterOption("abjuration", "Ограждение"),
                FilterOption("conjuration", "Призыв"),
                FilterOption("divination", "Прорицание"),
                FilterOption("enchantment", "Очарование"),
                FilterOption("evocation", "Воплощение"),
                FilterOption("illusion", "Иллюзия"),
                FilterOption("necromancy", "Некромантия"),
                FilterOption("transmutation", "Преобразование")
            )))

            groups.add(FilterGroup("level", "Уровень", listOf(
                FilterOption("0", "Заговор"),
                FilterOption("1", "1 уровень"),
                FilterOption("2", "2 уровень"),
                FilterOption("3", "3 уровень"),
                FilterOption("4", "4 уровень"),
                FilterOption("5", "5 уровень"),
                FilterOption("6", "6 уровень"),
                FilterOption("7", "7 уровень"),
                FilterOption("8", "8 уровень"),
                FilterOption("9", "9 уровень")
            )))

            groups.add(FilterGroup("material", "Материальный компонент", listOf(
                FilterOption("has_cost", "M со стоимостью"),
                FilterOption("consumable", "M расходуемые")
            )))
        }

        if (categoryKey == "monsters") {
            groups.add(FilterGroup("monster_size", "Размер", monsterSizeOptions()))
            groups.add(FilterGroup("monster_type", "Тип", monsterTypeOptions()))
            groups.add(FilterGroup("monster_challenge", "Опасность", monsterChallengeOptions()))
            groups.add(FilterGroup("monster_environment", "Среда обитания", monsterEnvironmentOptions()))
        }

        val sources = allItems.map { it.source }.filter { it.isNotBlank() }.distinct().sorted()
        if (sources.isNotEmpty()) {
            groups.add(FilterGroup("source", "Источник", sources.map { src ->
                FilterOption(src, localizeSource(src))
            }))
        }

        val sheet = FilterBottomSheet()
        sheet.setGroups(groups)
        sheet.setSelected(mapOf(
            "category" to currentFilterCategories,
            "source" to currentFilterSources,
            "rarity" to currentFilterRarities,
            "armor_type" to currentFilterArmorTypes,
            "material" to currentFilterMaterials,
            "monster_size" to currentMonsterSizes,
            "monster_type" to currentMonsterTypes,
            "monster_challenge" to currentMonsterChallenge,
            "monster_environment" to currentMonsterEnvironments
        ))
        setupFilterDialogCallbacks(sheet)
        sheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun monsterSizeOptions(): List<FilterOption> = listOf(
        FilterOption("tiny", "Крошечный"),
        FilterOption("small", "Маленький"),
        FilterOption("medium", "Средний"),
        FilterOption("large", "Большой"),
        FilterOption("huge", "Огромный"),
        FilterOption("gargantuan", "Громадный"),
        FilterOption("swarm_small", "Маленький рой"),
        FilterOption("swarm_medium", "Средний рой"),
        FilterOption("swarm_large", "Большой рой")
    )

    private fun monsterTypeOptions(): List<FilterOption> = listOf(
        FilterOption("aberration", "Аберрация"),
        FilterOption("beast", "Зверь"),
        FilterOption("celestial", "Небожитель"),
        FilterOption("construct", "Конструкт"),
        FilterOption("dragon", "Дракон"),
        FilterOption("elemental", "Элементаль"),
        FilterOption("fey", "Фея"),
        FilterOption("fiend", "Исчадие"),
        FilterOption("giant", "Великан"),
        FilterOption("humanoid", "Гуманоид"),
        FilterOption("monstrosity", "Чудовище"),
        FilterOption("ooze", "Слизь"),
        FilterOption("plant", "Растение"),
        FilterOption("undead", "Нежить")
    )

    private fun monsterChallengeOptions(): List<FilterOption> {
        val seen = mutableMapOf<String, Double>()
        for (item in allItems) {
            val cr = formatChallengeRating(item.challengeRating)
            if (cr.isNotBlank()) {
                seen.putIfAbsent(cr, item.challengeRating)
            }
        }
        return seen.entries
            .sortedBy { it.value }
            .map { FilterOption(it.key, "Опасность ${it.key}") }
    }

    private fun monsterEnvironmentOptions(): List<FilterOption> {
        val seen = mutableSetOf<String>()
        val options = mutableListOf<FilterOption>()
        for (item in allItems) {
            for (env in item.environment) {
                val key = env.lowercase()
                if (seen.add(key)) {
                    options.add(FilterOption(key, localizeEnvironmentFilter(env)))
                }
            }
        }
        return options.sortedBy { it.label }
    }

    private fun localizeEnvironmentFilter(env: String): String = when (env.lowercase()) {
        "any" -> "Любая"
        "arctic" -> "Арктика"
        "swamp" -> "Болото"
        "urban" -> "Город"
        "mountains" -> "Горы"
        "forest" -> "Леса"
        "grassland" -> "Луга"
        "coastal" -> "Прибрежье"
        "underdark" -> "Подземье"
        "underwater" -> "Под водой"
        "desert" -> "Пустыня"
        "hills" -> "Холмы"
        "astral plane" -> "Астральный план"
        "upper planes" -> "Верхние планы"
        "lower planes" -> "Нижние планы"
        "acheron" -> "Ахерон"
        "abyss" -> "Бездна"
        "gehenna" -> "Геенна"
        "nine hells" -> "Девять преисподних"
        "beastlands" -> "Звериные земли"
        "limbo" -> "Лимбо"
        "mechanus" -> "Механус"
        "elemental water" -> "Стихийный план воды"
        "elemental fire" -> "Стихийный план огня"
        "elemental earth" -> "Стихийный план земли"
        "elemental air" -> "Стихийный план воздуха"
        "elemental planes" -> "Стихийные планы"
        "elemental chaos" -> "Стихийный хаос"
        "feywild" -> "Страна фей"
        "shadowfell" -> "Царство теней"
        "ethereal plane" -> "Эфирный план"
        else -> env.replaceFirstChar { it.uppercase() }
    }

    private fun sizeOrder(size: String): Int = when (size.lowercase()) {
        "tiny" -> 0
        "small" -> 1
        "medium" -> 2
        "large" -> 3
        "huge" -> 4
        "gargantuan" -> 5
        else -> 6
    }

    private fun localizeSource(abbreviation: String): String = when (abbreviation) {
        "PHB2024" -> "Книга игрока (2024)"
        "EFA" -> "Эберрон: Забытые эпохи"
        "FR:HoF" -> "Забытые королевства: Герои фронтира"
        "RTW" -> "Равенлофт: Ведьмин свет"
        "ABoH" -> "Маяк надежды"
        "LFL" -> "Лорвин: Фейские предания"
        else -> abbreviation
    }

    private fun buildSubtitle(
        entry: com.herocraft24.core.data.ManifestEntry,
        isSwarm: Boolean = false,
        hitDie: Int? = null
    ): String {
        return when (categoryKey) {
            "classes" -> hitDie?.let { "Кость хитов d$it" } ?: entry.hit_die?.let { "Кость хитов d$it" } ?: ""
            "species" -> "${localizeType(entry.type)} • ${localizeSize(entry.size)}"
            "backgrounds" -> localizeCategory(entry.category)
            "conditions" -> ""
            "monsters" -> "${localizeType(entry.type)} • ${localizeMonsterSize(entry.size, isSwarm)} • Опасность ${entry.challenge_rating?.let { formatChallengeRating(it) } ?: ""}"
            "feats" -> localizeCategory(entry.category)
            "items" -> "${localizeRarity(entry.rarity)} • ${localizeCategory(entry.category)}"
            "spells" -> {
                // Safely handle spell-specific data from ManifestEntry
                val levelStr = entry.level?.let { "$it уровень" } ?: ""
                val schoolStr = entry.school?.let { localizeSchool(it) } ?: ""
                if (levelStr.isNotEmpty() && schoolStr.isNotEmpty()) {
                    "$levelStr • $schoolStr"
                } else {
                    levelStr + schoolStr
                }
            }
            "mechanics" -> ""
            else -> ""
        }
    }
    
    private fun localizeSchool(school: String?): String = when (school) {
        "abjuration" -> "Ограждение"
        "conjuration" -> "Призыв"
        "divination" -> "Прорицание"
        "enchantment" -> "Очарование"
        "evocation" -> "Воплощение"
        "illusion" -> "Иллюзия"
        "necromancy" -> "Некромантия"
        "transmutation" -> "Преобразование"
        else -> school ?: ""
    }

    private fun localizeCategory(category: String?): String = when (category) {
        "origin" -> "Черта происхождения"
        "universal" -> "Универсальная черта"
        "fighting_style" -> "Боевой стиль"
        "epic_boon" -> "Эпический дар"
        "dragonmark" -> "Драконья метка"
        "dark_gift" -> "Тёмный дар"
        "weapon" -> "Оружие"
        "armor" -> "Броня"
        "shield" -> "Щит"
        "adventuring_gear" -> "Снаряжение приключений"
        "pack" -> "Набор"
        "tool" -> "Ремесленный инструмент"
        "instrument" -> "Инструмент"
        "focus" -> "Фокусировка"
        "wand" -> "Волшебная палочка"
        "rod" -> "Жезл"
        "potion" -> "Зелье"
        "ring" -> "Кольцо"
        "staff" -> "Посох"
        "scroll" -> "Свиток"
        "wondrous_item" -> "Чудесная вещь"
        "ammunition" -> "Боеприпасы"
        "gear" -> "Снаряжение"
        else -> category?.replaceFirstChar { it.uppercase() } ?: ""
    }

    private fun localizeSubcategory(subcategory: String): String = when (subcategory.lowercase()) {
        "simple_melee" -> "Простое рукопашное"
        "martial_melee" -> "Воинское рукопашное"
        "simple_ranged" -> "Простое дальнобойное"
        "martial_ranged" -> "Воинское дальнобойное"
        "ammunition" -> "Боеприпас"
        "light_armor" -> "Лёгкий"
        "medium_armor" -> "Средний"
        "heavy_armor" -> "Тяжёлый"
        "shield" -> "Щит"
        "magic_item" -> "Волшебный предмет"
        else -> subcategory.replaceFirstChar { it.uppercase() }
    }

    private fun localizeRarity(rarity: String?): String = when (rarity?.lowercase()) {
        null, "", "non-magic", "nonmagic" -> "Немагический"
        "common" -> "Обычный"
        "uncommon" -> "Нестандартный"
        "rare" -> "Редкий"
        "very-rare", "veryrare" -> "Очень редкий"
        "legendary" -> "Легендарный"
        "artifact" -> "Артефакт"
        "varies" -> "Редкость варьируется"
        else -> rarity.replaceFirstChar { it.uppercase() }
    }

    override fun onDestroyView() {
        if (categoryKey in listOf("feats", "species", "backgrounds", "items", "spells", "monsters")) stateViewBinder.release()
        super.onDestroyView()
        _binding = null
    }

    private fun localizeSize(size: String?): String {
        if (size == null) return ""
        return when (size.lowercase()) {
            "tiny" -> getString(R.string.size_tiny)
            "small" -> getString(R.string.size_small)
            "medium" -> getString(R.string.size_medium)
            "large" -> getString(R.string.size_large)
            "huge" -> getString(R.string.size_huge)
            "gargantuan" -> getString(R.string.size_gargantuan)
            else -> size.replaceFirstChar { it.uppercase() }
        }
    }

    private fun localizeMonsterSize(size: String?, isSwarm: Boolean): String {
        if (isSwarm) {
            return when (size?.lowercase()) {
                "tiny" -> "Крошечный рой"
                "small" -> "Маленький рой"
                "medium" -> "Средний рой"
                "large" -> "Большой рой"
                "huge" -> "Огромный рой"
                "gargantuan" -> "Громадный рой"
                else -> "Рой"
            }
        }
        return localizeSize(size)
    }

    private fun localizeType(type: String?): String {
        if (type == null) return ""
        return when (type.lowercase()) {
            "humanoid" -> getString(R.string.type_humanoid)
            "fey" -> getString(R.string.type_fey)
            "fiend" -> getString(R.string.type_fiend)
            "undead" -> getString(R.string.type_undead)
            "monstrosity" -> getString(R.string.type_monstrosity)
            "aberration" -> getString(R.string.type_aberration)
            "celestial" -> getString(R.string.type_celestial)
            "elemental" -> getString(R.string.type_elemental)
            "construct" -> getString(R.string.type_construct)
            "dragon" -> getString(R.string.type_dragon)
            "giant" -> getString(R.string.type_giant)
            "ooze" -> getString(R.string.type_ooze)
            "plant" -> getString(R.string.type_plant)
            "beast" -> getString(R.string.type_beast)
            else -> type.replaceFirstChar { it.uppercase() }
        }
    }

    private fun showSortDialog() {
        val options = when (categoryKey) {
            "monsters" -> listOf(
                PreviewSortMode.CHALLENGE_ASC to "Опасность (возр.)",
                PreviewSortMode.CHALLENGE_DESC to "Опасность (убыв.)",
                PreviewSortMode.NAME_ASC to "Название А–Я",
                PreviewSortMode.NAME_DESC to "Название Я–А",
                PreviewSortMode.TYPE_ASC to "Тип",
                PreviewSortMode.SIZE_ASC to "Размер"
            )
            "items" -> listOf(
                PreviewSortMode.DEFAULT to "По умолчанию",
                PreviewSortMode.NAME_ASC to "Имя А–Я",
                PreviewSortMode.NAME_DESC to "Имя Я–А",
                PreviewSortMode.SUBCATEGORY_ASC to "Тип по алфавиту",
                PreviewSortMode.RARITY_ASC to "Редкость"
            )
            else -> listOf(
                PreviewSortMode.DEFAULT to "По умолчанию",
                PreviewSortMode.NAME_ASC to "Имя А–Я",
                PreviewSortMode.NAME_DESC to "Имя Я–А"
            )
        }
        val labels = options.map { it.second }.toTypedArray()
        val current = options.indexOfFirst { it.first == currentSortMode }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Сортировка")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                currentSortMode = options[which].first
                applyFilters()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}

sealed class PreviewSortMode(val label: String) {
    object DEFAULT : PreviewSortMode("По умолчанию")
    object NAME_ASC : PreviewSortMode("Имя А–Я")
    object NAME_DESC : PreviewSortMode("Имя Я–А")
    object CHALLENGE_ASC : PreviewSortMode("Опасность (возр.)")
    object CHALLENGE_DESC : PreviewSortMode("Опасность (убыв.)")
    object TYPE_ASC : PreviewSortMode("Тип")
    object SIZE_ASC : PreviewSortMode("Размер")
    object SUBCATEGORY_ASC : PreviewSortMode("Тип по алфавиту")
    object RARITY_ASC : PreviewSortMode("Редкость по алфавиту")
}
