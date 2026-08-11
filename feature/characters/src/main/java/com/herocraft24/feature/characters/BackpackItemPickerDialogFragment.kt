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
import androidx.appcompat.widget.SearchView
import androidx.core.animation.doOnEnd
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.herocraft24.core.model.Item
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.DialogBackpackItemPickerBinding
import kotlinx.coroutines.launch

class BackpackItemPickerDialogFragment : DialogFragment() {

    private var _binding: DialogBackpackItemPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var charId: String? = null
    private var addMode: Boolean = true

    private val allItems = mutableListOf<BackpackItemPickerAdapter.Row>()
    private val categories = mutableListOf<String>()
    private var selectedCategory: String? = null
    private var searchQuery: String = ""

    private lateinit var adapter: BackpackItemPickerAdapter

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

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText?.lowercase()?.trim() ?: ""
                refreshList()
                return true
            }
        })

        setupCategoryChips()
        loadItems(char)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                charId?.let { id ->
                    list.find { it.id == id }?.let { loadItems(it) }
                }
            }
        }
    }
    private fun setupCategoryChips() {
        val chipAll = Chip(requireContext()).apply {
            text = "Все"
            isCheckable = true
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCategory = null
                    refreshList()
                }
            }
        }
        binding.categoryChips.addView(chipAll)
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

            val distinctCategories = allItems.map { it.item.category }.distinct().sorted()
            if (distinctCategories != categories) {
                categories.clear()
                categories.addAll(distinctCategories)
                while (binding.categoryChips.childCount > 1) {
                    binding.categoryChips.removeViewAt(binding.categoryChips.childCount - 1)
                }
                for (category in distinctCategories) {
                    val chip = Chip(requireContext()).apply {
                        text = UiLocalizer.category(category)
                        isCheckable = true
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                selectedCategory = category
                                for (i in 0 until binding.categoryChips.childCount) {
                                    val child = binding.categoryChips.getChildAt(i)
                                    if (child is Chip && child !== this) child.isChecked = false
                                }
                                refreshList()
                            }
                        }
                    }
                    binding.categoryChips.addView(chip)
                }
            }

            refreshList()
        }
    }

    private fun refreshList() {
        var filtered = allItems.filter { row ->
            selectedCategory?.let { row.item.category == it } ?: true
        }
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.item.name.get().lowercase().contains(searchQuery) ||
                row.item.category.lowercase().contains(searchQuery)
            }
        }
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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
