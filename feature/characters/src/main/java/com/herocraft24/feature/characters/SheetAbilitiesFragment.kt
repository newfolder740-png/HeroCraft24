package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.Feat
import com.herocraft24.core.model.Feature
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardFeatureCreateBinding
import android.graphics.Typeface

class SheetAbilitiesFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_abilities, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val charId = arguments?.getString("characterId") ?: return
        val char = vm.getCharacter(charId) ?: return
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)

        val items = mutableListOf<AbilityItem>()

        // Class features
        val cls = vm.getClassInfo(char.classId)
        if (cls != null) {
            val l1Features = cls.features
                .filter { it.contains("_l1_") }
                .mapNotNull { vm.repository.getFeature(it) }
                .filter { !it.is_placeholder }
            if (l1Features.isNotEmpty()) {
                items.add(AbilityItem.SectionHeader("Умения класса"))
                l1Features.forEach { f ->
                    val choiceText = char.featureChoices[f.id]?.let { choiceId ->
                        vm.repository.resolveName(choiceId) ?: choiceId.substringAfterLast(":")
                    }
                    items.add(AbilityItem.FeatureItem(f, choiceText))
                }
            }
        }

        // Species traits — non-expandable cards
        val species = vm.getAllSpecies().find { it.id == char.speciesId.substringAfterLast(":") }
        val subspeciesId = char.subspeciesId
        val selectedSub = subspeciesId?.let { id -> species?.subspecies?.find { it.id == id } }
        if (species != null) {
            val effectiveTraits = buildEffectiveTraits(species, selectedSub)
            val level1Traits = effectiveTraits.filter { it.level == null || it.level == 1 }
            if (level1Traits.isNotEmpty()) {
                items.add(AbilityItem.SectionHeader("Умения вида"))
                level1Traits.forEach { t ->
                    items.add(AbilityItem.TraitItem(t.name.get(), t.description.get()))
                }
            }
        }

        // Feats
        val feats = mutableListOf<Feat>()
        val bg = vm.getAllBackgrounds().find { it.id == char.backgroundId.substringAfterLast(":") }
        bg?.feat?.let { vm.repository.getFeat(it)?.let { feats.add(it) } }
        for ((_, choiceId) in char.featureChoices) {
            vm.repository.getFeat(choiceId)?.let { if (it !in feats) feats.add(it) }
        }
        if (feats.isNotEmpty()) {
            items.add(AbilityItem.SectionHeader("Черты"))
            feats.forEach { items.add(AbilityItem.FeatItem(it)) }
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = SheetAbilitiesAdapter(items)
    }

    private fun buildEffectiveTraits(
        species: com.herocraft24.core.model.Species,
        selectedSub: com.herocraft24.core.model.SubspeciesInfo?
    ): List<com.herocraft24.core.model.SpeciesTrait> {
        val result = mutableListOf<com.herocraft24.core.model.SpeciesTrait>()
        for (trait in species.traits) {
            if (trait.is_placeholder && selectedSub != null) {
                result.addAll(selectedSub.traits)
            } else {
                result.add(trait)
            }
        }
        return result
    }

    sealed class AbilityItem {
        data class SectionHeader(val title: String) : AbilityItem()
        data class FeatureItem(val feature: Feature, val choiceText: String? = null) : AbilityItem()
        data class TraitItem(val name: String, val description: String) : AbilityItem()
        data class FeatItem(val feat: Feat) : AbilityItem()
    }

    class SheetAbilitiesAdapter(private val items: List<AbilityItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var expandedPosition = -1

        private val TYPE_HEADER = 0
        private val TYPE_FEATURE = 1
        private val TYPE_TRAIT = 2
        private val TYPE_FEAT = 3

        override fun getItemViewType(position: Int) = when (items[position]) {
            is AbilityItem.SectionHeader -> TYPE_HEADER
            is AbilityItem.FeatureItem -> TYPE_FEATURE
            is AbilityItem.TraitItem -> TYPE_TRAIT
            is AbilityItem.FeatItem -> TYPE_FEAT
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderViewHolder(TextView(parent.context).apply {
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setPadding(0, 12.dp(parent.context), 0, 4.dp(parent.context))
                })
                else -> FeatureViewHolder(CardFeatureCreateBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderViewHolder -> {
                    (holder.itemView as TextView).text = (items[position] as AbilityItem.SectionHeader).title
                }
                is FeatureViewHolder -> {
                    val item = items[position]
                    val isExpanded = position == expandedPosition
                    when (item) {
                        is AbilityItem.FeatureItem -> bindFeature(holder, item, position, isExpanded)
                        is AbilityItem.FeatItem -> bindFeat(holder, item, position, isExpanded)
                        is AbilityItem.TraitItem -> bindTrait(holder, item, position, isExpanded)
                        else -> {}
                    }
                }
            }
        }

        private fun bindFeature(holder: FeatureViewHolder, item: AbilityItem.FeatureItem, position: Int, isExpanded: Boolean) {
            val f = item.feature
            val levelSuffix = f.level?.let { "Уровень $it: " } ?: ""
            holder.binding.featureTitle.text = "$levelSuffix${f.name.get()}"
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.binding.headerRow.setOnClickListener {
                val prev = expandedPosition
                expandedPosition = if (isExpanded) -1 else position
                if (prev >= 0) notifyItemChanged(prev)
                if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
            }
            if (isExpanded) {
                holder.binding.expandedContent.removeAllViews()
                val ctx = holder.binding.expandedContent.context
                holder.binding.expandedContent.addView(TextView(ctx).apply {
                    text = f.description.get()
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 0, 0, 8.dp(ctx))
                })
                if (item.choiceText != null) {
                    holder.binding.expandedContent.addView(TextView(ctx).apply {
                        text = "Выбрано: ${item.choiceText}"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(0xFF6750A4.toInt())
                    })
                }
            }
        }

        private fun bindFeat(holder: FeatureViewHolder, item: AbilityItem.FeatItem, position: Int, isExpanded: Boolean) {
            val f = item.feat
            holder.binding.featureTitle.text = f.name.get()
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.binding.headerRow.setOnClickListener {
                val prev = expandedPosition
                expandedPosition = if (isExpanded) -1 else position
                if (prev >= 0) notifyItemChanged(prev)
                if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
            }
            if (isExpanded) {
                holder.binding.expandedContent.removeAllViews()
                val ctx = holder.binding.expandedContent.context
                holder.binding.expandedContent.addView(TextView(ctx).apply {
                    text = f.description.get()
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 0, 0, 8.dp(ctx))
                })
            }
        }

        private fun bindTrait(holder: FeatureViewHolder, item: AbilityItem.TraitItem, position: Int, isExpanded: Boolean) {
            holder.binding.featureTitle.text = item.name
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.binding.headerRow.setOnClickListener {
                val prev = expandedPosition
                expandedPosition = if (isExpanded) -1 else position
                if (prev >= 0) notifyItemChanged(prev)
                if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
            }
            if (isExpanded) {
                holder.binding.expandedContent.removeAllViews()
                val ctx = holder.binding.expandedContent.context
                holder.binding.expandedContent.addView(TextView(ctx).apply {
                    text = item.description
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 0, 0, 8.dp(ctx))
                })
            }
        }

        override fun getItemCount() = items.size

        class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
        class FeatureViewHolder(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
