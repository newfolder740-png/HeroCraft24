package com.herocraft24.feature.characters

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.herocraft24.core.model.Item
import com.herocraft24.core.model.ItemCategory
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.dp
import com.herocraft24.core.ui.widget.FilterBottomSheet
import com.herocraft24.core.ui.widget.FilterGroup
import com.herocraft24.core.ui.widget.FilterOption
import com.herocraft24.feature.characters.databinding.DialogBackpackItemPickerBinding
import kotlinx.coroutines.launch

class BackpackItemPickerDialogFragment : DialogFragment() {

    private var _binding: DialogBackpackItemPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var charId: String? = null
    private var addMode: Boolean = true

    private val allItems = mutableListOf<BackpackItemPickerAdapter.Row>()
    private var searchQuery: String = ""
    private var sortMode: SortMode = SortMode.NAME_ASC
    private var activeFilters: ItemFilters = ItemFilters()

    private lateinit var adapter: BackpackItemPickerAdapter

    private enum class SortMode(val label: String) {
        NAME_ASC("Имя А–Я"),
        NAME_DESC("Имя Я–А"),
        CATEGORY_ASC("Тип А–Я")
    }

    private data class ItemFilters(
        val categories: Set<ItemCategory> = emptySet()
    ) {
        val isActive: Boolean get() = categories.isNotEmpty()
    }

