package com.herocraft24.feature.characters

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.herocraft24.feature.characters.databinding.FragmentCharacterSheetBinding
import kotlinx.coroutines.launch

class CharacterSheetFragment : Fragment() {

    private var _binding: FragmentCharacterSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by viewModels({ requireActivity() })
    private var char: CharacterData? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCharacterSheetBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val id = arguments?.getString("characterId")
        if (id == null) {
            showErrorAndExit()
            return
        }
        val loaded = vm.getCharacter(id)
        if (loaded == null) {
            showErrorAndExit()
            return
        }
        char = loaded
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.title = loaded.name
        render()
    }

    private fun showErrorAndExit() {
        binding.toolbar.title = "Error"
        binding.content.removeAllViews()
        binding.content.addView(TextView(requireContext()).apply {
            text = "Character not found."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setPadding(32, 64, 32, 32)
        })
    }

    private fun render() {
        val currentChar = char ?: return
        val c = binding.content; c.removeAllViews()

        section(c, "Quick Stats") {
            row("HP", "${currentChar.hitPoints.current}/${currentChar.hitPoints.max}" + if (currentChar.hitPoints.temporary > 0) " (+${currentChar.hitPoints.temporary} temp)" else "")
            row("AC", "${currentChar.armorClass}")
            row("Initiative", "${currentChar.initiative}")
            row("Speed", "${currentChar.speed} ft")
            row("Prof. Bonus", "+${currentChar.proficiencyBonus}")
            row("Level", "${currentChar.level}")
            row("XP", "${currentChar.experience}")
        }

        section(c, "Ability Scores") {
            val order = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
            order.forEach { ab ->
                val score = currentChar.abilityScores[ab] ?: 10
                row(ab.replaceFirstChar { it.uppercase() }.take(3), "$score (${vm.modifier(score).let { if (it >= 0) "+$it" else "$it" }})")
            }
        }

        if (currentChar.skills.isNotEmpty()) {
            section(c, "Skills") {
                currentChar.skills.forEach { sk ->
                    row(sk.skill.replaceFirstChar { it.uppercase() }, "${vm.skillBonus(sk, currentChar).let { if (it >= 0) "+$it" else "$it" }}${if (sk.proficient) " *" else ""}${if (sk.expertise) " **" else ""}")
                }
            }
        }

        section(c, "Saving Throws") {
            listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma").forEach { ab ->
                row(ab.replaceFirstChar { it.uppercase() }.take(3), "${vm.saveBonus(ab, currentChar).let { if (it >= 0) "+$it" else "$it" }}${if (ab in currentChar.savingThrows) " *" else ""}")
            }
        }

        if (currentChar.conditions.isNotEmpty()) section(c, "Conditions") { text(currentChar.conditions.joinToString(", ")) }
        if (currentChar.exhaustion > 0) section(c, "Exhaustion") { row("Level", "${currentChar.exhaustion}") }

        section(c, "Death Saves") {
            row("Successes", "${currentChar.deathSaves.successes}/3")
            row("Failures", "${currentChar.deathSaves.failures}/3")
        }

        section(c, "Hit Dice") { row(currentChar.hitDice.total, "${currentChar.hitDice.remaining} remaining") }

        section(c, "Currency") {
            row("GP", "${currentChar.currency.gp}"); row("SP", "${currentChar.currency.sp}")
            row("CP", "${currentChar.currency.cp}"); row("PP", "${currentChar.currency.pp}")
        }

        val sp = currentChar.spells
        if (sp != null) {
            section(c, "Spells") {
                if (sp.cantrips.isNotEmpty()) text("Cantrips: ${sp.cantrips.mapNotNull { vm.resolveName(it) }.joinToString(", ")}")
                if (sp.prepared.isNotEmpty()) text("Prepared: ${sp.prepared.mapNotNull { vm.resolveName(it) }.joinToString(", ")}")
            }
            if (currentChar.spellSlots.isNotEmpty()) {
                section(c, "Spell Slots") {
                    currentChar.spellSlots.forEach { (lvl, slot) -> row("Level $lvl", "${slot.used}/${slot.total}") }
                }
            }
        }

        val restRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        restRow.addView(Button(requireContext()).apply {
            text = "Short Rest"; setOnClickListener { shortRest() }
        })
        restRow.addView(Button(requireContext()).apply {
            text = "Long Rest"; setOnClickListener { longRest() }
        })
        c.addView(restRow)
    }

    private fun shortRest() {
        val currentChar = char ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Short Rest")
            .setMessage("Use Hit Dice to recover HP?")
            .setPositiveButton("Yes") { _, _ -> showHitDiceDialog() }
            .setNegativeButton("No") { _, _ ->
                vm.saveCharacter(currentChar); refreshChar()
            }
            .show()
    }

    private fun showHitDiceDialog() {
        val currentChar = char ?: return
        val diceCount = currentChar.hitDice.remaining
        val diceType = currentChar.hitDice.total.substringAfter("d").toIntOrNull() ?: 6
        val conMod = vm.modifier(currentChar.abilityScores["constitution"] ?: 10)
        val items = (1..diceCount).map { "Use $it hit dice" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Use Hit Dice ($diceCount available)")
            .setItems(items) { _, w ->
                val used = w + 1
                var healed = 0
                repeat(used) { healed += (1..diceType).random() + conMod }
                val newHP = minOf(currentChar.hitPoints.max, currentChar.hitPoints.current + healed)
                char = currentChar.copy(
                    hitPoints = currentChar.hitPoints.copy(current = newHP),
                    hitDice = currentChar.hitDice.copy(remaining = diceCount - used)
                )
                vm.saveCharacter(char!!); refreshChar()
            }
            .show()
    }

    private fun longRest() {
        val currentChar = char ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Long Rest")
            .setMessage("Recover all HP, half Hit Dice, all spell slots?")
            .setPositiveButton("Yes") { _, _ ->
                val totalDice = currentChar.hitDice.total.substringBefore("d").toIntOrNull() ?: 1
                val recovered = maxOf(1, totalDice / 2)
                char = currentChar.copy(
                    hitPoints = currentChar.hitPoints.copy(current = currentChar.hitPoints.max, temporary = 0),
                    hitDice = currentChar.hitDice.copy(remaining = recovered),
                    spellSlots = currentChar.spellSlots.mapValues { it.value.copy(used = 0) },
                    deathSaves = DeathSaves(),
                    exhaustion = maxOf(0, currentChar.exhaustion - 1)
                )
                vm.saveCharacter(char!!); refreshChar()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun refreshChar() {
        val currentChar = char ?: return
        lifecycleScope.launch {
            vm.repo.loadAll()
            char = vm.getCharacter(currentChar.id) ?: return@launch
            render()
        }
    }

    private fun section(container: LinearLayout, title: String, block: LinearLayout.() -> Unit) {
        container.addView(TextView(requireContext()).apply {
            text = title; setTypeface(null, Typeface.BOLD); setPadding(0, 16, 0, 8)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 0, 0, 8) }
        ll.block(); container.addView(ll)
    }

    private fun LinearLayout.row(label: String, value: String) {
        addView(TextView(requireContext()).apply {
            val t = "$label: $value"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 2, 0, 2)
            text = SpannableString(t).apply { setSpan(StyleSpan(Typeface.BOLD), 0, label.length + 1, 0) }
        })
    }

    private fun LinearLayout.text(value: String) {
        addView(TextView(requireContext()).apply {
            text = value; setPadding(0, 2, 0, 2)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}