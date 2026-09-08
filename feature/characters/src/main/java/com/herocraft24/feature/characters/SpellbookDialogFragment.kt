package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.feature.characters.databinding.DialogSpellPickerBinding
import kotlinx.coroutines.launch

/**
 * Книга заклинаний волшебника: список заклинаний книги, отметка подготовленных (✓)
 * и кнопка «+» для добавления любых доступных заклинаний волшебника.
 */
class SpellbookDialogFragment : DialogFragment() {

    private var _binding: DialogSpellPickerBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var charId: String = ""
    private var searchQuery: String = ""
    private lateinit var adapter: SpellPickerAdapter

    // Записи книги "fullId|source" в том же порядке, что и показанные карточки
    private var bookEntries: List<String> = emptyList()
    private var bookSummaries: List<SpellSummary> = emptyList()
    private var preparedEntryIds: Set<String> = emptySet()
    private var preparedLimit: Int = 0

    companion object {
        private const val ARG_CHAR_ID = "charId"

        fun newInstance(charId: String): SpellbookDialogFragment {
            return SpellbookDialogFragment().apply {
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

        adapter = SpellPickerAdapter(
            onItemClick = { spell ->
                SpellDetailSheetDialog.newInstance(spell.fullId, charId, currentAbility())
                    .show(childFragmentManager, "SpellDetail")
            },
            onAddClick = { spell ->
                val entry = bookEntries.firstOrNull { it.spellFullId() == spell.fullId } ?: return@SpellPickerAdapter
                vm.toggleBookSpellPrepared(charId, entry)
            },
            isSelected = { spell ->
                val entry = bookEntries.firstOrNull { it.spellFullId() == spell.fullId }
                entry != null && entry in preparedEntryIds
            },
            selectedIcon = "✓",
            unselectedIcon = "–"
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchBar.setOnQueryListener { query ->
            searchQuery = query.lowercase().trim()
            refreshList()
        }
        binding.btnSort.visibility = View.GONE
        binding.btnFilter.visibility = View.GONE

        binding.controlsRow.addView(com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "+"
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { openAddPicker() }
        })

        binding.emptyView.text = "Книга заклинаний пуста"
        binding.confirmButton.visibility = View.VISIBLE
        binding.confirmButton.text = "Готово"
        binding.confirmButton.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            vm.characters.collect { list ->
                if (list.any { it.id == charId }) reload()
            }
        }
        lifecycleScope.launch { reload() }
    }

    private fun currentAbility(): String {
        val char = vm.getCharacter(charId) ?: return "intelligence"
        val classId = vm.getSpellbookClassId(char) ?: return "intelligence"
        return vm.getClassInfo(classId)?.spellcasting?.ability ?: "intelligence"
    }

    private suspend fun reload() {
        val char = vm.getCharacter(charId) ?: return
        val sp = char.spells ?: return
        val classId = vm.getSpellbookClassId(char) ?: return
        val ability = vm.getClassInfo(classId)?.spellcasting?.ability ?: return
        bookEntries = sp.spellbook
        preparedEntryIds = (sp.preparedByAbility[ability] ?: emptyList()).toSet()
        preparedLimit = vm.getSpellbookPreparedLimit(char)
        val byId = vm.getAllSpellSummaries().associateBy { it.fullId }
        bookSummaries = bookEntries.mapNotNull { byId[it.spellFullId()] }
            .sortedWith(compareBy<SpellSummary> { it.level }.thenBy { it.name.lowercase() })
        updateTitle()
        refreshList()
    }

    private fun updateTitle() {
        val preparedCount = bookEntries.count { it in preparedEntryIds }
        binding.titleView.text = "Книга заклинаний · Подготовлено $preparedCount/$preparedLimit"
    }

    private fun refreshList() {
        val filtered = if (searchQuery.isBlank()) {
            bookSummaries
        } else {
            val tokens = searchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isEmpty()) bookSummaries
            else bookSummaries.filter { spell ->
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

    private fun openAddPicker() {
        val char = vm.getCharacter(charId) ?: return
        val classId = vm.getSpellbookClassId(char) ?: return
        val level = char.classLevels[classId] ?: if (classId == char.classId) char.level else 0
        val maxLevel = vm.getMaxSpellSlotLevel(classId, level)
        SpellPickerDialogFragment.newInstance(
            characterId = charId,
            ability = currentAbility(),
            mode = SpellPickerDialogFragment.MODE_SPELLBOOK,
            classFilter = classId,
            maxLevel = maxLevel
        ).show(childFragmentManager, "SpellbookAddPicker")
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