    companion object {
        private const val ARG_CHAR_ID = "characterId"
        private const val ARG_ADD_MODE = "addMode"

        fun newInstance(characterId: String, addMode: Boolean): BackpackItemPickerDialogFragment {
            return BackpackItemPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAR_ID, characterId)
                    putBoolean(ARG_ADD_MODE, addMode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        charId = arguments?.getString(ARG_CHAR_ID)
        addMode = arguments?.getBoolean(ARG_ADD_MODE, true) ?: true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogBackpackItemPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val char = charId?.let { vm.getCharacter(it) } ?: run { dismiss(); return }

        binding.titleView.text = if (addMode) "Добавить предмет" else "Удалить предмет"

        adapter = BackpackItemPickerAdapter(
            actionLabel = if (addMode) "+" else "−",
            showCount = !addMode,
            onItemClick = { itemId, variantId ->
                BackpackItemDetailDialogFragment.newInstance(itemId, variantId).show(childFragmentManager, "BackpackItemDetail")
            },
            onAction = { itemId, variantId ->
                if (addMode) {
                    val item = vm.getItem(itemId)
                    val variants = item?.let { vm.findMagicItemVariants(it) } ?: emptyList()
                    if (variants.isNotEmpty()) {
                        showVariantPicker(itemId, variants) { selectedBaseId ->
                            vm.addItemToBackpack(char.id, itemId, selectedBaseId)
                            showAddFeedback(itemId)
                        }
                    } else {
                        vm.addItemToBackpack(char.id, itemId)
                        showAddFeedback(itemId)
                    }
                } else {
                    vm.removeItemFromBackpack(char.id, itemId, variantId)
                }
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

        loadItems(char)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                charId?.let { id ->
                    list.find { it.id == id }?.let { loadItems(it) }
                }
            }
        }
    }

    private fun loadItems(char: CharacterData) {
        lifecycleScope.launch {
            val allItemsList = char.equipment
            val sourceIds = if (addMode) vm.getItemIds() else allItemsList.map { it.itemId }.distinct()
            val counts = allItemsList.groupingBy { it.itemId to it.variantItemId }.eachCount()

            allItems.clear()
            if (addMode) {
                allItems.addAll(
                    sourceIds.mapNotNull { id ->
                        val item = vm.getItem(id)
                        if (item != null) BackpackItemPickerAdapter.Row(id, item, 1, null) else null
                    }
                )
            } else {
                allItems.addAll(
                    allItemsList.mapNotNull { inv ->
                        val item = vm.getItem(inv.itemId)
                        val variant = inv.variantItemId?.let { vm.getItem(it) }
                        if (item != null) {
                            val count = counts[inv.itemId to inv.variantItemId] ?: 1
                            BackpackItemPickerAdapter.Row(inv.itemId, item, count, inv.variantItemId, variant)
                        } else null
                    }.distinctBy { it.id to it.variantId }
                )
            }

            refreshList()
        }
    }

    private fun refreshList() {
        var filtered = applySearch(allItems)
        filtered = applyFilters(filtered)
        filtered = applySort(filtered)
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applySearch(items: List<BackpackItemPickerAdapter.Row>): List<BackpackItemPickerAdapter.Row> {
        if (searchQuery.isBlank()) return items
        val tokens = searchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
        if (tokens.isEmpty()) return items
        return items.filter { row ->
            tokens.all { token ->
                row.item.name.get().lowercase().contains(token) ||
                row.item.category.lowercase().contains(token)
            }
        }
    }

    private fun applyFilters(items: List<BackpackItemPickerAdapter.Row>): List<BackpackItemPickerAdapter.Row> {
        if (activeFilters.categories.isEmpty()) return items
        return items.filter { row ->
            ItemCategory.fromValue(row.item.category) in activeFilters.categories
        }
    }

    private fun applySort(items: List<BackpackItemPickerAdapter.Row>): List<BackpackItemPickerAdapter.Row> {
        return when (sortMode) {
            SortMode.NAME_ASC -> items.sortedBy { it.item.name.get().lowercase() }
            SortMode.NAME_DESC -> items.sortedByDescending { it.item.name.get().lowercase() }
            SortMode.CATEGORY_ASC -> items.sortedBy { UiLocalizer.category(it.item.category) }
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
                refreshList()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showFilterDialog() {
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
            ))
        )
        val selectedMap = mutableMapOf<String, Set<String>>()
        if (activeFilters.categories.isNotEmpty()) selectedMap["categories"] = activeFilters.categories.map { it.raw }.toSet()
        sheet.setGroups(groups)
        sheet.setSelected(selectedMap)
        sheet.setCallbacks(
            onApply = { result ->
                activeFilters = ItemFilters(
                    categories = (result["categories"] ?: emptySet()).mapNotNull { ItemCategory.fromValue(it) }.toSet()
                )
                refreshList()
            },
            onReset = {
                activeFilters = ItemFilters()
                refreshList()
            }
        )
        sheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun showVariantPicker(itemId: String, variants: List<Pair<String, Item>>, onSelected: (String) -> Unit) {
        val names = variants.map { it.second.name.get() ?: it.second.id }.toTypedArray()
        var selectedIndex = 0
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Выберите вид")
            .setSingleChoiceItems(names, selectedIndex) { _, which -> selectedIndex = which }
            .setPositiveButton("Добавить") { _, _ ->
                val selected = variants.getOrNull(selectedIndex)
                if (selected != null) onSelected(selected.first)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddFeedback(itemId: String) {
        val item = vm.getItem(itemId)
        val root = dialog?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content) ?: return

        root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

        val ctx = requireContext()
        val feedback = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(24.dp(ctx), 16.dp(ctx), 24.dp(ctx), 16.dp(ctx))
            setBackgroundColor(0xFF2E7D32.toInt())
            elevation = 8f
        }
        feedback.addView(TextView(ctx).apply {
            text = "✓"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 24f
            setPadding(0, 0, 12.dp(ctx), 0)
        })
        feedback.addView(TextView(ctx).apply {
            text = item?.name?.get() ?: "Добавлено"
            setTextColor(0xFFFFFFFF.toInt())
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        })

        val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(16.dp(ctx), 0, 16.dp(ctx), 64.dp(ctx))
        }
        root.addView(feedback, params)
        feedback.translationY = 100f
        feedback.alpha = 0f
        feedback.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                feedback.animate()
                    .alpha(0f)
                    .translationY(-50f)
                    .setDuration(500)
                    .setStartDelay(800)
                    .withEndAction { root.removeView(feedback) }
                    .start()
            }
            .start()
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
