package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.feature.characters.databinding.DialogSpellPickerBinding

/**
 * Universal picker for content options (feats, metamagic; invocations/maneuvers in the future).
 * Follows the same UX as ClassSpellPickerDialogFragment: cards with selection marks,
 * tap opens the full description, confirm returns the selected IDs.
 */
class OptionPickerDialogFragment : DialogFragment() {

    private var _binding: DialogSpellPickerBinding? = null
    private val binding get() = _binding!!

    private var kind: String = KIND_FEAT
    private var title: String = ""
    private var optionIds: List<String> = emptyList()
    private var requiredCount: Int = 1

    private val selectedIds = mutableListOf<String>()
    private var allOptions: List<PickerOption> = emptyList()
    private var searchQuery: String = ""
    private lateinit var adapter: OptionPickerAdapter

    private var onResultListener: ((List<String>) -> Unit)? = null

    companion object {
        const val KIND_FEAT = "feat"
        const val KIND_METAMAGIC = "metamagic"

        private const val ARG_KIND = "kind"
        private const val ARG_TITLE = "title"
        private const val ARG_OPTION_IDS = "optionIds"
        private const val ARG_REQUIRED_COUNT = "requiredCount"
        private const val ARG_SELECTED = "selected"

        fun newInstance(
            kind: String,
            title: String,
            optionIds: List<String>,
            requiredCount: Int,
            selected: List<String> = emptyList()
        ): OptionPickerDialogFragment {
            return OptionPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_KIND, kind)
                    putString(ARG_TITLE, title)
                    putStringArrayList(ARG_OPTION_IDS, ArrayList(optionIds))
                    putInt(ARG_REQUIRED_COUNT, requiredCount)
                    putStringArrayList(ARG_SELECTED, ArrayList(selected))
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
            kind = it.getString(ARG_KIND) ?: KIND_FEAT
            title = it.getString(ARG_TITLE) ?: ""
            optionIds = it.getStringArrayList(ARG_OPTION_IDS) ?: emptyList()
            requiredCount = it.getInt(ARG_REQUIRED_COUNT, 1)
            selectedIds.addAll(it.getStringArrayList(ARG_SELECTED) ?: emptyList())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val accentColor = resolveColor(com.google.android.material.R.attr.colorPrimary)
        adapter = OptionPickerAdapter(
            onItemClick = { option -> showDetail(option.fullId) },
            onAddClick = { option -> toggleSelection(option.fullId) },
            isSelected = { option -> option.fullId in selectedIds }
        )
        binding.recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchBar.setOnQueryListener { query ->
            searchQuery = query.lowercase().trim()
            refreshList()
        }
        binding.btnSort.visibility = View.GONE
        binding.btnFilter.visibility = View.GONE

        binding.emptyView.text = "Нет доступных вариантов"
        binding.confirmButton.visibility = View.VISIBLE
        binding.confirmButton.setOnClickListener {
            onResultListener?.invoke(selectedIds.toList())
            dismiss()
        }

        loadOptions(accentColor)
    }

    private fun loadOptions(accentColor: Int) {
        val contentRepo = ContentRepository.get(requireContext())
        allOptions = when (kind) {
            KIND_FEAT -> optionIds.mapNotNull { fullId ->
                val feat = contentRepo.getFeat(fullId) ?: return@mapNotNull null
                PickerOption(
                    fullId = fullId,
                    name = feat.name.get(),
                    subtitle = "Черта • ${UiLocalizer.category(feat.category)}",
                    color = accentColor
                )
            }
            KIND_METAMAGIC -> optionIds.mapNotNull { fullId ->
                val metamagic = contentRepo.getMetamagic(fullId) ?: return@mapNotNull null
                PickerOption(
                    fullId = fullId,
                    name = metamagic.name.get(),
                    subtitle = "Метамагия • ${metamagic.cost}",
                    color = accentColor
                )
            }
            else -> emptyList()
        }.sortedBy { it.name.lowercase() }
        updateTitle()
        refreshList()
    }

    private fun toggleSelection(fullId: String) {
        if (fullId in selectedIds) {
            selectedIds.remove(fullId)
        } else {
            if (requiredCount <= 1) {
                selectedIds.clear()
            } else if (selectedIds.size >= requiredCount) {
                return
            }
            selectedIds.add(fullId)
        }
        updateTitle()
        refreshList()
    }

    private fun updateTitle() {
        binding.titleView.text = "$title: ${selectedIds.size}/$requiredCount"
    }

    private fun refreshList() {
        val filtered = if (searchQuery.isBlank()) {
            allOptions
        } else {
            val tokens = searchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isEmpty()) allOptions
            else allOptions.filter { option -> tokens.all { token -> option.name.lowercase().contains(token) } }
        }
        adapter.submitList(filtered)
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDetail(fullId: String) {
        when (kind) {
            KIND_FEAT -> FeatDetailSheetDialog.newInstance(fullId).show(childFragmentManager, "FeatDetail")
            KIND_METAMAGIC -> MetamagicDetailSheetDialog.newInstance(fullId).show(childFragmentManager, "MetamagicDetail")
        }
    }

    private fun resolveColor(attr: Int): Int {
        val ta = requireContext().theme.obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
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
