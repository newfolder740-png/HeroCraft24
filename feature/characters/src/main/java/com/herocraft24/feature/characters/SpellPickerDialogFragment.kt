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

class SpellPickerDialogFragment : DialogFragment() {

    private var _binding: DialogSpellPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var charId: String? = null

    private var allSpells: List<SpellSummary> = emptyList()
    private var filteredSpells: List<SpellSummary> = emptyList()
    private var selectedLevel: Int? = null
    private var searchQuery: String = ""

    private lateinit var adapter: SpellPickerAdapter

    companion object {
        private const val ARG_CHAR_ID = "characterId"

        fun newInstance(characterId: String): SpellPickerDialogFragment {
            return SpellPickerDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_CHAR_ID, characterId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        charId = arguments?.getString(ARG_CHAR_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val char = charId?.let { vm.getCharacter(it) } ?: run { dismiss(); return }

        adapter = SpellPickerAdapter(
            onItemClick = { spell ->
                SpellDetailSheetDialog.newInstance(spell.fullId, char.id)
                    .show(childFragmentManager, "SpellDetail")
            },
            onAddClick = { spell ->
                vm.addPreparedSpell(char.id, spell.fullId)
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
            setupLevelChips()
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
