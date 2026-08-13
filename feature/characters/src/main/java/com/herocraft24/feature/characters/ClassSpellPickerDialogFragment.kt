package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.herocraft24.core.model.SpellSummary
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
    private var filteredSpells: List<SpellSummary> = emptyList()
    private var selectedLevel: Int? = null
    private var searchQuery: String = ""

    private val selectedIds = mutableSetOf<String>()
    private lateinit var adapter: SpellPickerAdapter

    private var onResultListener: ((List<String>) -> Unit)? = null

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

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText?.lowercase()?.trim() ?: ""
                refreshList()
                return true
            }
        })

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
            android.util.Log.d("ClassSpellsDebug", "classFilter='$filter', raw=${raw.size}, filtered=${allSpells.size}")
            setupLevelChips()
            updateTitle()
            refreshList()
        }
    }

    private fun setupLevelChips() {
        binding.levelChips.removeAllViews()

        val chipAll = Chip(requireContext()).apply {
            text = "Все"
            isCheckable = true
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedLevel = null
                    uncheckOthers(this)
                    refreshList()
                }
            }
        }
        binding.levelChips.addView(chipAll)

        val levels = allSpells.map { it.level }.distinct().sorted()
        for (level in levels) {
            val chip = Chip(requireContext()).apply {
                text = if (level == 0) "Заговор" else "$level"
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedLevel = level
                        uncheckOthers(this)
                        refreshList()
                    }
                }
            }
            binding.levelChips.addView(chip)
        }
    }

    private fun uncheckOthers(keep: Chip) {
        for (i in 0 until binding.levelChips.childCount) {
            val child = binding.levelChips.getChildAt(i)
            if (child is Chip && child !== keep) child.isChecked = false
        }
    }

    private fun refreshList() {
        var filtered = allSpells
        selectedLevel?.let { lvl -> filtered = filtered.filter { it.level == lvl } }
        if (searchQuery.isNotBlank()) {
            val tokens = searchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                filtered = filtered.filter { spell ->
                    tokens.all { token ->
                        spell.name.lowercase().contains(token) ||
                        spell.school.lowercase().contains(token) ||
                        spell.tags.any { it.lowercase().contains(token) }
                    }
                }
            }
        }
        filteredSpells = filtered.sortedWith(compareBy<SpellSummary> { it.level }.thenBy { it.name.lowercase() })
        adapter.submitList(filteredSpells)
        binding.emptyView.visibility = if (filteredSpells.isEmpty()) View.VISIBLE else View.GONE
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
