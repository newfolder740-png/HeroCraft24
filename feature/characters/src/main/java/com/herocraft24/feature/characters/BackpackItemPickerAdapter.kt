package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.Item
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.feature.characters.databinding.ItemBackpackPickerBinding

class BackpackItemPickerAdapter(
    private val actionLabel: String,
    private val showCount: Boolean,
    private val onItemClick: (String, String?) -> Unit,
    private val onAction: (String, String?) -> Unit
) : RecyclerView.Adapter<BackpackItemPickerAdapter.VH>() {

    data class Row(val id: String, val item: Item, val count: Int = 1, val variantId: String? = null, val variantItem: Item? = null) {
        val displayName: String get() = if (variantItem != null) "${item.name.get()} (${variantItem.name.get()})" else item.name.get()
    }

    private var items: List<Row> = emptyList()

    fun submitList(list: List<Row>) {
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
        val (id, item, count, variantId, variantItem) = items[position]
        holder.binding.itemName.text = items[position].displayName
        holder.binding.itemSubtitle.text = UiLocalizer.category(variantItem?.category ?: item.category)
        holder.binding.actionButton.text = actionLabel
        holder.binding.actionButton.setOnClickListener { onAction(id, variantId) }
        holder.binding.root.setOnClickListener { onItemClick(id, variantId) }
        if (showCount && count > 1) {
            holder.binding.itemCount.visibility = android.view.View.VISIBLE
            holder.binding.itemCount.text = "x$count"
        } else {
            holder.binding.itemCount.visibility = android.view.View.GONE
        }
    }
}
