package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.feature.characters.databinding.DialogSpellPickerBinding

/**
 * Смена подготовленных заклинаний prepared-кастера (жрец, друид, паладин, следопыт, колдун)
 * после долгого отдыха. Показывает весь список класса доступных уровней с отметками.
 */
class PreparedSpellsDialogFragment : DialogFragment() {

    private var _binding: DialogSpellPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var charId: String = ""
    private var searchQuery: String = ""
    private lateinit var adapter: SpellPickerAdapter

    private var candidates: List<SpellSummary> = emptyList()
    private var selectedIds: MutableSet<String> = mutableSetOf()
    private var limit: Int = 0

    companion object {
        private const val ARG_CHAR_ID = "charId"

        fun newInstance(charId: String): PreparedSpellsDialogFragment {
            return PreparedSpellsDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_CHAR_ID, charId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        charId = arguments?.getString(ARG_CHAR_ID) ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val char = vm.getCharacter(charId) ?: run { dismiss(); return }
        val info = vm.preparedCasterInfo(char) ?: run { dismiss(); return }
        candidates = vm.preparedSpellCandidates(char)
        selectedIds = vm.preparedClassSpellIds(char).toMutableSet()
        limit = info.limit

        adapter = SpellPickerAdapter(
            onItemClick = { spell ->
                SpellDetailSheetDialog.newInstance(spell.fullId, charId, info.ability)
                    .show(childFragmentManager, "SpellDetail")
            },
            onAddClick = { spell ->
                if (spell.fullId in selectedIds) {
                    selectedIds.remove(spell.fullId)
                } else if (selectedIds.size < limit) {
                    selectedIds.add(spell.fullId)
                }
                updateTitle()
                refreshList()
            },
            isSelected = { spell -> spell.fullId in selectedIds },
            selectedIcon = "✓",
            unselectedIcon = "+"
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchBar.setOnQueryListener { query ->
            searchQuery = query.lowercase().trim()
            refreshList()
        }
        binding.btnSort.visibility = View.GONE
        binding.btnFilter.visibility = View.GONE

        binding.emptyView.text = "Нет доступных заклинаний"
        binding.confirmButton.visibility = View.VISIBLE
        binding.confirmButton.text = "Готово"
        binding.confirmButton.setOnClickListener {
            vm.setPreparedSpells(charId, selectedIds.toList())
            dismiss()
        }

        updateTitle()
        refreshList()
    }

    private fun updateTitle() {
        binding.titleView.text = "Подготовленные заклинания · ${selectedIds.size}/$limit"
    }

    private fun refreshList() {
        val filtered = if (searchQuery.isBlank()) {
            candidates
        } else {
            val tokens = searchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isEmpty()) candidates
            else candidates.filter { spell ->
                tokens.all { token ->
                    spell.name.lowercase().contains(token) ||
                        spell.tags.any { it.lowercase().contains(token) } ||
                        spell.school.lowercase().contains(token)
                }
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
