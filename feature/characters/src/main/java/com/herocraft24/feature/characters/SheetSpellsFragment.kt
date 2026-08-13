package com.herocraft24.feature.characters

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.core.model.SpellSchool
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.dp
import com.herocraft24.core.ui.util.schoolColor
import kotlinx.coroutines.launch

class SheetSpellsFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()

    private val abNames = mapOf(
        "strength" to "СИЛ", "dexterity" to "ЛОВ", "constitution" to "ТЕЛ",
        "intelligence" to "ИНТ", "wisdom" to "МДР", "charisma" to "ХАР"
    )

    private val spellcastingAbilities = listOf("intelligence", "wisdom", "charisma")

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_spells, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val charId = arguments?.getString("characterId") ?: return
        val char = vm.getCharacter(charId) ?: return
        val content = view.findViewById<LinearLayout>(R.id.content)
        render(content, char)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                list.find { it.id == charId }?.let { updated ->
                    content.removeAllViews()
                    render(content, updated)
                }
            }
        }
    }

    private fun render(content: LinearLayout, char: CharacterData) {
        val ctx = requireContext()

        val cls = vm.getClassInfo(char.classId)
        val isCaster = cls?.spellcasting != null ||
            SpellSlotsCounter.isSpellcaster(char.classId, char.subclassId)
        val hasInnateSpells = char.spells?.innateSpells?.any { it.value.isNotEmpty() } == true

        if (!isCaster && !hasInnateSpells) {
            content.addView(TextView(ctx).apply {
                text = "Этот класс не заклинает заклинания"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(32, 64, 32, 32)
            })
            return
        }

        val effectiveAbility = if (isCaster) {
            vm.getEffectiveSpellcastingAbility(char)
        } else {
            char.speciesSpellAbility ?: "charisma"
        }
        val abMod = vm.modifier(char.abilityScores[effectiveAbility] ?: 10)
        val spellAttack = char.proficiencyBonus + abMod
        val spellDC = 8 + char.proficiencyBonus + abMod

        // ── Spell stats table with spellcasting ability dropdown ──
        val statsCard = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8.dp(ctx))
            }
            radius = 16f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
        }
        val statsTable = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(ctx), 8.dp(ctx), 12.dp(ctx), 8.dp(ctx))
        }

        statsTable.addView(buildAbilityDropdownRow(ctx, char, effectiveAbility, abMod))
        statsTable.addView(tableRow("Сл. спасброска", "", "$spellDC"))
        statsTable.addView(tableRow("Бонус атаки", "", formatBonus(spellAttack)))
        statsCard.addView(statsTable)
        content.addView(statsCard)

        // ── Spell slots — dynamic frames with 4-pointed stars ──
        if (isCaster) {
            val slots = vm.getEffectiveSpellSlots(char)
            if (slots.isNotEmpty()) {
                sectionTitle(content, "Ячейки заклинаний")
                val sortedSlots = slots.toSortedMap(compareBy<String> { it.toIntOrNull() ?: 0 })
                for ((level, slot) in sortedSlots) {
                    content.addView(buildSlotFrame(ctx, char.id, level, slot.total, slot.used))
                }
            }
        }

        // ── Prepared spells section ──
        val preparedHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16.dp(ctx), 0, 4.dp(ctx))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        preparedHeader.addView(TextView(ctx).apply {
            text = "Подготовленные заклинания"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (isCaster) {
            preparedHeader.addView(AppCompatButton(ctx).apply {
                text = "+"
                setPadding(16.dp(ctx), 0, 16.dp(ctx), 0)
                minimumWidth = 0
                minHeight = 0
                minWidth = 0
                minimumHeight = 0
                background = null
                textSize = 20f
                setOnClickListener {
                    SpellPickerDialogFragment.newInstance(char.id, effectiveAbility)
                        .show(childFragmentManager, "SpellPicker")
                }
            })
        }
        content.addView(preparedHeader)

        // ── Prepared spell cards for current ability ──
        val preparedSpells = vm.getPreparedSpellSummaries(char, effectiveAbility)
        val innateSpellIds = vm.getInnateSpellIds(char, effectiveAbility)
        val alwaysPreparedIds = vm.getAlwaysPreparedSpellIds(char, effectiveAbility)
        if (preparedSpells.isEmpty()) {
            content.addView(TextView(ctx).apply {
                text = "Нет подготовленных заклинаний"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(0xFF666666.toInt())
                setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
            })
        } else {
            for (spell in preparedSpells) {
                val alwaysPrepared = spell.fullId in alwaysPreparedIds
                val deletable = spell.fullId !in innateSpellIds && !alwaysPrepared
                content.addView(buildPreparedSpellCard(ctx, char.id, spell, effectiveAbility, deletable, alwaysPrepared))
            }
        }
    }

    private fun buildAbilityDropdownRow(
        ctx: android.content.Context,
        char: CharacterData,
        currentAbility: String,
        abMod: Int
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        row.addView(TextView(ctx).apply {
            text = "Мод. закл. хар."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })

        val dropdownItems = spellcastingAbilities.map { abNames[it] ?: it }
        val dropdown = MaterialAutoCompleteTextView(ctx).apply {
            setText(abNames[currentAbility] ?: currentAbility, false)
            inputType = android.text.InputType.TYPE_NULL
            threshold = 0
            dropDownWidth = 600
            minWidth = 0
            isFocusableInTouchMode = false
            setOnClickListener { showDropDown() }
            setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, dropdownItems))
            setOnItemClickListener { _, _, position, _ ->
                val selectedAbility = spellcastingAbilities[position]
                vm.setSpellcastingAbilityOverride(char.id, selectedAbility)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8.dp(ctx), 0, 8.dp(ctx), 0) }
        }
        row.addView(dropdown)

        row.addView(TextView(ctx).apply {
            text = formatBonus(abMod)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTypeface(typeface, Typeface.BOLD)
        })

        return row
    }

    private fun buildSlotFrame(
        ctx: android.content.Context,
        charId: String,
        level: String,
        total: Int,
        used: Int
    ): View {
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 4.dp(ctx), 0, 4.dp(ctx))
            }
            radius = 12f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainer))
            strokeWidth = 0
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(ctx), 8.dp(ctx), 12.dp(ctx), 8.dp(ctx))
        }
        inner.addView(TextView(ctx).apply {
            text = "Уровень $level"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
        })
        val starsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.dp(ctx), 0, 0)
        }
        for (i in 0 until total) {
            val isFilled = i < (total - used)
            val star = ImageButton(ctx).apply {
                setImageResource(
                    if (isFilled) R.drawable.ic_spell_slot_filled else R.drawable.ic_spell_slot_empty
                )
                background = null
                setPadding(4.dp(ctx), 4.dp(ctx), 4.dp(ctx), 4.dp(ctx))
                layoutParams = LinearLayout.LayoutParams(36.dp(ctx), 36.dp(ctx))
                setOnClickListener {
                    if (isFilled) {
                        vm.decrementSpellSlot(charId, level)
                    } else {
                        vm.incrementSpellSlot(charId, level)
                    }
                }
            }
            starsRow.addView(star)
        }
        inner.addView(starsRow)
        card.addView(inner)
        return card
    }

    private fun buildPreparedSpellCard(
        ctx: android.content.Context,
        charId: String,
        spell: SpellSummary,
        ability: String,
        deletable: Boolean = true,
        alwaysPrepared: Boolean = false
    ): View {
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 2.dp(ctx), 0, 2.dp(ctx))
            }
            radius = 12f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
            isClickable = true
            isFocusable = true
            foreground = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use { ta ->
                ta.getDrawable(0)
            }
        }
        val cardContent = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dp(ctx), 8.dp(ctx), 8.dp(ctx))
        }

        val borderColor = ctx.schoolColor(SpellSchool.fromValue(spell.school))
        cardContent.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(4.dp(ctx), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(borderColor)
        })

        val textContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(ctx), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textContainer.addView(TextView(ctx).apply {
            text = spell.name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })

        val levelStr = if (spell.level == 0) "Заговор" else "${spell.level} уровень"
        val schoolRu = UiLocalizer.school(spell.school)
        textContainer.addView(TextView(ctx).apply {
            text = "$levelStr • $schoolRu"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(resolveColor(android.R.attr.textColorSecondary))
        })

        val badges = mutableListOf<String>()
        if (spell.concentration) badges.add("Концентрация")
        if (spell.ritual) badges.add("Ритуал")
        if (spell.components.isNotEmpty()) {
            badges.add(spell.components.joinToString("/") { c ->
                when (c.uppercase()) { "V" -> "В"; "S" -> "С"; "M" -> "М"; else -> c }
            })
        }
        spell.damageType?.let { dt -> badges.add(dt.replaceFirstChar { it.uppercase() }) }
        if (badges.isNotEmpty()) {
            textContainer.addView(TextView(ctx).apply {
                text = badges.joinToString(" • ")
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(resolveColor(android.R.attr.textColorSecondary))
                setPadding(0, 4.dp(ctx), 0, 0)
            })
        }

        cardContent.addView(textContainer)

        if (alwaysPrepared) {
            cardContent.addView(ImageView(ctx).apply {
                setImageResource(android.R.drawable.ic_lock_idle_lock)
                background = null
                setPadding(8.dp(ctx), 8.dp(ctx), 0, 8.dp(ctx))
                layoutParams = LinearLayout.LayoutParams(32.dp(ctx), 32.dp(ctx)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            })
        } else if (deletable) {
            val deleteBtn = ImageButton(ctx).apply {
                setImageResource(R.drawable.ic_delete)
                background = null
                setPadding(8.dp(ctx), 8.dp(ctx), 0, 8.dp(ctx))
                layoutParams = LinearLayout.LayoutParams(32.dp(ctx), 32.dp(ctx)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                setOnClickListener { vm.removePreparedSpell(charId, spell.fullId, ability) }
            }
            cardContent.addView(deleteBtn)
        }

        card.addView(cardContent)
        card.setOnClickListener {
            SpellDetailSheetDialog.newInstance(spell.fullId, charId, ability)
                .show(childFragmentManager, "SpellDetail")
        }
        return card
    }

    private fun tableRow(label: String, value2: String, value3: String): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(TextView(ctx).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
            addView(TextView(ctx).apply {
                text = value2
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(8.dp(ctx), 0, 8.dp(ctx), 0)
            })
            addView(TextView(ctx).apply {
                text = value3
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTypeface(typeface, Typeface.BOLD)
            })
        }
    }

    private fun sectionTitle(container: LinearLayout, title: String) {
        container.addView(TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 12.dp(context), 0, 4.dp(context))
        })
    }

    private fun formatBonus(value: Int) = if (value >= 0) "+$value" else "$value"

    private fun resolveColor(attr: Int): Int {
        val ta = requireContext().theme?.obtainStyledAttributes(intArrayOf(attr))
        val color = ta?.getColor(0, 0) ?: 0
        ta?.recycle()
        return color
    }

    private inline fun <T> android.content.res.TypedArray.use(block: (android.content.res.TypedArray) -> T): T {
        try { return block(this) } finally { recycle() }
    }
}
