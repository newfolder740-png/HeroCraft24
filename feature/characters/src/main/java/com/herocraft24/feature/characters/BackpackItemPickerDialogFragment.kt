package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.herocraft24.core.model.Item
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.feature.characters.databinding.DialogBackpackItemPickerBinding
import kotlinx.coroutines.launch

class BackpackItemPickerDialogFragment : DialogFragment() {

    private var _binding: DialogBackpackItemPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var charId: String? = null
    private var addMode: Boolean = true

    private val allItems = mutableListOf<Pair<String, Item>>()
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
            onItemClick = { itemId ->
                BackpackItemDetailDialogFragment.newInstance(itemId).show(childFragmentManager, "BackpackItemDetail")
            },
            onAction = { itemId ->
                if (addMode) {
                    vm.addItemToBackpack(char, itemId)
                } else {
                    vm.removeItemFromBackpack(char, itemId)
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
            val backpackIds = char.equipment.map { it.itemId }.toSet()
            val sourceIds = if (addMode) vm.getItemIds() else backpackIds.toList()

            allItems.clear()
            allItems.addAll(
                sourceIds.mapNotNull { id ->
                    val item = vm.getItem(id)
                    if (item != null) id to item else null
                }
            )

            val distinctCategories = allItems.map { it.second.category }.distinct().sorted()
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
        var filtered = allItems.filter { (_, item) ->
            selectedCategory?.let { item.category == it } ?: true
        }
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { (_, item) ->
                item.name.get().lowercase().contains(searchQuery) ||
                item.category.lowercase().contains(searchQuery)
            }
        }
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
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
