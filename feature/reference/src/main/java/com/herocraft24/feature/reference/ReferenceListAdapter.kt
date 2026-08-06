package com.herocraft24.feature.reference

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.feature.reference.databinding.CardReferenceItemBinding

class ReferenceListAdapter(
    private val items: List<ReferenceListItem>,
    private val onItemClick: (ReferenceListItem) -> Unit
) : RecyclerView.Adapter<ReferenceListAdapter.ViewHolder>() {

    data class ReferenceListItem(
        val fullId: String,
        val name: String,
        val subtitle: String,
        val category: String = "",
        val source: String = "",
        val subcategory: List<String> = emptyList<String>(),
        val rarity: String = "",
        val materialHasCost: Boolean = false,
        val materialConsumable: Boolean = false,
        val size: String = "",
        val creatureType: String = "",
        val challengeRating: Double = 0.0,
        val environment: List<String> = emptyList<String>(),
        val isSwarm: Boolean = false,
        val hitDie: Int? = null
    )

    class ViewHolder(val binding: CardReferenceItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardReferenceItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.itemName.text = item.name
        holder.binding.itemSubtitle.text = item.subtitle
        holder.binding.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
