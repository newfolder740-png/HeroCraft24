package com.herocraft24.feature.characters

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.util.Log
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.CharacterData

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SheetMainFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()
    private var charId: String? = null

    private val ALL_SKILLS = listOf(
        "athletics" to "strength",
        "acrobatics" to "dexterity",
        "sleight_of_hand" to "dexterity",
        "stealth" to "dexterity",
        "arcana" to "intelligence",
        "history" to "intelligence",
        "investigation" to "intelligence",
        "nature" to "intelligence",
        "religion" to "intelligence",
        "animal_handling" to "wisdom",
        "insight" to "wisdom",
        "medicine" to "wisdom",
        "perception" to "wisdom",
        "survival" to "wisdom",
        "deception" to "charisma",
        "intimidation" to "charisma",
        "performance" to "charisma",
        "persuasion" to "charisma"
    )

    private val SKILL_NAMES = mapOf(
        "athletics" to "Атлетика",
        "acrobatics" to "Акробатика",
        "sleight_of_hand" to "Ловкость рук",
        "stealth" to "Скрытность",
        "arcana" to "Тайная магия",
        "history" to "История",
        "investigation" to "Расследование",
        "nature" to "Природа",
        "religion" to "Религия",
        "animal_handling" to "Уход за животными",
        "insight" to "Проницательность",
        "medicine" to "Медицина",
        "perception" to "Восприятие",
        "survival" to "Выживание",
        "deception" to "Обман",
        "intimidation" to "Запугивание",
        "performance" to "Выступление",
        "persuasion" to "Убеждение"
    )

    private val AB_NAMES = mapOf(
        "strength" to "Сила", "dexterity" to "Ловкость", "constitution" to "Телосложение",
        "intelligence" to "Интеллект", "wisdom" to "Мудрость", "charisma" to "Харизма"
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_main, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        charId = arguments?.getString("characterId") ?: return
        val content = view.findViewById<LinearLayout>(R.id.content)
        content.clipChildren = false
        content.clipToPadding = false
        
        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                charId?.let { id ->
                    list.find { it.id == id }?.let { render(content, it) }
                }
            }
        }
    }

    private fun render(content: LinearLayout, char: CharacterData) {
        content.removeAllViews()
        val ctx = requireContext()

        val cls = vm.getClassInfo(char.classId)
        val speciesLocalId = char.speciesId.substringAfterLast(":")
        val species = vm.getAllSpecies().find { it.id == speciesLocalId }
        val bgLocalId = char.backgroundId.substringAfterLast(":")
        val bg = vm.getAllBackgrounds().find { it.id == bgLocalId }

        Log.d("SheetMain", "char.backgroundId=${char.backgroundId}, bg=${bg?.id}, bgSkills=${bg?.skill_proficiencies}, bgAbilityMode=${char.bgAbilityMode}, bgAbilityPlus2=${char.bgAbilityPlus2}, bgAbilityPlus1=${char.bgAbilityPlus1}")

        val className = cls?.name?.get() ?: char.classId.substringAfterLast(":")
        val subclassName = char.subclassId?.let { vm.repository.getSubclass(it)?.name?.get() }
        val speciesName = species?.name?.get() ?: char.speciesId.substringAfterLast(":")
        val bgName = bg?.name?.get() ?: char.backgroundId.substringAfterLast(":")
        val size = species?.size ?: "Ср"

        val effectiveScores = computeEffectiveScores(char, bg)
        val proficientSkills = computeProficientSkills(char, cls, bg)
        val proficientSaves = cls?.saving_throws?.toSet() ?: emptySet()

        val profBonus = char.proficiencyBonus
        val dexMod = vm.modifier(effectiveScores["dexterity"] ?: 10)
        val wisMod = vm.modifier(effectiveScores["wisdom"] ?: 10)
        val percBonus = if ("perception" in proficientSkills) profBonus else 0

        // ── Info cards ──
        val infoContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        fun makeCard(inner: View): MaterialCardView {
            return MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(4.dp(ctx), 4.dp(ctx), 4.dp(ctx), 4.dp(ctx))
                }
                radius = 12f
                setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
                strokeWidth = 0
                setPadding(10.dp(ctx), 10.dp(ctx), 10.dp(ctx), 10.dp(ctx))
                addView(inner)
            }
        }

        fun labelValue(label: String, value: String, centered: Boolean = false, largeValue: Boolean = false): LinearLayout {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                if (centered) {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                setPadding(0, 3.dp(ctx), 0, 3.dp(ctx))
                addView(TextView(ctx).apply {
                    text = label
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    if (centered) gravity = Gravity.CENTER_HORIZONTAL
                })
                addView(TextView(ctx).apply {
                    this.text = value
                    if (largeValue) {
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
                    } else {
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    }
                    if (centered) gravity = Gravity.CENTER_HORIZONTAL
                })
            }
        }

        fun rowOfCards(vararg cards: MaterialCardView): LinearLayout {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                cards.forEach { addView(it) }
            }
        }

        // Row 1: Class/Level/Subclass | Experience/Next Level
        val left1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        left1.addView(makeInfoDropdown(ctx, "Класс / Уровень", "$className Уровень ${char.level}"))
        val subclassLabel = labelValue("Подкласс", subclassName ?: "—")
        subclassLabel.visibility = if (subclassName != null) View.VISIBLE else View.INVISIBLE
        left1.addView(subclassLabel)
        val card1 = makeCard(left1)

        val right1 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        right1.addView(makeInfoInput(ctx, "Опыт", "${char.experience}"))
        val xpThresholds = listOf(0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000, 85000, 100000, 120000, 140000, 165000, 195000, 225000, 265000, 305000, 355000)
        val nextXp = xpThresholds.getOrElse(char.level) { 355000 }
        right1.addView(labelValue("След. уровень", "$nextXp"))
        val card2 = makeCard(right1)
        infoContainer.addView(rowOfCards(card1, card2))

        // Row 2: Species/Subspecies/Background | HP/Temp HP/Hit Dice
        val left2 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        left2.addView(labelValue("Вид", speciesName))
        val subSpeciesName = char.subspeciesId?.let { sid -> species?.subspecies?.find { it.id == sid }?.name?.get() }
        val subSpeciesLabel2 = labelValue("Род", subSpeciesName ?: "—")
        subSpeciesLabel2.visibility = if (subSpeciesName != null) View.VISIBLE else View.INVISIBLE
        left2.addView(subSpeciesLabel2)
        val bgLabel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 3.dp(ctx), 0, 3.dp(ctx)) }
        bgLabel.addView(TextView(ctx).apply { text = "Происхождение"; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)) })
        bgLabel.addView(TextView(ctx).apply { text = bgName; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium) })
        left2.addView(bgLabel)
        val card3 = makeCard(left2)

        val right2 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val hpRowCard = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hpRowCard.addView(TextView(ctx).apply { text = "Хиты: "; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall) })
        hpRowCard.addView(TextInputEditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("${char.hitPoints.current}")
            textSize = 16f
            minEms = 2
            maxEms = 3
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        hpRowCard.addView(TextView(ctx).apply { text = "/${char.hitPoints.max}"; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium) })
        right2.addView(hpRowCard)
        right2.addView(makeInfoInput(ctx, "Врем. хиты", if (char.hitPoints.temporary > 0) "${char.hitPoints.temporary}" else ""))
        val hitDie = cls?.hit_die ?: 6
        right2.addView(makeInfoDropdown(ctx, "Кости хитов", "d$hitDie ${char.hitDice.remaining}/${char.level}"))
        val card4 = makeCard(right2)
        infoContainer.addView(rowOfCards(card3, card4))

        // Row 3: Size/Speed/Initiative/Passive Perception | Death Saves
        val left3 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val row3a = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row3a.addView(labelValue("Размер", size).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        row3a.addView(labelValue("Скорость", "${char.speed} фт.").apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        left3.addView(row3a)
        val row3b = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row3b.addView(labelValue("Инициатива", formatBonus(dexMod)).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        row3b.addView(labelValue("Пасс. воспр.", "${10 + wisMod + percBonus}").apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        left3.addView(row3b)
        val card5 = makeCard(left3)

        val right3 = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        right3.addView(TextView(ctx).apply {
            text = "Спасы от смерти"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, 3.dp(ctx), 0, 3.dp(ctx))
        })
        var successes = char.deathSaves.successes
        var failures = char.deathSaves.failures
        val successBubbles = mutableListOf<TextView>()
        val failBubbles = mutableListOf<TextView>()
        val dsCardRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dsIconColumn = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        dsIconColumn.addView(TextView(ctx).apply { text = "♥"; textSize = 20f; setTextColor(0xFF4CAF50.toInt()); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(28.dp(ctx), LinearLayout.LayoutParams.WRAP_CONTENT) })
        dsIconColumn.addView(TextView(ctx).apply { text = "☠"; textSize = 20f; setTextColor(0xFFF44336.toInt()); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(28.dp(ctx), LinearLayout.LayoutParams.WRAP_CONTENT) })
        dsCardRow.addView(dsIconColumn)
        val dsBubblesColumn = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val successRowCard = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        repeat(3) { i ->
            val bubble = TextView(ctx).apply {
                text = if (i < successes) "●" else "○"
                setTextColor(if (i < successes) 0xFF4CAF50.toInt() else 0xFF999999.toInt())
                textSize = 24f
                setPadding(4.dp(ctx), 0, 4.dp(ctx), 0)
                setOnClickListener {
                    successes = if (i < successes) i else i + 1
                    successBubbles.forEachIndexed { idx, tv ->
                        tv.text = if (idx < successes) "●" else "○"
                        tv.setTextColor(if (idx < successes) 0xFF4CAF50.toInt() else 0xFF999999.toInt())
                    }
                }
            }
            successBubbles.add(bubble)
            successRowCard.addView(bubble)
        }
        dsBubblesColumn.addView(successRowCard)
        val failRowCard = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        repeat(3) { i ->
            val bubble = TextView(ctx).apply {
                text = if (i < failures) "●" else "○"
                setTextColor(if (i < failures) 0xFFF44336.toInt() else 0xFF999999.toInt())
                textSize = 24f
                setPadding(4.dp(ctx), 0, 4.dp(ctx), 0)
                setOnClickListener {
                    failures = if (i < failures) i else i + 1
                    failBubbles.forEachIndexed { idx, tv ->
                        tv.text = if (idx < failures) "●" else "○"
                        tv.setTextColor(if (idx < failures) 0xFFF44336.toInt() else 0xFF999999.toInt())
                    }
                }
            }
            failBubbles.add(bubble)
            failRowCard.addView(bubble)
        }
        dsBubblesColumn.addView(failRowCard)
        dsCardRow.addView(dsBubblesColumn)
        dsCardRow.addView(TextView(ctx).apply {
            text = "↻"
            textSize = 20f
            setPadding(8.dp(ctx), 0, 0, 0)
            setOnClickListener {
                successes = 0; failures = 0
                successBubbles.forEach { it.text = "○"; it.setTextColor(0xFF999999.toInt()) }
                failBubbles.forEach { it.text = "○"; it.setTextColor(0xFF999999.toInt()) }
            }
        })
        right3.addView(dsCardRow)
        val card6 = makeCard(right3)
        infoContainer.addView(rowOfCards(card5, card6))

        // Row 4: BM, AC, Shield, Inspiration
        val profCard = labelValue("БМ", formatBonus(profBonus), centered = true, largeValue = true)
        val totalAc = computeCharacterAC(char, dexMod)
        val acCard = labelValue("КЗ", "$totalAc", centered = true, largeValue = true)
        val shieldCard = labelValue("Щит", char.equippedShield?.let { computeShieldBonus(it) } ?: "—", centered = true, largeValue = true)
        val inspirationCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 3.dp(ctx), 0, 3.dp(ctx))
            addView(TextView(ctx).apply { text = "Вдохновение"; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall); setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)) })
            val star = TextView(ctx).apply {
                text = if (char.inspiration) "★" else "☆"
                textSize = 28f
                setTextColor(if (char.inspiration) 0xFFFFC107.toInt() else 0xFF999999.toInt())
                gravity = Gravity.CENTER
                setOnClickListener {
                    val newState = text == "☆"
                    text = if (newState) "★" else "☆"
                    setTextColor(if (newState) 0xFFFFC107.toInt() else 0xFF999999.toInt())
                }
            }
            addView(star)
        }
        infoContainer.addView(rowOfCards(makeCard(profCard), makeCard(acCard), makeCard(shieldCard), makeCard(inspirationCard)))

        content.addView(infoContainer)

        // ── Ability scores — 2 rows of 3 ──
        val abilities = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
        for (rowIdx in 0..1) {
            val rowLl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
            for (colIdx in 0..2) {
                val ab = abilities[rowIdx * 3 + colIdx]
                val score = effectiveScores[ab] ?: 10
                val mod = vm.modifier(score)
                val isProfSave = ab in proficientSaves
                val saveBonus = mod + if (isProfSave) profBonus else 0

                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(4.dp(ctx), 12.dp(ctx), 4.dp(ctx), 4.dp(ctx))
                    }
                }

                val modSize = 64.dp(ctx)
                val modBox = android.widget.FrameLayout(ctx).apply {
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = LinearLayout.LayoutParams(modSize, modSize).apply { gravity = Gravity.CENTER_HORIZONTAL }
                    background = GradientDrawable().apply {
                        setColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
                        setStroke(2.dp(ctx), resolveColor(com.google.android.material.R.attr.colorOutline))
                        cornerRadius = 12f
                    }
                }
                modBox.addView(TextView(ctx).apply {
                    text = formatBonus(mod)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
                    gravity = Gravity.CENTER
                    layoutParams = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                })
                val scoreBoxSize = 28.dp(ctx)
                val overlap = (scoreBoxSize / 3)
                val scoreBox = TextView(ctx).apply {
                    text = "$score"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(resolveColor(com.google.android.material.R.attr.colorSurface))
                        setStroke(2.dp(ctx), resolveColor(com.google.android.material.R.attr.colorOutline))
                        cornerRadius = 6f
                    }
                    layoutParams = android.widget.FrameLayout.LayoutParams(scoreBoxSize, scoreBoxSize).apply {
                        gravity = Gravity.START or Gravity.TOP
                        marginStart = -overlap
                        topMargin = -overlap
                    }
                }
                modBox.addView(scoreBox)
                col.addView(modBox)

                col.addView(TextView(ctx).apply {
                    text = AB_NAMES[ab] ?: ab
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                    gravity = Gravity.CENTER
                    setPadding(0, 2.dp(ctx), 0, 4.dp(ctx))
                })

                col.addView(makeSkillRow(ctx, isProfSave, "${formatBonus(saveBonus)} Спасбросок"))

                val skillsForAb = ALL_SKILLS.filter { it.second == ab }
                for ((skillId, _) in skillsForAb) {
                    val isProf = skillId in proficientSkills
                    val skillMod = mod + if (isProf) profBonus else 0
                    val skillName = SKILL_NAMES[skillId] ?: skillId.replaceFirstChar { it.uppercase() }
                    col.addView(makeSkillRow(ctx, isProf, "${formatBonus(skillMod)} $skillName"))
                }

                rowLl.addView(col)
            }
            content.addView(rowLl)
        }

        // ── Rest buttons ──
        val restRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
        restRow.addView(Button(ctx).apply { text = "Короткий отдых"; textSize = 12f; setPadding(16.dp(ctx), 0, 16.dp(ctx), 0) })
        restRow.addView(Button(ctx).apply { text = "Долгий отдых"; textSize = 12f; setPadding(16.dp(ctx), 0, 16.dp(ctx), 0) })
        content.addView(restRow)
    }

    private fun computeEffectiveScores(char: CharacterData, bg: com.herocraft24.core.model.Background?): Map<String, Int> {
        val scores = char.abilityScores.toMutableMap()
        if (bg != null && bg.ability_score_increases.isNotEmpty()) {
            val mode = char.bgAbilityMode
            if (mode == false || mode == null) {
                for (asi in bg.ability_score_increases) {
                    scores[asi.ability] = (scores[asi.ability] ?: 10) + 1
                }
            } else if (mode == true) {
                char.bgAbilityPlus2?.let { scores[it] = (scores[it] ?: 10) + 2 }
                char.bgAbilityPlus1?.let { scores[it] = (scores[it] ?: 10) + 1 }
            }
        }
        Log.d("SheetMain", "effectiveScores=$scores")
        return scores
    }

    private fun computeProficientSkills(char: CharacterData, cls: com.herocraft24.core.model.GameClass?, bg: com.herocraft24.core.model.Background?): Set<String> {
        val proficient = mutableSetOf<String>()
        proficient.addAll(char.classSkillChoices)
        bg?.skill_proficiencies?.let { proficient.addAll(it) }
        Log.d("SheetMain", "proficientSkills=$proficient, classSkills=${char.classSkillChoices}, bgSkills=${bg?.skill_proficiencies}")
        return proficient
    }

    private fun makeSkillRow(ctx: android.content.Context, proficient: Boolean, text: String): TextView {
        val bullet = if (proficient) "●" else "○"
        val textColor = if (proficient) 0xFF000000.toInt() else 0xFF999999.toInt()
        return TextView(ctx).apply {
            this.text = "$bullet $text"
            this.setTextColor(textColor)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 1.dp(ctx), 0, 1.dp(ctx))
        }
    }

    private fun makeInfoDropdown(ctx: android.content.Context, label: String, value: String): TextInputLayout {
        return TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedDenseStyle).apply {
            this.hint = label
            setPadding(0, 0, 0, 4.dp(ctx))
            val dropdown = MaterialAutoCompleteTextView(ctx).apply {
                inputType = android.text.InputType.TYPE_NULL
                threshold = 0
                isFocusableInTouchMode = false
                setText(value, false)
                textSize = 14f
            }
            addView(dropdown)
        }
    }

    private fun makeInfoLabel(ctx: android.content.Context, label: String, value: String?): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
            addView(TextView(ctx).apply {
                val span = SpannableString("$label: ")
                span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, 0)
                text = span
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
            addView(TextView(ctx).apply {
                text = value ?: ""
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
        }
    }

    private fun makeInfoInput(ctx: android.content.Context, label: String, value: String): TextInputLayout {
        return TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedDenseStyle).apply {
            this.hint = label
            setPadding(0, 0, 0, 4.dp(ctx))
            val input = TextInputEditText(ctx).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(value)
                textSize = 14f
            }
            addView(input)
        }
    }

    private fun infoLabel(container: LinearLayout, label: String, value: String) {
        val ctx = container.context
        container.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
            addView(TextView(ctx).apply {
                val span = SpannableString("$label: ")
                span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, 0)
                text = span
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
            addView(TextView(ctx).apply {
                text = value
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
        })
    }

    private fun infoLabelWrapped(container: LinearLayout, label: String, value: String) {
        val ctx = container.context
        container.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
            addView(TextView(ctx).apply {
                val span = SpannableString(label)
                span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, 0)
                text = span
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
            addView(TextView(ctx).apply {
                text = value
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            })
        })
    }

    private fun formatBonus(value: Int) = if (value >= 0) "+$value" else "$value"

    private fun computeShieldBonus(composite: String): String {
        val itemId = composite.substringBefore("|")
        val variantItemId = composite.substringAfter("|", "").takeIf { it.isNotBlank() }
        val item = vm.getItem(itemId) ?: return "—"
        val variant = variantItemId?.let { vm.getItem(it) }
        val baseAc = item.armor_class?.base ?: variant?.armor_class?.base ?: 0
        val magicBonus = item.armor_class_bonus ?: 0
        val total = baseAc + magicBonus
        return "+$total"
    }

    /**
     * Computes the character's total Armor Class from equipped armor and magic items.
     * Without armor: 10 + dex modifier + magic bonuses.
     * With armor: armor base + magic bonus + dex modifier (capped by max_dexterity_bonus) + other magic bonuses.
     */
    private fun computeCharacterAC(char: CharacterData, dexMod: Int): Int {
        // Base AC without armor: 10 + dex modifier
        var ac = 10 + dexMod

        // Check for equipped magical armor with variants
        val equippedArmor = char.equippedArmor
        if (equippedArmor != null) {
            val itemId = equippedArmor.substringBefore("|")
            val variantItemId = equippedArmor.substringAfter("|", "").takeIf { it.isNotBlank() }

            val item = vm.getItem(itemId)
            val variantItem = variantItemId?.let { vm.getItem(it) }

            if (item != null) {
                // Get base AC: try item.armor_class first, then variantItem.armor_class
                val acInfo = item.armor_class ?: variantItem?.armor_class
                if (acInfo != null) {
                    var base = acInfo.base
                    // Add magic armor_class_bonus on top of base
                    val magicAcBonus = item.armor_class_bonus ?: 0
                    base += magicAcBonus

                    // Apply Dex modifier (respects max_dexterity_bonus cap from JSON, falls back to max_dex)
                    if (acInfo.dex_bonus) {
                        val maxDex = acInfo.max_dexterity_bonus ?: acInfo.max_dex ?: Integer.MAX_VALUE
                        val applicableDex = minOf(dexMod, maxDex)
                        base += applicableDex
                    }
                    // If dex_bonus is false, just use base as-is

                    ac = base
                }
            }
        }

        // Add armor_class_bonus from equipped magic items (non-armor items like rings, cloaks)
        val equippedMagicItems = char.equippedMagicItems
        for (composite in equippedMagicItems) {
            val itemId = composite.substringBefore("|")
            val variantItemId = composite.substringAfter("|", "").takeIf { it.isNotBlank() }
            val item = vm.getItem(itemId) ?: continue

            // Only add bonus from non-armor magic items (armor already counted above)
            if (item.category != "armor") {
                val magicBonus = item.armor_class_bonus ?: 0
                if (magicBonus > 0) {
                    ac += magicBonus
                }
            }
        }

        return ac
    }

    private fun resolveColor(attr: Int): Int {
        val ta = requireContext().theme?.obtainStyledAttributes(intArrayOf(attr))
        val color = ta?.getColor(0, 0) ?: 0
        ta?.recycle()
        return color
    }
}
