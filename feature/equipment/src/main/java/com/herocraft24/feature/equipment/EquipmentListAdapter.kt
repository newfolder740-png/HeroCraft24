package com.herocraft24.feature.equipment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.ItemRarity
import com.herocraft24.core.model.ItemSummary
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.rarityColor
import com.herocraft24.feature.equipment.databinding.CardEquipmentBinding

class EquipmentListAdapter(
    private var favoriteIds: Set<String>,
    private val onItemClick: (ItemSummary) -> Unit,
    private val onFavoriteClick: (ItemSummary) -> Unit
) : RecyclerView.Adapter<EquipmentListAdapter.VH>() {

    private var items: List<ItemSummary> = emptyList<ItemSummary>()

    fun submitList(list: List<ItemSummary>, favorites: Set<String> = favoriteIds) {
        items = list
        favoriteIds = favorites
        notifyDataSetChanged()
    }

    fun updateFavorites(favorites: Set<String>) {
        favoriteIds = favorites
        notifyDataSetChanged()
    }

    class VH(val b: CardEquipmentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH =
        VH(CardEquipmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.b.itemName.text = item.name
        h.b.itemSubtitle.text = buildSubtitle(item)
        h.b.rarityColor.setBackgroundColor(h.b.rarityColor.context.rarityColor(ItemRarity.fromValue(item.rarity)))
        h.b.root.setOnClickListener { onItemClick(item) }
        val isFav = item.fullId in favoriteIds
        h.b.favoriteIcon.setImageResource(
            if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        h.b.favoriteIcon.setOnClickListener { onFavoriteClick(item) }
    }

    override fun getItemCount() = items.size

    private fun buildSubtitle(i: ItemSummary): String {
        val parts = mutableListOf(localizeCategory(i.category))
        if (i.subcategory.isNotEmpty() && i.category !in magicItemCategories) {
            parts.addAll(i.subcategory.map { localizeSubcategory(it) })
        }
        i.cost?.let { parts.add(it) }
        return parts.joinToString(" • ")
    }

    private val magicItemCategories = setOf("wand", "rod", "potion", "ring", "staff", "scroll", "wondrous_item")

    private fun localizeCategory(c: String): String = UiLocalizer.category(c)

    private fun localizeSubcategory(s: String): String = UiLocalizer.subcategory(s)

}