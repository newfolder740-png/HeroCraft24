package com.herocraft24.feature.characters

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.card.MaterialCardView
import com.herocraft24.core.ui.util.dp

class SheetSpellsFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()

    private val schoolColors = mapOf(
        "abjuration" to 0xFF2196F3.toInt(),    // blue
        "conjuration" to 0xFFFF9800.toInt(),    // orange
        "divination" to 0xFFFFEB3B.toInt(),      // yellow
        "enchantment" to 0xFFE91E63.toInt(),     // pink
        "evocation" to 0xFFF44336.toInt(),       // red
        "illusion" to 0xFF9C27B0.toInt(),        // purple
        "necromancy" to 0xFF4CAF50.toInt(),      // green
        "transmutation" to 0xFF00BCD4.toInt(),   // cyan
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_spells, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val charId = arguments?.getString("characterId") ?: return
        val char = vm.getCharacter(charId) ?: return
        val content = view.findViewById<LinearLayout>(R.id.content)
        render(content, char)
    }

    private fun render(content: LinearLayout, char: CharacterData) {
        content.removeAllViews()
        val ctx = requireContext()
        val cls = vm.getClassInfo(char.classId)
        val spellAbility = cls?.spellcasting?.ability

        if (spellAbility == null) {
            content.addView(TextView(ctx).apply {
                text = "Этот класс не заклинает заклинания"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(32, 64, 32, 32)
            })
            return
        }

        val abMod = vm.modifier(char.abilityScores[spellAbility] ?: 10)
        val spellAttack = vm.spellAttack(char)
        val spellDC = vm.spellDC(char)
        val abNames = mapOf("strength" to "СИЛ", "dexterity" to "ЛОВ", "constitution" to "ТЕЛ", "intelligence" to "ИНТ", "wisdom" to "МДР", "charisma" to "ХАР")

        // ── Spell stats table ──
        val statsCard = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8.dp(ctx))
            }
            radius = 16f
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
        }
        val statsTable = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(12.dp(ctx), 8.dp(ctx), 12.dp(ctx), 8.dp(ctx)) }
        statsTable.addView(tableRow("Мод. закл. хар.", abNames[spellAbility] ?: spellAbility, formatBonus(abMod)))
        statsTable.addView(tableRow("Сл. спасброска", "", "$spellDC"))
        statsTable.addView(tableRow("Бонус атаки", "", formatBonus(spellAttack)))
        statsCard.addView(statsTable)
        content.addView(statsCard)

        // ── Spell slots — 3×3 grid ──
        if (char.spellSlots.isNotEmpty()) {
            sectionTitle(content, "Ячейки заклинаний")
            val sortedSlots = char.spellSlots.toSortedMap(compareBy<String> { it.toIntOrNull() ?: 0 })
            val rows = sortedSlots.entries.chunked(3)
            for (row in rows) {
                val rowLl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for ((level, slot) in row) {
                    val slotCard = MaterialCardView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(2.dp(ctx), 2.dp(ctx), 2.dp(ctx), 2.dp(ctx))
                        }
                        radius = 12f
                        setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainer))
                        strokeWidth = 0
                    }
                    val inner = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(4.dp(ctx), 6.dp(ctx), 4.dp(ctx), 6.dp(ctx))
                    }
                    inner.addView(TextView(ctx).apply {
                        text = "Уровень $level"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                    })
                    val stars = StringBuilder()
                    repeat(slot.total) { i -> stars.append(if (i < slot.total - slot.used) "★" else "☆") }
                    inner.addView(TextView(ctx).apply {
                        text = stars.toString()
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    })
                    slotCard.addView(inner)
                    rowLl.addView(slotCard)
                }
                content.addView(rowLl)
            }
        }

        // ── Spell list with colored left border ──
        val sp = char.spells
        if (sp != null) {
            val allSpells = mutableListOf<Triple<String, String, String>>() // category, name, id
            sp.cantrips.forEach { id -> vm.resolveName(id)?.let { allSpells.add(Triple("Заговор", it, id)) } }
            sp.prepared.forEach { id -> vm.resolveName(id)?.let { allSpells.add(Triple("Подготовлено", it, id)) } }
            sp.known.forEach { id -> vm.resolveName(id)?.let { allSpells.add(Triple("Известные", it, id)) } }

            if (allSpells.isNotEmpty()) {
                sectionTitle(content, "Заклинания")
                allSpells.forEach { (_, name, id) ->
                    val spell = vm.repository.getSpell(id)
                    val school = spell?.school?.lowercase() ?: ""
                    val borderColor = schoolColors[school] ?: 0xFF9E9E9E.toInt()

                    val card = MaterialCardView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 2.dp(ctx), 0, 2.dp(ctx))
                        }
                        radius = 12f
                        setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
                        strokeWidth = 0
                    }
                    val cardContent = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8.dp(ctx), 8.dp(ctx), 8.dp(ctx))
                    }
                    // Left color bar
                    cardContent.addView(View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(4.dp(ctx), LinearLayout.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(borderColor)
                    })
                    // Text content
                    val textContainer = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8.dp(ctx), 0, 0, 0)
                    }
                    textContainer.addView(TextView(ctx).apply {
                        text = name
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    })
                    val levelText = spell?.level?.let { "$it уровень" } ?: "Заговор"
                    val schoolRu = spell?.school?.let { schoolNameRu(it) } ?: ""
                    if (schoolRu.isNotEmpty()) {
                        textContainer.addView(TextView(ctx).apply {
                            text = "$levelText • $schoolRu"
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                            setTextColor(0xFF666666.toInt())
                        })
                    }
                    cardContent.addView(textContainer)
                    card.addView(cardContent)
                    content.addView(card)
                }
            }
        }
    }

    private fun schoolNameRu(school: String): String = when (school.lowercase()) {
        "abjuration" -> "Ограждение"
        "conjuration" -> "Вызов"
        "divination" -> "Прорицание"
        "enchantment" -> "Очарование"
        "evocation" -> "Воплощение"
        "illusion" -> "Иллюзия"
        "necromancy" -> "Некромантия"
        "transmutation" -> "Преобразование"
        else -> school.replaceFirstChar { it.uppercase() }
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
}
