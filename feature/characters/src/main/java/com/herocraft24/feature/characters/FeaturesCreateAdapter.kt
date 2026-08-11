package com.herocraft24.feature.characters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Feature
import com.herocraft24.core.ui.render.ExpandableCard
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardFeatureCreateBinding

class FeaturesCreateAdapter(
    private val onFeatureChoiceChanged: (String, String?) -> Unit,
    private val initialFeatureChoices: Map<String, String> = emptyMap()
) : RecyclerView.Adapter<FeaturesCreateAdapter.ViewHolder>() {

    private var items: List<Feature> = emptyList()
    private var expandedPosition: Int = -1
    private val openIds = mutableSetOf<String>()
    private val featureChoices = mutableMapOf<String, String?>().apply {
        putAll(initialFeatureChoices)
    }

    fun submitList(list: List<Feature>) {
        items = list
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feature = items[position]
        val isExpanded = position == expandedPosition

        val levelSuffix = feature.level?.let { "Ур. $it: " } ?: ""
        holder.binding.featureTitle.text = "$levelSuffix${feature.name.get()}"
        holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.binding.headerRow.setOnClickListener {
            val prevExpanded = expandedPosition
            expandedPosition = if (isExpanded) -1 else position
            if (prevExpanded >= 0) notifyItemChanged(prevExpanded)
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
        }

        if (isExpanded) {
            buildExpandedContent(holder.binding.expandedContent, feature, position)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun buildExpandedContent(container: LinearLayout, feature: Feature, position: Int) {
        container.removeAllViews()
        val ctx = container.context

        // Description
        container.addView(TextView(ctx).apply {
            text = feature.description.get()
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, 8.dp(ctx))
        })

        // Choice dropdown
        val choice = feature.choice
        if (choice != null && choice.options.isNotEmpty()) {
            val contentRepo = ContentRepository.get(ctx)
            val optionNames = choice.options.mapNotNull { id ->
                contentRepo.resolveName(id) ?: id.substringAfterLast(":")
            }

            val choiceContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
            }

            val label = if (choice.count > 1) {
                "Выберите ${choice.count} варианта:"
            } else {
                "Выберите вариант:"
            }
            choiceContainer.addView(TextView(ctx).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 0, 0, 4.dp(ctx))
            })

            val dropdown = com.google.android.material.textfield.MaterialAutoCompleteTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                inputType = android.text.InputType.TYPE_NULL
                threshold = 0
                isFocusableInTouchMode = false
                hint = "Выберите вариант"
                setOnClickListener { showDropDown() }
                setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, optionNames))

                // Restore selection
                featureChoices[feature.id]?.let { selectedId ->
                    val idx = choice.options.indexOf(selectedId)
                    if (idx >= 0) setText(optionNames[idx], false)
                }

                setOnItemClickListener { _, _, pos, _ ->
                    val selectedId = choice.options.getOrNull(pos)
                    featureChoices[feature.id] = selectedId
                    onFeatureChoiceChanged(feature.id, selectedId)
                }
            }
            choiceContainer.addView(dropdown)
            container.addView(choiceContainer)
        }
    }
}
