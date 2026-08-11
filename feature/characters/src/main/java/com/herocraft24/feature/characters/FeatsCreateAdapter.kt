package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.Feat
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardFeatureCreateBinding

class FeatsCreateAdapter : RecyclerView.Adapter<FeatsCreateAdapter.ViewHolder>() {

    private var items: List<Feat> = emptyList()
    private var expandedPosition: Int = -1

    fun submitList(list: List<Feat>) {
        items = list
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feat = items[position]
        val isExpanded = position == expandedPosition

        holder.binding.featureTitle.text = feat.name.get()
        holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.binding.headerRow.setOnClickListener {
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            if (prevExpanded >= 0) notifyItemChanged(prevExpanded)
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
        }

        if (isExpanded) {
            holder.binding.expandedContent.removeAllViews()
            val ctx = holder.binding.expandedContent.context
            holder.binding.expandedContent.addView(TextView(ctx).apply {
                text = feat.description.get()
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 0, 0, 8.dp(ctx))
            })
        }
    }

    override fun getItemCount(): Int = items.size
}
