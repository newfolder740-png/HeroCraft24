package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.model.Feat
import com.herocraft24.core.model.Feature
import com.herocraft24.core.model.Metamagic
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardFeatureCreateBinding
import android.graphics.Typeface
import kotlinx.coroutines.launch

class SheetAbilitiesFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_abilities, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val charId = arguments?.getString("characterId") ?: return
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                val char = list.find { it.id == charId } ?: vm.getCharacter(charId)
                char?.let {
                    recyclerView.layoutManager = LinearLayoutManager(requireContext())
                    recyclerView.adapter = SheetAbilitiesAdapter(
                        items = buildItems(it),
                        onToggleResource = { featureId -> vm.toggleFeatureResource(charId, featureId) },
                        onIncrementResource = { featureId -> vm.incrementFeatureResource(charId, featureId) },
                        onDecrementResource = { featureId -> vm.decrementFeatureResource(charId, featureId) }
                    )
                }
            }
        }
    }

    private fun buildItems(char: CharacterData): List<AbilityItem> {
        val items = mutableListOf<AbilityItem>()

        // Feature resource cards (above class features)
        val featureResources = vm.getEffectiveFeatureResources(char)
        for ((featureId, state) in featureResources) {
            val feature = vm.repository.getFeature(featureId) ?: continue
            val res = feature.resource ?: continue
            items.add(AbilityItem.ResourceCard(
                featureId = featureId,
                title = feature.name.get(),
                shape = res.shape,
                total = state.total,
                used = state.used
            ))
        }

        // Class features
        val cls = vm.getClassInfo(char.classId)
        val selectedMetamagicIds = mutableSetOf<String>()
        if (cls != null) {
            val allClassIds = char.classLevels.keys + char.classId
            val allFeatures = mutableListOf<com.herocraft24.core.model.Feature>()
            for (cid in allClassIds) {
                val c = vm.getClassInfo(cid) ?: continue
                val levelInClass = char.classLevels[cid] ?: if (cid == char.classId) char.level else 0
                c.features
                    .filter { featureId ->
                        val levelMatch = Regex("_l(\\d+)_").find(featureId)
                        val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                        featureLevel <= levelInClass
                    }
                    .mapNotNull { vm.repository.getFeature(it) }
                    .filter { !it.is_placeholder }
                    .let { allFeatures.addAll(it) }
            }
            if (allFeatures.isNotEmpty()) {
                items.add(AbilityItem.SectionHeader("Умения класса"))
                allFeatures.forEach { f ->
                    val choiceText = resolveFeatureChoiceText(char, f)
                    items.add(AbilityItem.FeatureItem(f, choiceText))
                }
            }

            // Collect metamagic selections from class features
            for (f in allFeatures) {
                if (f.choice?.type == "metamagic") {
                    char.featureMultiChoices[f.id]?.let { selectedMetamagicIds.addAll(it) }
                }
            }

            // Subclass features
            if (char.subclassId != null) {
                val subclass = vm.repository.getSubclass(char.subclassId!!)
                if (subclass != null) {
                    val subFeatures = subclass.features
                        .filter { featureId ->
                            val levelMatch = Regex("_l(\\d+)_").find(featureId)
                            val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                            featureLevel <= char.level
                        }
                        .mapNotNull { vm.repository.getFeature(it) }
                        .filter { !it.is_placeholder }
                    if (subFeatures.isNotEmpty()) {
                        items.add(AbilityItem.SectionHeader("Умения подкласса"))
                        subFeatures.forEach { f ->
                            val choiceText = resolveFeatureChoiceText(char, f)
                            items.add(AbilityItem.FeatureItem(f, choiceText))
                        }
                    }

                    // Collect metamagic selections from subclass features (if any)
                    for (f in subFeatures) {
                        if (f.choice?.type == "metamagic") {
                            char.featureMultiChoices[f.id]?.let { selectedMetamagicIds.addAll(it) }
                        }
                    }
                }
            }

            // Metamagic section
            val metamagics = selectedMetamagicIds.mapNotNull { vm.repository.getMetamagic(it) }
            if (metamagics.isNotEmpty()) {
                items.add(AbilityItem.SectionHeader("Метамагия"))
                metamagics.forEach { items.add(AbilityItem.MetamagicItem(it)) }
            }
        }

        // Species traits — non-expandable cards
        val species = vm.getAllSpecies().find { it.id == char.speciesId.substringAfterLast(":") }
        val subspeciesId = char.subspeciesId
        val selectedSub = subspeciesId?.let { id -> species?.subspecies?.find { it.id == id } }
        if (species != null) {
            val effectiveTraits = buildEffectiveTraits(species, selectedSub)
            val visibleTraits = effectiveTraits.filter { t -> t.level == null || t.level!! <= char.level }
            if (visibleTraits.isNotEmpty()) {
                items.add(AbilityItem.SectionHeader("Умения вида"))
                visibleTraits.forEach { t ->
                    val choiceText = resolveTraitChoiceText(char, species.id, t)
                    items.add(AbilityItem.TraitItem(t.name.get(), t.description.get(), choiceText))
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

        return items
    }

    private fun resolveTraitChoiceText(char: CharacterData, speciesId: String, t: com.herocraft24.core.model.SpeciesTrait): String? {
        val choice = t.choice ?: return null
        if (choice.type == "spellcasting_ability") {
            val traitId = "trait_${speciesId}_${t.name.get()}"
            val chosen = char.featureChoices[traitId] ?: return null
            val abilityNames = mapOf(
                "intelligence" to "Интеллект",
                "wisdom" to "Мудрость",
                "charisma" to "Харизма"
            )
            return "Заклинательная характеристика: ${abilityNames[chosen] ?: chosen}"
        }
        return null
    }

    private fun resolveFeatureChoiceText(char: CharacterData, f: Feature): String? {
        val choice = f.choice ?: return null
        if (choice.type == "skill_expertise") {
            val skills = char.featureMultiChoices[f.id] ?: return null
            if (skills.isEmpty()) return null
            return skills.joinToString(", ") { UiLocalizer.skill(it) }
        }
        if (choice.type == "asi_or_feat") {
            val selectedFeatId = char.featureChoices[f.id] ?: return null
            val featName = vm.repository.resolveName(selectedFeatId) ?: selectedFeatId.substringAfterLast(":")
            val asi = char.asiChoices[f.id]
            if (asi != null) {
                val asiText = if (asi.mode == "plus1x2") {
                    val a1 = asiAbilityName(asi.ability1)
                    val a2 = asiAbilityName(asi.ability2)
                    if (a1 != null && a2 != null) "$a1 +1, $a2 +1" else featName
                } else {
                    val a1 = asiAbilityName(asi.ability1)
                    if (a1 != null) "$a1 +2" else featName
                }
                return "$featName ($asiText)"
            }
            return featName
        }
        return char.featureChoices[f.id]?.let { choiceId ->
            vm.repository.resolveName(choiceId) ?: choiceId.substringAfterLast(":")
        }
    }

    private fun asiAbilityName(key: String): String? {
        if (key.isEmpty()) return null
        return mapOf(
            "strength" to "Сила", "dexterity" to "Ловкость", "constitution" to "Телосложение",
            "intelligence" to "Интеллект", "wisdom" to "Мудрость", "charisma" to "Харизма"
        )[key]
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
        data class ResourceCard(val featureId: String, val title: String, val shape: String, val total: Int, val used: Int) : AbilityItem()
        data class FeatureItem(val feature: Feature, val choiceText: String? = null) : AbilityItem()
        data class TraitItem(val name: String, val description: String, val choiceText: String? = null) : AbilityItem()
        data class FeatItem(val feat: Feat) : AbilityItem()
        data class MetamagicItem(val metamagic: Metamagic) : AbilityItem()
    }

    class SheetAbilitiesAdapter(
        private val items: List<AbilityItem>,
        private val onToggleResource: ((String) -> Unit)? = null,
        private val onIncrementResource: ((String) -> Unit)? = null,
        private val onDecrementResource: ((String) -> Unit)? = null
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var expandedPosition = -1

        private val TYPE_HEADER = 0
        private val TYPE_FEATURE = 1
        private val TYPE_TRAIT = 2
        private val TYPE_FEAT = 3
        private val TYPE_RESOURCE = 4
        private val TYPE_METAMAGIC = 5

        override fun getItemViewType(position: Int) = when (items[position]) {
            is AbilityItem.SectionHeader -> TYPE_HEADER
            is AbilityItem.FeatureItem -> TYPE_FEATURE
            is AbilityItem.TraitItem -> TYPE_TRAIT
            is AbilityItem.FeatItem -> TYPE_FEAT
            is AbilityItem.ResourceCard -> TYPE_RESOURCE
            is AbilityItem.MetamagicItem -> TYPE_METAMAGIC
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderViewHolder(TextView(parent.context).apply {
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setPadding(0, 12.dp(parent.context), 0, 4.dp(parent.context))
                })
                TYPE_RESOURCE -> ResourceViewHolder(com.google.android.material.card.MaterialCardView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 4.dp(parent.context), 0, 4.dp(parent.context))
                    }
                    radius = 12f
                    setCardBackgroundColor(resolveColorAttr(context, com.google.android.material.R.attr.colorSurfaceContainer))
                    strokeWidth = 0
                })
                TYPE_METAMAGIC -> MetamagicViewHolder(CardFeatureCreateBinding.inflate(inflater, parent, false))
                else -> FeatureViewHolder(CardFeatureCreateBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderViewHolder -> {
                    (holder.itemView as TextView).text = (items[position] as AbilityItem.SectionHeader).title
                }
                is ResourceViewHolder -> {
                    val item = items[position] as AbilityItem.ResourceCard
                    bindResource(holder, item)
                }
                is MetamagicViewHolder -> {
                    val item = items[position] as AbilityItem.MetamagicItem
                    val isExpanded = position == expandedPosition
                    bindMetamagic(holder, item, position, isExpanded)
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

        private fun bindResource(holder: ResourceViewHolder, item: AbilityItem.ResourceCard) {
            val card = holder.itemView as com.google.android.material.card.MaterialCardView
            card.removeAllViews()
            val ctx = card.context

            val inner = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(12.dp(ctx), 8.dp(ctx), 12.dp(ctx), 8.dp(ctx))
            }
            inner.addView(TextView(ctx).apply {
                text = item.title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            val shapesContainer = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, 4.dp(ctx), 0, 0)
            }
            val filledRes = when (item.shape) {
                "flame" -> R.drawable.ic_resource_flame_filled
                "hexagon" -> R.drawable.ic_resource_hexagon_filled
                else -> R.drawable.ic_resource_hexagon_filled
            }
            val emptyRes = when (item.shape) {
                "flame" -> R.drawable.ic_resource_flame_empty
                "hexagon" -> R.drawable.ic_resource_hexagon_empty
                else -> R.drawable.ic_resource_hexagon_empty
            }

            // Split resource markers into rows of up to 10 so they fit on small screens.
            val perRow = 10
            var currentRow: android.widget.LinearLayout? = null
            for (i in 0 until item.total) {
                if (i % perRow == 0) {
                    currentRow = android.widget.LinearLayout(ctx).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                    }
                    shapesContainer.addView(currentRow)
                }
                val isFilled = i < (item.total - item.used)
                val shape = android.widget.ImageButton(ctx).apply {
                    setImageResource(if (isFilled) filledRes else emptyRes)
                    background = null
                    setPadding(2.dp(ctx), 2.dp(ctx), 2.dp(ctx), 2.dp(ctx))
                    layoutParams = android.widget.LinearLayout.LayoutParams(32.dp(ctx), 32.dp(ctx))
                    setOnClickListener {
                        if (isFilled) {
                            onDecrementResource?.invoke(item.featureId)
                        } else {
                            onIncrementResource?.invoke(item.featureId)
                        }
                    }
                }
                currentRow?.addView(shape)
            }
            inner.addView(shapesContainer)
            card.addView(inner)
        }

        private fun resolveColorAttr(ctx: android.content.Context, attr: Int): Int {
            val ta = ctx.theme?.obtainStyledAttributes(intArrayOf(attr))
            val color = ta?.getColor(0, 0) ?: 0
            ta?.recycle()
            return color
        }

        private fun bindMetamagic(holder: MetamagicViewHolder, item: AbilityItem.MetamagicItem, position: Int, isExpanded: Boolean) {
            val m = item.metamagic
            holder.binding.featureTitle.text = m.name.get()
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
                    text = m.description.get()
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 0, 0, 8.dp(ctx))
                })
                if (m.cost.isNotEmpty()) {
                    holder.binding.expandedContent.addView(TextView(ctx).apply {
                        text = "Стоимость: ${m.cost}"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(0xFF6750A4.toInt())
                    })
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
                if (item.choiceText != null) {
                    holder.binding.expandedContent.addView(TextView(ctx).apply {
                        text = item.choiceText
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(0xFF6750A4.toInt())
                    })
                }
            }
        }

        override fun getItemCount() = items.size

        class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
        class ResourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
        class MetamagicViewHolder(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)
        class FeatureViewHolder(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
