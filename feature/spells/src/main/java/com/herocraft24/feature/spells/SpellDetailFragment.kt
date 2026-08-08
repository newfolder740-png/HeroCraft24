package com.herocraft24.feature.spells

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.herocraft24.core.model.Spell
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.ItemLinkifier
import com.herocraft24.feature.spells.databinding.FragmentSpellDetailBinding

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
            binding.detailContent.addView(TextView(requireContext()).apply {
                text = getString(R.string.spell_not_found)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(32, 64, 32, 32)
            })
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

        addSection(getString(R.string.spell_quick_info)) {
            val levelStr = if (s.level == 0) getString(R.string.spell_cantrip) else "${getString(R.string.spell_level)} ${s.level}"
            addRow(getString(R.string.spell_level), levelStr)
            addRow(getString(R.string.spell_school), UiLocalizer.school(s.school))
            addRow(getString(R.string.spell_casting_time), s.casting_time)
            addRow(getString(R.string.spell_duration), s.duration)
            addRow(getString(R.string.spell_range), s.range?.text ?: s.range?.type ?: "—")
            addRow(getString(R.string.spell_components), s.components.joinToString(", ") { localizeComponent(it) })
            if (material != null) addRow(getString(R.string.spell_material), material)
            addRow(getString(R.string.spell_concentration), if (s.concentration) getString(R.string.spell_yes) else getString(R.string.spell_no))
            addRow(getString(R.string.spell_ritual), if (s.ritual) getString(R.string.spell_yes) else getString(R.string.spell_no))
            if (saveThrow != null) addRow(getString(R.string.spell_saving_throw), saveThrow.replaceFirstChar { it.uppercase() })
            if (atkType != null) addRow(getString(R.string.spell_attack_type), atkType.replaceFirstChar { it.uppercase() })
            if (aoe != null) addRow(getString(R.string.spell_area), "${aoe.size} ft ${aoe.type}")
        }

        addSection(getString(R.string.spell_description)) { addView(buildLinkedTextView(s.description.get())) }

        if (higherLevels != null) {
            addSection(getString(R.string.spell_at_higher_levels)) { addView(buildLinkedTextView(higherLevels.get())) }
        }

        if (dmg != null) {
            addSection(getString(R.string.spell_damage)) {
                addRow(getString(R.string.spell_damage), dmg.damage_type.replaceFirstChar { it.uppercase() })
                if (dmg.damage_at_slot_level.isNotEmpty()) {
                    addText(getString(R.string.spell_damage_per_slot))
                    for ((slot, dice) in dmg.damage_at_slot_level) {
                        addText("  $slot: $dice")
                    }
                }
                val charLevels = dmg.damage_at_character_level
                if (charLevels != null && charLevels.isNotEmpty()) {
                    addText(getString(R.string.spell_damage_per_character_level))
                    for ((lvl, dice) in charLevels) {
                        addText("  $lvl: $dice")
                    }
                }
            }
        }

        if (s.classes.isNotEmpty()) {
            addSection(getString(R.string.spell_classes)) {
                addText(s.classes.mapNotNull { id -> viewModel.resolveName(id) ?: id }.joinToString(", "))
            }
        }

        if (s.subclasses.isNotEmpty()) {
            addSection("Подклассы") {
                addText(s.subclasses.joinToString(", ") { sub ->
                    val parentClass = viewModel.subclassToClassMap[sub]
                    if (parentClass != null) "$sub ($parentClass)" else sub
                })
            }
        }

        addSection(getString(R.string.spell_source)) {
            addText("${s.source.book.get()} (${s.source.abbreviation})${if (s.source.page != null) ", p. ${s.source.page}" else ""}")
        }
    }

    private fun localizeComponent(component: String): String = when (component.trim().uppercase()) {
        "V" -> getString(R.string.spell_comp_verbal)
        "S" -> getString(R.string.spell_comp_somatic)
        "M" -> getString(R.string.spell_comp_material)
        else -> component
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

    private fun addSection(title: String, block: LinearLayout.() -> Unit) {
        val titleView = TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 24, 0, 8)
        }
        binding.detailContent.addView(titleView)
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8)
        }
        container.block()
        binding.detailContent.addView(container)
    }

    private fun LinearLayout.addRow(label: String, value: String) {
        val row = TextView(requireContext()).apply {
            val text = "$label: $value"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            this.text = SpannableString(text).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, label.length + 1, 0)
            }
        }
        addView(row)
    }

    private fun LinearLayout.addText(text: String) {
        addView(TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}