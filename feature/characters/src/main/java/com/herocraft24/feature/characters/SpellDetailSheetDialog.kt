package com.herocraft24.feature.characters

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.herocraft24.core.model.Spell
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.CardBuilder
import com.herocraft24.feature.characters.databinding.DialogSpellDetailSheetBinding

class SpellDetailSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpellDetailSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var spellId: String = ""
    private var charId: String = ""
    private var ability: String = "intelligence"

    companion object {
        private const val ARG_SPELL_ID = "spellId"
        private const val ARG_CHAR_ID = "charId"
        private const val ARG_ABILITY = "ability"

        fun newInstance(spellId: String, charId: String, ability: String): SpellDetailSheetDialog {
            return SpellDetailSheetDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SPELL_ID, spellId)
                    putString(ARG_CHAR_ID, charId)
                    putString(ARG_ABILITY, ability)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellDetailSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spellId = arguments?.getString(ARG_SPELL_ID) ?: ""
        charId = arguments?.getString(ARG_CHAR_ID) ?: ""
        ability = arguments?.getString(ARG_ABILITY) ?: "intelligence"

        val spell = vm.getSpell(spellId)
        if (spell == null) {
            binding.toolbar.title = "Заклинание не найдено"
            CardBuilder.showNotFound(requireContext(), binding.detailContent, "Заклинание не найдено")
            return
        }
        render(spell)
    }

    private fun render(s: Spell) {
        binding.toolbar.title = s.name.get()
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val ctx = requireContext()
        val target = binding.detailContent
        target.removeAllViews()

        val material = s.material
        val saveThrow = s.saving_throw
        val atkType = s.attack_type
        val aoe = s.area_of_effect
        val higherLevels = s.higher_levels
        val dmg = s.damage

        CardBuilder.addSection(ctx, target, "Краткая информация") {
            val levelStr = if (s.level == 0) "Заговор" else "Уровень ${s.level}"
            CardBuilder.addRow(this, "Уровень", levelStr)
            CardBuilder.addRow(this, "Школа", UiLocalizer.school(s.school))
            CardBuilder.addRow(this, "Время сотворения", s.casting_time)
            CardBuilder.addRow(this, "Длительность", s.duration)
            CardBuilder.addRow(this, "Дистанция", s.range?.text ?: s.range?.type ?: "—")
            CardBuilder.addRow(this, "Компоненты", s.components.joinToString(", ") { localizeComponent(it) })
            if (material != null) CardBuilder.addRow(this, "Материал", material)
            CardBuilder.addRow(this, "Концентрация", if (s.concentration) "Да" else "Нет")
            CardBuilder.addRow(this, "Ритуал", if (s.ritual) "Да" else "Нет")
            if (saveThrow != null) CardBuilder.addRow(this, "Спасбросок", saveThrow.replaceFirstChar { it.uppercase() })
            if (atkType != null) CardBuilder.addRow(this, "Тип атаки", atkType.replaceFirstChar { it.uppercase() })
            if (aoe != null) CardBuilder.addRow(this, "Область", "${aoe.size} ft ${aoe.type}")
        }

        CardBuilder.addSection(ctx, target, "Описание") {
            addView(buildTextView(s.description.get()))
        }

        if (higherLevels != null) {
            CardBuilder.addSection(ctx, target, "На более высоких уровнях") {
                addView(buildTextView(higherLevels.get()))
            }
        }

        if (dmg != null) {
            CardBuilder.addSection(ctx, target, "Урон") {
                CardBuilder.addRow(this, "Тип урона", dmg.damage_type.replaceFirstChar { it.uppercase() })
                if (dmg.damage_at_slot_level.isNotEmpty()) {
                    CardBuilder.addText(this, "Урон по уровню ячейки:")
                    for ((slot, dice) in dmg.damage_at_slot_level) {
                        CardBuilder.addText(this, "  $slot: $dice")
                    }
                }
                val charLevels = dmg.damage_at_character_level
                if (charLevels != null && charLevels.isNotEmpty()) {
                    CardBuilder.addText(this, "Урон по уровню персонажа:")
                    for ((lvl, dice) in charLevels) {
                        CardBuilder.addText(this, "  $lvl: $dice")
                    }
                }
            }
        }

        if (s.classes.isNotEmpty()) {
            CardBuilder.addSection(ctx, target, "Классы") {
                CardBuilder.addText(this, s.classes.mapNotNull { id -> vm.resolveName(id) ?: id }.joinToString(", "))
            }
        }

        CardBuilder.addSourceSection(ctx, target, "Источник", s.source)
    }

    private fun buildTextView(text: String): TextView {
        return TextView(requireContext()).apply {
            setText(text)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        }
    }

    private fun localizeComponent(c: String): String = when (c.uppercase()) {
        "V" -> "В"
        "S" -> "С"
        "M" -> "М"
        else -> c
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
