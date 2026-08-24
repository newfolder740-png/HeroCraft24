package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.feature.characters.databinding.CardPreparedSpellBinding

data class PickerOption(
    val fullId: String,
    val name: String,
    val subtitle: String,
    val color: Int
)

class OptionPickerAdapter(
    private val onItemClick: (PickerOption) -> Unit,
    private val onAddClick: (PickerOption) -> Unit,
    private val isSelected: (PickerOption) -> Boolean = { false },
    private val selectedIcon: String = "✓",
    private val unselectedIcon: String = "+"
) : RecyclerView.Adapter<OptionPickerAdapter.VH>() {

    private var items: List<PickerOption> = emptyList()

    fun submitList(list: List<PickerOption>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: CardPreparedSpellBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = CardPreparedSpellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val option = items[position]
        holder.binding.spellName.text = option.name
        holder.binding.spellSubtitle.text = option.subtitle
        holder.binding.schoolColor.setBackgroundColor(option.color)
        holder.binding.badges.visibility = View.GONE
        holder.binding.actionButton.text = if (isSelected(option)) selectedIcon else unselectedIcon
        holder.binding.actionButton.setOnClickListener { onAddClick(option) }
        holder.binding.root.setOnClickListener { onItemClick(option) }
    }
}
