package com.herocraft24.feature.spells

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.herocraft24.core.model.Spell
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.CardBuilder
import com.herocraft24.core.ui.util.ItemLinkifier
import com.herocraft24.feature.spells.databinding.FragmentSpellDetailBinding
import com.herocraft24.feature.spells.util.SpellComponentLocalizer

class SpellDetailFragment : Fragment() {

    private var _binding: FragmentSpellDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SpellsViewModel by viewModels()
    private var spellId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpellDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spellId = arguments?.getString("spellId") ?: ""
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val spell = viewModel.getSpell(spellId)
        if (spell == null) {
            binding.toolbar.title = getString(R.string.spell_not_found)
            CardBuilder.showNotFound(requireContext(), binding.detailContent, getString(R.string.spell_not_found))
            return
        }
        render(spell)
    }

    private fun render(s: Spell) {
        binding.toolbar.title = s.name.get()

        val material = s.material
        val saveThrow = s.saving_throw
        val atkType = s.attack_type
        val aoe = s.area_of_effect
        val higherLevels = s.higher_levels
        val dmg = s.damage
        val ctx = requireContext()
        val target = binding.detailContent

        CardBuilder.addSection(ctx, target, getString(R.string.spell_quick_info)) {
            val levelStr = if (s.level == 0) getString(R.string.spell_cantrip) else "${getString(R.string.spell_level)} ${s.level}"
            CardBuilder.addRow(this, getString(R.string.spell_level), levelStr)
            CardBuilder.addRow(this, getString(R.string.spell_school), UiLocalizer.school(s.school))
            CardBuilder.addRow(this, getString(R.string.spell_casting_time), s.casting_time)
            CardBuilder.addRow(this, getString(R.string.spell_duration), s.duration)
            CardBuilder.addRow(this, getString(R.string.spell_range), s.range?.text ?: s.range?.type ?: "—")
            CardBuilder.addRow(this, getString(R.string.spell_components), s.components.joinToString(", ") { SpellComponentLocalizer.localizeComponent(ctx, it) })
            if (material != null) CardBuilder.addRow(this, getString(R.string.spell_material), material)
            CardBuilder.addRow(this, getString(R.string.spell_concentration), if (s.concentration) getString(R.string.spell_yes) else getString(R.string.spell_no))
            CardBuilder.addRow(this, getString(R.string.spell_ritual), if (s.ritual) getString(R.string.spell_yes) else getString(R.string.spell_no))
            if (saveThrow != null) CardBuilder.addRow(this, getString(R.string.spell_saving_throw), saveThrow.replaceFirstChar { it.uppercase() })
            if (atkType != null) CardBuilder.addRow(this, getString(R.string.spell_attack_type), atkType.replaceFirstChar { it.uppercase() })
            if (aoe != null) CardBuilder.addRow(this, getString(R.string.spell_area), "${aoe.size} ft ${aoe.type}")
        }

        CardBuilder.addSection(ctx, target, getString(R.string.spell_description)) {
            addView(buildLinkedTextView(s.description.get()))
        }

        if (higherLevels != null) {
            CardBuilder.addSection(ctx, target, getString(R.string.spell_at_higher_levels)) {
                addView(buildLinkedTextView(higherLevels.get()))
            }
        }

        if (dmg != null) {
            CardBuilder.addSection(ctx, target, getString(R.string.spell_damage)) {
                CardBuilder.addRow(this, getString(R.string.spell_damage), dmg.damage_type.replaceFirstChar { it.uppercase() })
                if (dmg.damage_at_slot_level.isNotEmpty()) {
                    CardBuilder.addText(this, getString(R.string.spell_damage_per_slot))
                    for ((slot, dice) in dmg.damage_at_slot_level) {
                        CardBuilder.addText(this, "  $slot: $dice")
                    }
                }
                val charLevels = dmg.damage_at_character_level
                if (charLevels != null && charLevels.isNotEmpty()) {
                    CardBuilder.addText(this, getString(R.string.spell_damage_per_character_level))
                    for ((lvl, dice) in charLevels) {
                        CardBuilder.addText(this, "  $lvl: $dice")
                    }
                }
            }
        }

        if (s.classes.isNotEmpty()) {
            CardBuilder.addSection(ctx, target, getString(R.string.spell_classes)) {
                CardBuilder.addText(this, s.classes.mapNotNull { id -> viewModel.resolveName(id) ?: id }.joinToString(", "))
            }
        }

        if (s.subclasses.isNotEmpty()) {
            CardBuilder.addSection(ctx, target, "Подклассы") {
                CardBuilder.addText(this, s.subclasses.joinToString(", ") { sub ->
                    val subclassName = viewModel.resolveName(sub) ?: sub
                    val parentClass = viewModel.subclassToClassMap[sub]
                    if (parentClass != null) "$subclassName ($parentClass)" else subclassName
                })
            }
        }

        CardBuilder.addSourceSection(ctx, target, getString(R.string.spell_source), s.source)
    }

    private fun buildLinkedTextView(text: String): TextView {
        val marker = ItemLinkifier.stripMarkers(text)
        val spannable = SpannableStringBuilder(marker.text)
        val matches = ItemLinkifier.findRanges(marker.text, viewModel.conditionBucketsCache, marker.excludedRanges)
        for ((start, end, fullId) in matches) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val b = Bundle().apply { putString("conditionId", fullId) }
                    Navigation.findNavController(widget).navigate(R.id.spellConditionDetail, b)
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                    ds.color = ds.linkColor
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return TextView(requireContext()).apply {
            setText(spannable)
            movementMethod = LinkMovementMethod.getInstance()
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
