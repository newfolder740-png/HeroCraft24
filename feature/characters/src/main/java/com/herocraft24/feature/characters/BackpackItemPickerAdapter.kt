package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.Item
import com.herocraft24.core.model.ItemRarity
import com.herocraft24.core.ui.R
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
        val isAttuned = variantItem?.attunement == true || item.attunement
        holder.binding.itemSubtitle.text = if (isAttuned) {
            "${UiLocalizer.category(variantItem?.category ?: item.category)} · Настройка: требуется"
        } else {
            UiLocalizer.category(variantItem?.category ?: item.category)
        }
        holder.binding.actionButton.text = actionLabel
        holder.binding.actionButton.setOnClickListener { onAction(id, variantId) }
        holder.binding.root.setOnClickListener { onItemClick(id, variantId) }
        if (showCount && count > 1) {
            holder.binding.itemCount.visibility = android.view.View.VISIBLE
            holder.binding.itemCount.text = "x$count"
        } else {
            holder.binding.itemCount.visibility = android.view.View.GONE
        }

        val rarityColor = getRarityColor(holder.itemView.context, item.rarity)
        holder.binding.rarityColor.setBackgroundColor(rarityColor)
    }

    private fun getRarityColor(ctx: android.content.Context, rarity: String?): Int {
        val resId = when (ItemRarity.fromValue(rarity)) {
            ItemRarity.NON_MAGIC, ItemRarity.COMMON -> R.color.rarity_common
            ItemRarity.UNCOMMON -> R.color.rarity_uncommon
            ItemRarity.RARE -> R.color.rarity_rare
            ItemRarity.VERY_RARE, ItemRarity.VERY_RARE_ALT -> R.color.rarity_very_rare
            ItemRarity.LEGENDARY -> R.color.rarity_legendary
            ItemRarity.ARTIFACT -> R.color.rarity_artifact
            else -> R.color.rarity_default
        }
        return ContextCompat.getColor(ctx, resId)
    }
}
