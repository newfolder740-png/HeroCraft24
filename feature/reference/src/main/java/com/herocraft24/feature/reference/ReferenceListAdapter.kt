package com.herocraft24.feature.reference

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.ReferenceListItem
import com.herocraft24.feature.reference.databinding.CardReferenceItemBinding

class ReferenceListAdapter(
    private val items: List<ReferenceListItem>,
    private val onItemClick: (ReferenceListItem) -> Unit
) : RecyclerView.Adapter<ReferenceListAdapter.ViewHolder>() {

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
