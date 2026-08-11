package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.Item
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.feature.characters.databinding.ItemBackpackPickerBinding

class BackpackItemPickerAdapter(
    private val actionLabel: String,
    private val onItemClick: (String) -> Unit,
    private val onAction: (String) -> Unit
) : RecyclerView.Adapter<BackpackItemPickerAdapter.VH>() {

    private var items: List<Pair<String, Item>> = emptyList()

    fun submitList(list: List<Pair<String, Item>>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemBackpackPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBackpackPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (id, item) = items[position]
        holder.binding.itemName.text = item.name.get()
        holder.binding.itemSubtitle.text = UiLocalizer.category(item.category)
        holder.binding.actionButton.text = actionLabel
        holder.binding.actionButton.setOnClickListener { onAction(id) }
        holder.binding.root.setOnClickListener { onItemClick(id) }
    }
}
