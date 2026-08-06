package com.herocraft24.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.herocraft24.core.ui.R

data class FilterGroup(
    val key: String,
    val title: String,
    val options: List<FilterOption>
)

data class FilterOption(
    val key: String,
    val label: String,
    val indent: Int = 0,
    val isParent: Boolean = false,
    val parentKey: String? = null
)

class FilterBottomSheet : BottomSheetDialogFragment() {

    private val groups = mutableListOf<FilterGroup>()
    private val selected = mutableMapOf<String, MutableSet<String>>()
    private var onApply: ((Map<String, Set<String>>) -> Unit)? = null
    private var onReset: (() -> Unit)? = null

    private val groupContainers = mutableMapOf<String, LinearLayout>()
    private val checkBoxes = mutableMapOf<String, MutableList<CheckBox>>()
    private val parentCheckBoxes = mutableMapOf<String, CheckBox>()

    fun setGroups(groups: List<FilterGroup>) {
        this.groups.clear()
        this.groups.addAll(groups)
    }

    fun setSelected(selected: Map<String, Set<String>>) {
        this.selected.clear()
        selected.forEach { (k, v) -> this.selected[k] = v.toMutableSet() }
    }

    fun setCallbacks(onApply: (Map<String, Set<String>>) -> Unit, onReset: () -> Unit) {
        this.onApply = onApply
        this.onReset = onReset
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val content = view.findViewById<LinearLayout>(R.id.filter_content)
        content.removeAllViews()
        groupContainers.clear()
        checkBoxes.clear()
        parentCheckBoxes.clear()

        val onSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface)

        for (group in groups) {
            val groupSelected = selected[group.key] ?: emptySet()
            val isExpanded = groupSelected.isNotEmpty()

            val header = TextView(requireContext()).apply {
                text = group.title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                setPadding(0, 16.dp, 0, 8.dp)
                setTextColor(onSurface)
            }

            val optionsContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (isExpanded) View.VISIBLE else View.GONE
            }

            for (option in group.options) {
                val cb = CheckBox(requireContext()).apply {
                    text = option.label
                    isChecked = option.key in groupSelected
                    setPadding(option.indent * 24.dp, 2.dp, 0, 2.dp)
                    textSize = 14f
                    setTextColor(onSurface)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            selected.getOrPut(group.key) { mutableSetOf() }.add(option.key)
                        } else {
                            selected[group.key]?.remove(option.key)
                        }
                        if (option.isParent) {
                            toggleChildren(group.key, option.key, checked)
                        }
                        updateParentState(group, option.parentKey)
                        applyImmediately()
                    }
                }
                optionsContainer.addView(cb)
                checkBoxes.getOrPut(group.key) { mutableListOf() }.add(cb)
                if (option.isParent) {
                    parentCheckBoxes[option.key] = cb
                }
            }

            header.setOnClickListener {
                optionsContainer.visibility =
                    if (optionsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }

            content.addView(header)
            content.addView(optionsContainer)
            groupContainers[group.key] = optionsContainer
        }
    }

    /** Applies the current selection immediately (filters update on the fly). */
    private fun applyImmediately() {
        val result = selected.filterValues { it.isNotEmpty() }.mapValues { it.value.toSet() }
        onApply?.invoke(result)
    }

    private fun toggleChildren(groupKey: String, parentKey: String, checked: Boolean) {
        val group = groups.find { it.key == groupKey } ?: return
        val cbs = checkBoxes[groupKey] ?: return
        val childIndices = group.options.mapIndexedNotNull { index, opt ->
            if (opt.parentKey == parentKey) index else null
        }
        for (idx in childIndices) {
            cbs.getOrNull(idx)?.isChecked = checked
        }
    }

    private fun updateParentState(group: FilterGroup, parentKey: String?) {
        if (parentKey == null) return
        val parentCb = parentCheckBoxes[parentKey] ?: return
        val groupKey = group.key
        val cbs = checkBoxes[groupKey] ?: return
        val childIndices = group.options.mapIndexedNotNull { index, opt ->
            if (opt.parentKey == parentKey) index else null
        }
        val allChecked = childIndices.isNotEmpty() && childIndices.all { cbs.getOrNull(it)?.isChecked == true }
        parentCb.setOnCheckedChangeListener(null)
        parentCb.isChecked = allChecked
        parentCb.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selected.getOrPut(groupKey) { mutableSetOf() }.add(parentKey)
            } else {
                selected[groupKey]?.remove(parentKey)
            }
            toggleChildren(groupKey, parentKey, checked)
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun resolveColor(attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    companion object {
        const val TAG = "FilterBottomSheet"
    }
}
