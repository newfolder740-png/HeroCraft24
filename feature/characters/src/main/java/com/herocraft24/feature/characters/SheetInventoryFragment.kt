package com.herocraft24.feature.characters

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.herocraft24.core.model.Item
import com.herocraft24.core.model.ItemCategory
import com.herocraft24.core.model.ItemRarity
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.dp
import com.herocraft24.core.ui.widget.FilterBottomSheet
import com.herocraft24.core.ui.widget.FilterGroup
import com.herocraft24.core.ui.widget.FilterOption
import com.herocraft24.core.ui.widget.SearchBarView
import com.herocraft24.feature.characters.databinding.CardBackpackItemBinding
import com.herocraft24.feature.characters.databinding.CardFeatureCreateBinding
import kotlinx.coroutines.launch

class SheetInventoryFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()
    private var charId: String? = null
    private var equipmentExpandedPosition: Int = -1
    private var proficiencyExpandedPosition: Int = -1
    private var magicItemExpandedPosition: Int = -1

    private var backpackSearchQuery: String = ""
    private var backpackSortMode: BackpackSortMode = BackpackSortMode.NAME_ASC
    private var backpackFilters: BackpackFilters = BackpackFilters()
    private var backpackItemContainer: LinearLayout? = null
    private var backpackTitleInner: LinearLayout? = null
    private var currentCharData: CharacterData? = null

    private enum class BackpackSortMode(val label: String) {
        NAME_ASC("Имя А–Я"),
        NAME_DESC("Имя Я–А"),
        CATEGORY_ASC("Тип А–Я")
    }

    private data class BackpackFilters(
        val categories: Set<ItemCategory> = emptySet(),
        val rarities: Set<ItemRarity> = emptySet(),
        val weaponCategories: Set<String> = emptySet(),
        val armorCategories: Set<String> = emptySet(),
        val magic: Boolean? = null
    ) {
        val isActive: Boolean
            get() = categories.isNotEmpty() || rarities.isNotEmpty() ||
                weaponCategories.isNotEmpty() || armorCategories.isNotEmpty() || magic != null
    }

    data class EquipmentSlot(
        val title: String,
        val items: List<ResolvedItem>,
        val selectedId: String?,
        val onSelected: (String?) -> Unit
    )

    data class MagicItemSlot(
        val index: Int,
        val title: String,
        val items: List<ResolvedItem>,
        val selectedComposite: String?,
        val onSelected: (String?) -> Unit
    )

    data class ResolvedItem(
        val inventoryItem: CharacterItem,
        val item: Item,
        val variantItem: Item?
    ) {
        val id: String get() = inventoryItem.itemId
        val variantId: String? get() = inventoryItem.variantItemId
        val compositeId: String get() = if (variantId != null) "$id|$variantId" else id
        val displayName: String get() {
            val base = variantItem?.name?.get() ?: item.name.get()
            return if (variantItem != null) "${item.name.get()} ($base)" else item.name.get()
        }
        val category: String get() = variantItem?.category ?: item.category
        val cost: com.herocraft24.core.model.Cost? get() = item.cost ?: variantItem?.cost
        val weight: com.herocraft24.core.model.Weight? get() = item.weight ?: variantItem?.weight
        val damage: com.herocraft24.core.model.WeaponDamage? get() = item.damage ?: variantItem?.damage
        val armorClass: com.herocraft24.core.model.ArmorClass? get() = item.armor_class ?: variantItem?.armor_class
        val isMagic: Boolean get() = item.magic
        val description: String get() {
            val magicDesc = item.description.get()
            val baseDesc = variantItem?.description?.get()
            return if (baseDesc.isNullOrBlank()) magicDesc else "$baseDesc\n\n$magicDesc"
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_inventory, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        charId = arguments?.getString("characterId") ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                charId?.let { id ->
                    list.find { it.id == id }?.let { render(it) }
                }
            }
        }
    }

    private fun render(char: CharacterData) {
        val view = view ?: return
        val scrollView = view as? ScrollView
        val content = view.findViewById<LinearLayout>(R.id.content) ?: return
        val scrollY = scrollView?.scrollY ?: 0
        content.removeAllViews()
        val ctx = requireContext()
        val cls = vm.getClassInfo(char.classId)

        // Resolve backpack items with details
        android.util.Log.d("InventoryDebug", "char.equipment ids: ${char.equipment.map { it.itemId }}")
        val backpackItems: List<ResolvedItem> = char.equipment.mapNotNull { inv ->
            val item = vm.repository.getItem(inv.itemId)
            val variant = inv.variantItemId?.let { vm.repository.getItem(it) }
            if (item != null) ResolvedItem(inv, item, variant) else null
        }
        android.util.Log.d("InventoryDebug", "backpackItems count: ${backpackItems.size}, categories: ${backpackItems.map { it.category }}")

        // ── Equipment — expandable cards in 2x2 grid, expanding to full width ──
        sectionTitle(content, "Экипировано")

        val armorItems = backpackItems.filter { it.category == "armor" }
        val shieldItems = backpackItems.filter { it.category == "shield" }
        val weaponItems = backpackItems.filter { it.category == "weapon" }
        android.util.Log.d("InventoryDebug", "armor: ${armorItems.size}, shield: ${shieldItems.size}, weapon: ${weaponItems.size}")

        val equipmentSlots = listOf(
            EquipmentSlot("Доспех", armorItems, char.equippedArmor) { selectedComposite ->
                save(char.copy(equippedArmor = selectedComposite))
            },
            EquipmentSlot("Щит", shieldItems, char.equippedShield) { selectedComposite ->
                save(char.copy(equippedShield = selectedComposite))
            },
            EquipmentSlot("Оружие 1", weaponItems, char.equippedWeapon1) { selectedComposite ->
                save(char.copy(equippedWeapon1 = selectedComposite))
            },
            EquipmentSlot("Оружие 2", weaponItems, char.equippedWeapon2) { selectedComposite ->
                save(char.copy(equippedWeapon2 = selectedComposite))
            }
        )

        val equipmentRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            adapter = EquipmentAdapter(equipmentSlots)
            isNestedScrollingEnabled = false
            itemAnimator = null
        }
        content.addView(equipmentRecycler)

        // ── Magic Items ──
        val magicResolvedItems = backpackItems.filter { it.isMagic }
        val magicItemsByComposite = magicResolvedItems.associateBy { it.compositeId }
        sectionTitle(content, "Магические предметы")
        val selectedMagicComposites = char.equippedMagicItems.take(24)
        val attunedCount = selectedMagicComposites.count { magicItemsByComposite[it]?.item?.attunement == true }

        // Counter: 3 cells
        val counterRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(3) { i ->
            counterRow.addView(statBox(ctx, "", if (i < attunedCount) "●" else "○", filled = i < attunedCount))
        }
        content.addView(counterRow)

        // Magic item cards: one per selected item plus one empty slot, up to 24
        val magicCardCount = minOf(selectedMagicComposites.size + 1, 24)
        val magicItemSlots = (0 until magicCardCount).map { index ->
            val selectedComposite = selectedMagicComposites.getOrNull(index)
            MagicItemSlot(
                index = index,
                title = selectedComposite?.let { magicItemsByComposite[it]?.displayName } ?: "Магический предмет ${index + 1}",
                items = magicResolvedItems,
                selectedComposite = selectedComposite,
                onSelected = { selectedId ->
                    val current = selectedMagicComposites.toMutableList()
                    when {
                        selectedId == null && index < current.size -> current.removeAt(index)
                        selectedId != null && index < current.size -> current[index] = selectedId
                        selectedId != null -> current.add(selectedId)
                    }
                    save(char.copy(equippedMagicItems = current.take(24)))
                }
            )
        }
        val magicRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            adapter = MagicItemAdapter(magicItemSlots)
            isNestedScrollingEnabled = false
            itemAnimator = null
        }
        content.addView(magicRecycler)

        // ── Equipment Proficiencies ──
        sectionTitle(content, "Владения экипировкой")
        val profArmor = cls?.starting_proficiencies?.armor?.filterNotNull() ?: emptyList()
        val profWeapons = cls?.starting_proficiencies?.weapons?.filterNotNull() ?: emptyList()
        val profTools = cls?.starting_proficiencies?.tools?.filterNotNull() ?: emptyList()

        val profRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            adapter = ProficiencyAdapter(listOf(
                "Доспехи" to profArmor,
                "Оружие" to profWeapons,
                "Инструменты" to profTools
            ))
            isNestedScrollingEnabled = false
            itemAnimator = null
        }
        content.addView(profRecycler)

        // ── Currency ──
        sectionTitle(content, "Монеты")
        val currencyRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        currencyRow.addView(statBox(ctx, "ММ", "${char.currency.cp}"))
        currencyRow.addView(statBox(ctx, "СМ", "${char.currency.sp}"))
        currencyRow.addView(statBox(ctx, "ЭМ", "0"))
        currencyRow.addView(statBox(ctx, "ЗМ", "${char.currency.gp}"))
        currencyRow.addView(statBox(ctx, "ПМ", "${char.currency.pp}"))
        content.addView(currencyRow)

        // ── Backpack ──
        val totalWeight = calculateTotalWeight(backpackItems)
        val unit = backpackItems.mapNotNull { it.weight?.unit }.firstOrNull() ?: "lb"
        val backpackHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backpackTitleCard = com.google.android.material.card.MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 4.dp(ctx), 8.dp(ctx), 4.dp(ctx))
            }
            radius = 12f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
            isClickable = true
            isFocusable = true
            rippleColor = android.content.res.ColorStateList.valueOf(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setOnClickListener { openBackpackPicker(char, addMode = false) }
        }
        backpackTitleInner = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(ctx), 8.dp(ctx), 12.dp(ctx), 8.dp(ctx))
        }
        backpackTitleInner?.addView(TextView(ctx).apply {
            text = "Рюкзак (Общий вес: ${formatWeight(totalWeight, unit)})"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        backpackTitleInner?.addView(TextView(ctx).apply {
            text = "−"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })
        backpackTitleCard.addView(backpackTitleInner)
        backpackHeader.addView(backpackTitleCard)
        backpackHeader.addView(androidx.appcompat.widget.AppCompatButton(ctx).apply {
            text = "+"
            setPadding(16.dp(ctx), 0, 16.dp(ctx), 0)
            setOnClickListener { openBackpackPicker(char, addMode = true) }
        })
        content.addView(backpackHeader)

        // Controls row
        val controlsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4.dp(ctx), 0, 4.dp(ctx))
        }
        val searchBar = SearchBarView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sortBtn = MaterialButton(ctx).apply {
            text = "Сорт."
            setPadding(8.dp(ctx), 0, 8.dp(ctx), 0)
            minWidth = 0
            minimumWidth = 0
        }
        val filterBtn = MaterialButton(ctx).apply {
            text = "Фильтр"
            setPadding(8.dp(ctx), 0, 8.dp(ctx), 0)
            minWidth = 0
            minimumWidth = 0
        }
        controlsRow.addView(searchBar)
        controlsRow.addView(sortBtn)
        controlsRow.addView(filterBtn)
        content.addView(controlsRow)

        backpackItemContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(backpackItemContainer)

        currentCharData = char

        fun renderBackpackItems() {
            backpackItemContainer?.removeAllViews()
            val totalWeight = calculateTotalWeight(backpackItems)
            val unit = backpackItems.mapNotNull { it.weight?.unit }.firstOrNull() ?: "lb"
            backpackTitleInner?.getChildAt(0)?.let { (it as? TextView)?.text = "Рюкзак (Общий вес: ${formatWeight(totalWeight, unit)})" }

            var filtered = applyBackpackSearch(backpackItems)
            filtered = applyBackpackFilters(filtered)
            filtered = applyBackpackSort(filtered)

            if (filtered.isEmpty()) {
                backpackItemContainer?.addView(TextView(ctx).apply {
                    text = "Пусто"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 4.dp(ctx), 0, 0)
                })
            } else {
                val grouped = filtered.groupBy { it.compositeId }
                for ((_, items) in grouped) {
                    val first = items.first()
                    val count = items.size
                    val card = buildBackpackItemCard(ctx, first, count, char)
                    backpackItemContainer?.addView(card)
                }
            }
        }

        searchBar.setOnQueryListener { query ->
            backpackSearchQuery = query.lowercase().trim()
            renderBackpackItems()
        }
        sortBtn.setOnClickListener { showBackpackSortDialog(::renderBackpackItems) }
        filterBtn.setOnClickListener { showBackpackFilterDialog(::renderBackpackItems) }

        renderBackpackItems()

        scrollView?.post { scrollView.scrollTo(0, scrollY) }
    }

    private fun buildBackpackItemCard(ctx: android.content.Context, item: ResolvedItem, count: Int, char: CharacterData): View {
        val binding = CardBackpackItemBinding.inflate(LayoutInflater.from(ctx), backpackItemContainer, false)
        binding.root.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(16.dp(ctx), 4.dp(ctx), 16.dp(ctx), 4.dp(ctx))
        }

        val displayName = if (count > 1) "x$count ${item.displayName}" else item.displayName
        binding.itemName.text = displayName
        binding.itemSubtitle.text = UiLocalizer.category(item.category)

        val rarityColor = getRarityColor(ctx, item.item.rarity)
        binding.rarityColor.setBackgroundColor(rarityColor)

        binding.removeButton.setOnClickListener {
            vm.removeItemFromBackpack(char.id, item.id, item.variantId)
        }

        binding.root.setOnClickListener {
            BackpackItemDetailDialogFragment.newInstance(item.id, item.variantId)
                .show(parentFragmentManager, "BackpackItemDetail")
        }

        return binding.root
    }

    private fun getRarityColor(ctx: android.content.Context, rarity: String?): Int {
        val resId = when (ItemRarity.fromValue(rarity)) {
            ItemRarity.NON_MAGIC, ItemRarity.COMMON -> com.herocraft24.core.ui.R.color.rarity_common
            ItemRarity.UNCOMMON -> com.herocraft24.core.ui.R.color.rarity_uncommon
            ItemRarity.RARE -> com.herocraft24.core.ui.R.color.rarity_rare
            ItemRarity.VERY_RARE, ItemRarity.VERY_RARE_ALT -> com.herocraft24.core.ui.R.color.rarity_very_rare
            ItemRarity.LEGENDARY -> com.herocraft24.core.ui.R.color.rarity_legendary
            ItemRarity.ARTIFACT -> com.herocraft24.core.ui.R.color.rarity_artifact
            else -> com.herocraft24.core.ui.R.color.rarity_default
        }
        return androidx.core.content.ContextCompat.getColor(ctx, resId)
    }

    private fun applyBackpackSearch(items: List<ResolvedItem>): List<ResolvedItem> {
        if (backpackSearchQuery.isBlank()) return items
        val tokens = backpackSearchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
        if (tokens.isEmpty()) return items
        return items.filter { item ->
            tokens.all { token ->
                item.displayName.lowercase().contains(token) ||
                item.item.tags.any { it.lowercase().contains(token) }
            }
        }
    }

    private fun applyBackpackFilters(items: List<ResolvedItem>): List<ResolvedItem> {
        return items.filter { item ->
            if (backpackFilters.categories.isNotEmpty() && ItemCategory.fromValue(item.category) !in backpackFilters.categories) return@filter false
            if (backpackFilters.rarities.isNotEmpty() && ItemRarity.fromValue(item.item.rarity) !in backpackFilters.rarities) return@filter false
            if (backpackFilters.weaponCategories.isNotEmpty() && item.item.subcategory.none { it in backpackFilters.weaponCategories }) return@filter false
            if (backpackFilters.armorCategories.isNotEmpty()) {
                val matchesArmor = item.item.subcategory.any { it in backpackFilters.armorCategories } ||
                    (item.item.category == "shield" && "shield" in backpackFilters.armorCategories)
                if (!matchesArmor) return@filter false
            }
            if (backpackFilters.magic != null && item.item.magic != backpackFilters.magic) return@filter false
            true
        }
    }

    private fun applyBackpackSort(items: List<ResolvedItem>): List<ResolvedItem> {
        return when (backpackSortMode) {
            BackpackSortMode.NAME_ASC -> items.sortedBy { it.displayName.lowercase() }
            BackpackSortMode.NAME_DESC -> items.sortedByDescending { it.displayName.lowercase() }
            BackpackSortMode.CATEGORY_ASC -> items.sortedBy { UiLocalizer.category(it.category) }
        }
    }

    private fun showBackpackSortDialog(onUpdate: () -> Unit) {
        val options = BackpackSortMode.entries.toTypedArray()
        val labels = options.map { it.label }.toTypedArray()
        val current = backpackSortMode.ordinal
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Сортировка")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                backpackSortMode = options[which]
                onUpdate()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showBackpackFilterDialog(onUpdate: () -> Unit) {
        val sheet = FilterBottomSheet()
        val groups = listOf(
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
            FilterGroup("magic", "Магия", listOf(
                FilterOption("yes", "Магический"),
                FilterOption("no", "Немагический")
            ))
        )
        val selectedMap = mutableMapOf<String, Set<String>>()
        if (backpackFilters.categories.isNotEmpty()) selectedMap["categories"] = backpackFilters.categories.map { it.raw }.toSet()
        if (backpackFilters.rarities.isNotEmpty()) selectedMap["rarities"] = backpackFilters.rarities.map { it.raw }.toSet()
        if (backpackFilters.weaponCategories.isNotEmpty()) selectedMap["weapon_categories"] = backpackFilters.weaponCategories
        if (backpackFilters.armorCategories.isNotEmpty()) selectedMap["armor_categories"] = backpackFilters.armorCategories
        backpackFilters.magic?.let { selectedMap["magic"] = setOf(if (it) "yes" else "no") }
        sheet.setGroups(groups)
        sheet.setSelected(selectedMap)
        sheet.setCallbacks(
            onApply = { result ->
                backpackFilters = backpackFiltersFromMap(result)
                onUpdate()
            },
            onReset = {
                backpackFilters = BackpackFilters()
                onUpdate()
            }
        )
        sheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun backpackFiltersFromMap(map: Map<String, Set<String>>): BackpackFilters {
        return BackpackFilters(
            categories = (map["categories"] ?: emptySet()).mapNotNull { ItemCategory.fromValue(it) }.toSet(),
            rarities = (map["rarities"] ?: emptySet()).mapNotNull { ItemRarity.fromValue(it) }.toSet(),
            weaponCategories = map["weapon_categories"] ?: emptySet(),
            armorCategories = map["armor_categories"] ?: emptySet(),
            magic = when {
                "yes" in (map["magic"] ?: emptySet()) -> true
                "no" in (map["magic"] ?: emptySet()) -> false
                else -> null
            }
        )
    }

    private fun calculateTotalWeight(items: List<ResolvedItem>): Double {
        var total = 0.0
        items.forEach { resolved ->
            total += weightOf(resolved)
        }
        return total
    }

    private fun weightOf(resolved: ResolvedItem): Double {
        val item = resolved.item
        if (item.category == "pack" && item.contents.isNotEmpty()) {
            return item.contents.sumOf { content ->
                val contentItem = vm.getItem(content.item_id)
                if (contentItem != null) content.quantity * weightOf(ResolvedItem(CharacterItem(content.item_id), contentItem, null)) else 0.0
            }
        }
        val weight = resolved.weight
        return weight?.amount ?: 0.0
    }

    private fun formatWeight(amount: Double, unit: String): String {
        val value = if (amount == amount.toInt().toDouble()) amount.toInt().toString() else amount.toString()
        return "$value $unit"
    }

    private fun save(char: CharacterData) {
        vm.saveCharacter(char)
        render(char)
    }

    private fun openBackpackPicker(char: CharacterData, addMode: Boolean) {
        val dialog = BackpackItemPickerDialogFragment.newInstance(char.id, addMode)
        dialog.show(childFragmentManager, "BackpackItemPicker")
    }

    private fun buildEquipmentExpandedContent(ctx: Context, slot: EquipmentSlot): View {
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val selectedResolved = slot.items.find { it.compositeId == slot.selectedId }

        val descView = TextView(ctx).apply {
            text = selectedResolved?.description ?: ""
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 4.dp(ctx), 0, 0)
        }

        val displayNames: List<String> = listOf("—") + slot.items.map { it.displayName }
        val idByName: Map<String, String> = slot.items.map { it.displayName to it.compositeId }.toMap()

        val dropdownLayout = TextInputLayout(ctx).apply {
            this.hint = "Выберите ${slot.title}"
            setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
        }
        val dropdown = MaterialAutoCompleteTextView(ctx).apply {
            inputType = android.text.InputType.TYPE_NULL
            threshold = 0
            isFocusableInTouchMode = false
            isClickable = true
            isFocusable = true
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, displayNames))
            val currentName = slot.items.find { it.compositeId == slot.selectedId }?.displayName
            setText(currentName ?: "—", false)
            setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                val selectedName = displayNames.getOrNull(position) ?: "—"
                val selectedItemId = idByName[selectedName]
                if (position == 0 || selectedItemId == null) {
                    descView.text = ""
                    slot.onSelected(null)
                } else {
                    val resolved = slot.items.find { it.compositeId == selectedItemId }
                    descView.text = resolved?.description ?: ""
                    slot.onSelected(selectedItemId)
                }
            }
        }
        dropdownLayout.addView(dropdown)
        content.addView(dropdownLayout)
        selectedResolved?.let { content.addView(buildItemStatsView(ctx, it)) }
        content.addView(descView)

        return content
    }

    private fun buildItemStatsView(ctx: Context, resolved: ResolvedItem): View {
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        container.addView(TextView(ctx).apply {
            text = "Категория: ${UiLocalizer.category(resolved.category)}"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 2.dp(ctx), 0, 0)
        })
        if (resolved.item.subcategory.isNotEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "Подкатегория: ${resolved.item.subcategory.joinToString(", ") { UiLocalizer.subcategory(it) }}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 2.dp(ctx), 0, 0)
            })
        }
        if (resolved.item.rarity.isNotBlank()) {
            container.addView(TextView(ctx).apply {
                text = "Редкость: ${UiLocalizer.rarity(resolved.item.rarity)}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 2.dp(ctx), 0, 0)
            })
        }
        if (resolved.item.attunement) {
            container.addView(TextView(ctx).apply {
                text = "Настройка: требуется"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 2.dp(ctx), 0, 0)
            })
        }
        resolved.cost?.let { cost ->
            container.addView(TextView(ctx).apply {
                text = "Стоимость: ${cost.amount.toInt()} ${UiLocalizer.costUnit(cost.unit)}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 2.dp(ctx), 0, 0)
            })
        }
        resolved.weight?.let { weight ->
            container.addView(TextView(ctx).apply {
                text = "Вес: ${weight.amount} ${UiLocalizer.weightUnit(weight.unit)}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 2.dp(ctx), 0, 0)
            })
        }
        resolved.damage?.let { dmg ->
            val dmgBonus = resolved.item.damage_bonus
            val bonusStr = if (dmgBonus != null && dmgBonus > 0) " + $dmgBonus" else ""
            container.addView(TextView(ctx).apply {
                text = "Урон: ${dmg.damage_dice}${bonusStr} ${UiLocalizer.damageType(dmg.damage_type)}${dmg.versatile_dice?.let { " (универсальное $it)" } ?: ""}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 2.dp(ctx), 0, 0)
            })
        }
        if (resolved.category != "shield") {
            resolved.armorClass?.let { ac ->
                val acBonus = resolved.item.armor_class_bonus
                val effectiveBase = ac.base + (acBonus ?: 0)
                
                // Build the dex cap text - prefer max_dexterity_bonus from JSON, fall back to max_dex
                val dexCapText = ac.max_dexterity_bonus?.let { " (не более +$it)" } 
                    ?: ac.max_dex?.let { " (макс +$it)" } 
                    ?: ""
                
                container.addView(TextView(ctx).apply {
                    text = "КЗ: ${effectiveBase}${if (ac.dex_bonus) "+ Ловкость$dexCapText" else ""}${ac.min_strength?.let { ", мин Сила $it" } ?: ""}${if (ac.stealth_disadvantage) ", помеха Скрытности" else ""}"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setPadding(0, 2.dp(ctx), 0, 0)
                })
            }
        }
        return container
    }

    inner class EquipmentAdapter(private val slots: List<EquipmentSlot>) : RecyclerView.Adapter<EquipmentAdapter.VH>() {

        inner class VH(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount() = slots.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val slot = slots[position]
            val isExpanded = position == equipmentExpandedPosition
            val selectedName = slot.items.find { it.compositeId == slot.selectedId }?.displayName
            holder.binding.featureTitle.text = selectedName ?: slot.title
            holder.binding.expandedContent.removeAllViews()
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                val expandedContent = buildEquipmentExpandedContent(holder.binding.root.context, slot)
                holder.binding.expandedContent.addView(expandedContent)
            }
            holder.binding.headerRow.setOnClickListener {
                equipmentExpandedPosition = if (isExpanded) -1 else position
                notifyDataSetChanged()
            }
        }
    }

    inner class ProficiencyAdapter(private val proficiencies: List<Pair<String, List<String>>>) : RecyclerView.Adapter<ProficiencyAdapter.VH>() {

        inner class VH(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount() = proficiencies.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (title, items) = proficiencies[position]
            val isExpanded = position == proficiencyExpandedPosition
            holder.binding.featureTitle.text = title
            holder.binding.expandedContent.removeAllViews()
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                val content = LinearLayout(holder.binding.root.context).apply { orientation = LinearLayout.VERTICAL }
                if (items.isEmpty()) {
                    content.addView(TextView(content.context).apply {
                        text = "Нет"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setPadding(0, 4.dp(content.context), 0, 0)
                    })
                } else {
                    items.forEach { item ->
                        content.addView(TextView(content.context).apply {
                            text = "• $item"
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                            setPadding(0, 2.dp(content.context), 0, 2.dp(content.context))
                        })
                    }
                }
                holder.binding.expandedContent.addView(content)
            }
            holder.binding.headerRow.setOnClickListener {
                proficiencyExpandedPosition = if (isExpanded) -1 else position
                notifyDataSetChanged()
            }
        }
    }

    private fun buildMagicItemExpandedContent(ctx: Context, slot: MagicItemSlot): View {
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val selectedItem = slot.selectedComposite?.let { composite -> slot.items.find { it.compositeId == composite } }

        val descView = TextView(ctx).apply {
            text = selectedItem?.description ?: ""
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 4.dp(ctx), 0, 0)
        }

        val displayNames: List<String> = listOf("—") + slot.items.map { it.displayName }
        val idByName: Map<String, String> = slot.items.map { it.displayName to it.compositeId }.toMap()

        val dropdownLayout = TextInputLayout(ctx).apply {
            this.hint = "Выберите магический предмет"
            setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
        }
        val dropdown = MaterialAutoCompleteTextView(ctx).apply {
            inputType = android.text.InputType.TYPE_NULL
            threshold = 0
            isFocusableInTouchMode = false
            isClickable = true
            isFocusable = true
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, displayNames))
            setText(selectedItem?.displayName ?: "—", false)
            setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                val selectedName = displayNames.getOrNull(position) ?: "—"
                val selectedComposite = idByName[selectedName]
                descView.text = selectedComposite?.let { composite -> slot.items.find { it.compositeId == composite }?.description } ?: ""
                slot.onSelected(selectedComposite)
            }
        }
        dropdownLayout.addView(dropdown)
        content.addView(dropdownLayout)
        selectedItem?.let { content.addView(buildItemStatsView(ctx, it)) }
        content.addView(descView)

        return content
    }

    inner class MagicItemAdapter(private val slots: List<MagicItemSlot>) : RecyclerView.Adapter<MagicItemAdapter.VH>() {

        inner class VH(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount() = slots.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val slot = slots[position]
            val isExpanded = position == magicItemExpandedPosition
            val selectedName = slot.selectedComposite?.let { composite -> slot.items.find { it.compositeId == composite }?.displayName }
            holder.binding.featureTitle.text = selectedName ?: slot.title
            holder.binding.expandedContent.removeAllViews()
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                val expandedContent = buildMagicItemExpandedContent(holder.binding.root.context, slot)
                holder.binding.expandedContent.addView(expandedContent)
            }
            holder.binding.headerRow.setOnClickListener {
                magicItemExpandedPosition = if (isExpanded) -1 else position
                notifyDataSetChanged()
            }
        }
    }

    private fun statBox(ctx: Context, label: String, value: String, filled: Boolean = false): MaterialCardView {
        return MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4.dp(ctx), 4.dp(ctx), 4.dp(ctx), 4.dp(ctx))
            }
            radius = 8f
            setCardBackgroundColor(if (filled) 0xFF6750A4.toInt() else resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8.dp(ctx), 8.dp(ctx), 8.dp(ctx), 8.dp(ctx))
            }
            if (label.isNotEmpty()) {
                inner.addView(TextView(ctx).apply {
                    text = label
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
            }
            inner.addView(TextView(ctx).apply {
                text = value
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                if (filled) setTextColor(0xFFFFFFFF.toInt())
            })
            addView(inner)
        }
    }

    private fun sectionTitle(container: LinearLayout, title: String) {
        container.addView(TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 12.dp(context), 0, 4.dp(context))
        })
    }

    private fun resolveColor(attr: Int): Int {
        val ta = requireContext().theme?.obtainStyledAttributes(intArrayOf(attr))
        val color = ta?.getColor(0, 0) ?: 0
        ta?.recycle()
        return color
    }
}
