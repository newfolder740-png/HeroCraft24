package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.herocraft24.feature.characters.databinding.FragmentCharacterCreateBinding
import kotlinx.coroutines.launch

class CharacterCreateFragment : Fragment() {

    private var _binding: FragmentCharacterCreateBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by viewModels({ requireActivity() })
    private var step = 0
    private var nameInput: EditText? = null
    private var alignSpinner: Spinner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step", 0) ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
        val name = nameInput?.text?.toString()?.trim() ?: ""
        val alignment = alignSpinner?.selectedItem?.toString() ?: ""
        outState.putString("name", name)
        outState.putString("alignment", alignment)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCharacterCreateBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnPrev.setOnClickListener { prevStep() }
        savedInstanceState?.getString("name")?.let { n -> vm.updateWizard { it.copy(name = n) } }
        savedInstanceState?.getString("alignment")?.let { a -> vm.updateWizard { it.copy(alignment = a) } }
        renderStep()
    }

    private fun nextStep() {
        if (step >= 6) {
            val name = nameInput?.text?.toString()?.trim() ?: ""
            val alignment = alignSpinner?.selectedItem?.toString() ?: ""
            vm.updateWizard { it.copy(name = name, alignment = alignment) }
            lifecycleScope.launch {
                vm.finishWizardSuspend()
                findNavController().navigateUp()
            }
            return
        }
        saveCurrentStepInput()
        step++; renderStep()
    }

    private fun saveCurrentStepInput() {
        if (step == 6) {
            val name = nameInput?.text?.toString()?.trim() ?: ""
            val alignment = alignSpinner?.selectedItem?.toString() ?: ""
            vm.updateWizard { it.copy(name = name, alignment = alignment) }
        }
    }

    private fun prevStep() {
        saveCurrentStepInput()
        if (step > 0) { step--; renderStep() }
    }

    private fun refreshAbilities() { if (step == 3) renderStep() }

    private fun renderStep() {
        binding.progress.max = 6
        binding.progress.progress = step.coerceIn(0, 6)
        binding.btnPrev.visibility = if (step == 0) View.GONE else View.VISIBLE
        binding.btnNext.text = if (step >= 6) "Создать" else "Далее"
        binding.btnNext.setOnClickListener { nextStep() }
        val container = binding.stepContainer
        container.removeAllViews()

        when (step) {
            0 -> renderSpeciesStep(container)
            1 -> renderBackgroundStep(container)
            2 -> renderClassStep(container)
            3 -> renderAbilitiesStep(container)
            4 -> renderEquipmentStep(container)
            5 -> renderSpellsStep(container)
            6 -> renderDetailsStep(container)
        }
    }

    private fun renderSpeciesStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        vm.getSpeciesIds().forEach { id ->
            val name = vm.resolveName(id) ?: id
            ll.addView(Button(requireContext()).apply {
                text = name; setOnClickListener { vm.updateWizard { it.copy(speciesId = id) }; renderStep() }
            })
        }
        container.addView(ScrollView(requireContext()).apply { addView(ll) })
    }

    private fun renderBackgroundStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        vm.getBackgroundIds().forEach { id ->
            val name = vm.resolveName(id) ?: id
            ll.addView(Button(requireContext()).apply {
                text = name; setOnClickListener { vm.updateWizard { it.copy(backgroundId = id) }; renderStep() }
            })
        }
        container.addView(ScrollView(requireContext()).apply { addView(ll) })
    }

    private fun renderClassStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        vm.getClassIds().forEach { id ->
            val cls = vm.getClassInfo(id)
            val name = cls?.name?.get() ?: id
            ll.addView(Button(requireContext()).apply {
                text = name; setOnClickListener { vm.updateWizard { it.copy(classId = id) }; renderStep() }
            })
        }
        container.addView(ScrollView(requireContext()).apply { addView(ll) })
    }

    private fun renderAbilitiesStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val abilities = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
        val char = vm.wizard.value
        abilities.forEach { ab ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val label = TextView(requireContext()).apply {
                text = ab.replaceFirstChar { it.uppercase() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)
            val score = char.abilityScores[ab] ?: 10
            val scoreView = TextView(requireContext()).apply {
                text = "$score (${vm.modifier(score).let { if (it >= 0) "+$it" else "$it" }})"
                setPadding(16, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(scoreView)
            row.addView(Button(requireContext()).apply {
                text = "+"; layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    vm.updateWizard { c -> c.copy(abilityScores = c.abilityScores.toMutableMap().apply { this[ab] = minOf(20, (this[ab] ?: 10) + 1) }) }
                    renderStep()
                }
            })
            row.addView(Button(requireContext()).apply {
                text = "-"; layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    vm.updateWizard { c -> c.copy(abilityScores = c.abilityScores.toMutableMap().apply { this[ab] = maxOf(3, (this[ab] ?: 10) - 1) }) }
                    renderStep()
                }
            })
            ll.addView(row)
        }
        container.addView(ScrollView(requireContext()).apply { addView(ll) })
    }

    private fun renderEquipmentStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(TextView(requireContext()).apply { text = "Equipment will be added after creation" })
        container.addView(ll)
    }

    private fun renderSpellsStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(TextView(requireContext()).apply { text = "Spells will be added after creation" })
        container.addView(ll)
    }

    private fun renderDetailsStep(container: FrameLayout) {
        val char = vm.wizard.value
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 16) }
        ll.addView(TextView(requireContext()).apply { text = "Name:"; setPadding(0, 8, 0, 4) })
        val ni = EditText(requireContext()).apply { setText(char.name); hint = "Character name" }
        nameInput = ni
        ll.addView(ni)
        ll.addView(TextView(requireContext()).apply { text = "Alignment:"; setPadding(0, 16, 0, 4) })
        val alignments = listOf("Lawful Good", "Neutral Good", "Chaotic Good", "Lawful Neutral", "True Neutral", "Chaotic Neutral", "Lawful Evil", "Neutral Evil", "Chaotic Evil")
        val as_ = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, alignments)
            val idx = alignments.indexOf(char.alignment)
            if (idx >= 0) setSelection(idx)
        }
        alignSpinner = as_
        ll.addView(as_)
        ll.addView(TextView(requireContext()).apply {
            text = "${vm.resolveName(char.speciesId) ?: "?"} | ${vm.resolveName(char.classId) ?: "?"} | ${vm.resolveName(char.backgroundId) ?: "?"}"
            setPadding(0, 16, 0, 0)
        })

        container.addView(ScrollView(requireContext()).apply { addView(ll) })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}