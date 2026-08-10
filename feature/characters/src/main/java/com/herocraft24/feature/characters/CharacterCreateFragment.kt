package com.herocraft24.feature.characters

import android.content.ClipData
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.herocraft24.feature.characters.databinding.FragmentCharacterCreateBinding
import com.herocraft24.feature.characters.databinding.FragmentCreateAbilitiesBinding
import com.herocraft24.feature.characters.R
import kotlinx.coroutines.launch

class CharacterCreateFragment : Fragment() {

    private var _binding: FragmentCharacterCreateBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by viewModels({ requireActivity() })

    // Steps: 0=Abilities, 1=Species, 2=Background, 3=Class, 4=Features, 5=Feats
    private var step = 0
    private var nameInput: EditText? = null
    private var alignSpinner: Spinner? = null

    // Abilities step binding
    private var _abilitiesBinding: FragmentCreateAbilitiesBinding? = null
    private val abilitiesBinding get() = _abilitiesBinding!!

    // Ability scores state for 4d6-less mode
    // Pool values: the 6 rolled values that haven't been assigned to slots yet
    private var poolValues: MutableList<Int?> = mutableListOf(null, null, null, null, null, null)
    // Slot assignments: which pool index is assigned to each ability slot (null if empty)
    private var slotAssignments: MutableMap<String, Int?> = mutableMapOf(
        "strength" to null, "dexterity" to null, "constitution" to null,
        "intelligence" to null, "wisdom" to null, "charisma" to null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step", 0) ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCharacterCreateBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnPrev.setOnClickListener { prevStep() }
        renderStep()
    }

    private fun nextStep() {
        if (step >= 5) {
            // Final step - create character
            saveCurrentStepInput()
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
        when (step) {
            0 -> saveAbilitiesInput()
        }
    }

    private fun prevStep() {
        saveCurrentStepInput()
        if (step > 0) { step--; renderStep() }
    }

    private fun updateNextButtonState() {
        val canProceed = when (step) {
            0 -> {
                // Abilities step: all 6 slots must be filled
                slotAssignments.values.all { it != null }
            }
            1 -> vm.wizard.value.speciesId.isNotEmpty()
            2 -> vm.wizard.value.backgroundId.isNotEmpty()
            3 -> vm.wizard.value.classId.isNotEmpty()
            else -> true
        }
        binding.btnNext.isEnabled = canProceed
    }

    private fun renderStep() {
        binding.btnPrev.visibility = if (step == 0) View.GONE else View.VISIBLE
        binding.btnNext.text = if (step >= 5) "Создать" else "Далее"
        binding.btnNext.setOnClickListener { nextStep() }
        updateNextButtonState()
        val container = binding.stepContainer
        container.removeAllViews()
        _abilitiesBinding = null

        when (step) {
            0 -> renderAbilitiesStep(container)
            1 -> renderSpeciesStep(container)
            2 -> renderBackgroundStep(container)
            3 -> renderClassStep(container)
            4 -> renderFeaturesStep(container)
            5 -> renderFeatsStep(container)
        }
    }

    private fun renderAbilitiesStep(container: FrameLayout) {
        _abilitiesBinding = FragmentCreateAbilitiesBinding.inflate(layoutInflater, container, true)
        val ab = abilitiesBinding
        val char = vm.wizard.value

        // Set name
        ab.nameInput.setText(char.name)

        // Set mode toggle
        val mode = char.abilityScoreMode
        when (mode) {
            "custom" -> ab.modeToggle.check(ab.btnModeCustom.id)
            "4d6less" -> ab.modeToggle.check(ab.btnMode4d6.id)
            "buy" -> ab.modeToggle.check(ab.btnModeBuy.id)
        }

        // Initialize pool values based on mode
        if (poolValues.all { it == null }) {
            when (mode) {
                "4d6less" -> poolValues = (1..6).map { roll4d6DropLowest() }.toMutableList()
                else -> poolValues = MutableList(6) { 8 } // custom and buy start at 8
            }
            // Reset slot assignments
            slotAssignments = mutableMapOf(
                "strength" to null, "dexterity" to null, "constitution" to null,
                "intelligence" to null, "wisdom" to null, "charisma" to null
            )
        }

        // Update UI based on mode
        updateAbilitiesUI(mode)

        // Mode toggle listener
        ab.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                ab.btnModeCustom.id -> "custom"
                ab.btnMode4d6.id -> "4d6less"
                ab.btnModeBuy.id -> "buy"
                else -> return@addOnButtonCheckedListener
            }
            vm.updateWizard { it.copy(abilityScoreMode = newMode) }
            // Reset pool when switching modes
            poolValues = when (newMode) {
                "4d6less" -> (1..6).map { roll4d6DropLowest() }.toMutableList()
                else -> MutableList(6) { 8 }
            }
            slotAssignments = mutableMapOf(
                "strength" to null, "dexterity" to null, "constitution" to null,
                "intelligence" to null, "wisdom" to null, "charisma" to null
            )
            syncAbilityScoresToViewModel()
            updateAbilitiesUI(newMode)
            setupDragAndDrop(ab)
        }

        // Setup +/- buttons for custom mode
        setupAbilityButtons(ab)

        // Setup reroll button for 4d6 mode
        ab.btnReroll.setOnClickListener {
            poolValues = (1..6).map { roll4d6DropLowest() }.toMutableList()
            // Clear all slot assignments
            slotAssignments = mutableMapOf(
                "strength" to null, "dexterity" to null, "constitution" to null,
                "intelligence" to null, "wisdom" to null, "charisma" to null
            )
            syncAbilityScoresToViewModel()
            updateAbilitiesUI("4d6less")
            setupDragAndDrop(ab)
        }

        // Setup reset button for custom/buy modes
        ab.btnReset.setOnClickListener {
            val mode = vm.wizard.value.abilityScoreMode
            poolValues = MutableList(6) { 8 }
            // Clear all slot assignments
            slotAssignments = mutableMapOf(
                "strength" to null, "dexterity" to null, "constitution" to null,
                "intelligence" to null, "wisdom" to null, "charisma" to null
            )
            syncAbilityScoresToViewModel()
            updateAbilitiesUI(mode)
            setupDragAndDrop(ab)
        }

        // Setup drag-and-drop for all modes
        setupDragAndDrop(ab)
    }

    private fun updateAbilitiesUI(mode: String) {
        val ab = abilitiesBinding

        // Show/hide points counter (Buy mode)
        ab.pointsCounter.visibility = if (mode == "buy") View.VISIBLE else View.GONE
        if (mode == "buy") {
            // Count ALL pool values, not just assigned ones
            val pointsUsed = calculatePointBuyCost(poolValues.map { it ?: 8 })
            ab.pointsCounter.text = "$pointsUsed/27"
        }

        // Show/hide reroll button (4d6 mode)
        ab.btnReroll.visibility = if (mode == "4d6less") View.VISIBLE else View.GONE

        // Show/hide reset button (custom/buy modes)
        ab.btnReset.visibility = if (mode == "custom" || mode == "buy") View.VISIBLE else View.GONE

        // In ALL modes: top slots show only assigned values (empty if not assigned)
        updateSlotDisplayFromAssignments(ab)

        // Bottom controls always show pool values
        updatePoolDisplay(ab)

        // Enable/disable +/- buttons based on mode
        setControlsButtonsEnabled(ab, mode == "custom" || mode == "buy")
    }

    // All modes: slots show only assigned values (empty if not assigned)
    private fun updateSlotDisplayFromAssignments(ab: FragmentCreateAbilitiesBinding) {
        val slotViews = mapOf(
            "strength" to ab.slotStrength,
            "dexterity" to ab.slotDexterity,
            "constitution" to ab.slotConstitution,
            "intelligence" to ab.slotIntelligence,
            "wisdom" to ab.slotWisdom,
            "charisma" to ab.slotCharisma
        )
        for ((ability, view) in slotViews) {
            val isFilled = slotAssignments[ability] != null
            view.setBackgroundResource(
                if (isFilled) R.drawable.bg_ability_slot_filled else R.drawable.bg_ability_slot
            )
        }
        ab.slotStrengthValue.text = getSlotValue("strength")
        ab.slotDexterityValue.text = getSlotValue("dexterity")
        ab.slotConstitutionValue.text = getSlotValue("constitution")
        ab.slotIntelligenceValue.text = getSlotValue("intelligence")
        ab.slotWisdomValue.text = getSlotValue("wisdom")
        ab.slotCharismaValue.text = getSlotValue("charisma")
    }

    private fun getSlotValue(ability: String): String {
        val poolIndex = slotAssignments[ability]
        return if (poolIndex != null) poolValues[poolIndex]?.toString() ?: "—" else "—"
    }

    // All modes: bottom controls show pool values (assigned ones show "—")
    private fun updatePoolDisplay(ab: FragmentCreateAbilitiesBinding) {
        val assignedIndices = slotAssignments.values.filterNotNull().toSet()
        val controls = listOf(
            ab.valueStrength, ab.valueDexterity, ab.valueConstitution,
            ab.valueIntelligence, ab.valueWisdom, ab.valueCharisma
        )
        controls.forEachIndexed { index, textView ->
            if (index in assignedIndices) {
                textView.text = "—"
            } else {
                textView.text = poolValues[index]?.toString() ?: "—"
            }
        }
    }

    private fun setControlsButtonsEnabled(ab: FragmentCreateAbilitiesBinding, enabled: Boolean) {
        val assignedIndices = slotAssignments.values.filterNotNull().toSet()
        val buttonPairs = listOf(
            0 to listOf(ab.btnStrengthPlus, ab.btnStrengthMinus),
            1 to listOf(ab.btnDexterityPlus, ab.btnDexterityMinus),
            2 to listOf(ab.btnConstitutionPlus, ab.btnConstitutionMinus),
            3 to listOf(ab.btnIntelligencePlus, ab.btnIntelligenceMinus),
            4 to listOf(ab.btnWisdomPlus, ab.btnWisdomMinus),
            5 to listOf(ab.btnCharismaPlus, ab.btnCharismaMinus)
        )
        for ((poolIndex, buttons) in buttonPairs) {
            val isAssigned = poolIndex in assignedIndices
            buttons.forEach { it.isEnabled = enabled && !isAssigned }
        }
    }

    private fun setupAbilityButtons(ab: FragmentCreateAbilitiesBinding) {
        // Strength (pool index 0)
        ab.btnStrengthPlus.setOnClickListener { adjustPoolValue(0, 1) }
        ab.btnStrengthMinus.setOnClickListener { adjustPoolValue(0, -1) }

        // Dexterity (pool index 1)
        ab.btnDexterityPlus.setOnClickListener { adjustPoolValue(1, 1) }
        ab.btnDexterityMinus.setOnClickListener { adjustPoolValue(1, -1) }

        // Constitution (pool index 2)
        ab.btnConstitutionPlus.setOnClickListener { adjustPoolValue(2, 1) }
        ab.btnConstitutionMinus.setOnClickListener { adjustPoolValue(2, -1) }

        // Intelligence (pool index 3)
        ab.btnIntelligencePlus.setOnClickListener { adjustPoolValue(3, 1) }
        ab.btnIntelligenceMinus.setOnClickListener { adjustPoolValue(3, -1) }

        // Wisdom (pool index 4)
        ab.btnWisdomPlus.setOnClickListener { adjustPoolValue(4, 1) }
        ab.btnWisdomMinus.setOnClickListener { adjustPoolValue(4, -1) }

        // Charisma (pool index 5)
        ab.btnCharismaPlus.setOnClickListener { adjustPoolValue(5, 1) }
        ab.btnCharismaMinus.setOnClickListener { adjustPoolValue(5, -1) }
    }

    private fun adjustPoolValue(poolIndex: Int, delta: Int) {
        val mode = vm.wizard.value.abilityScoreMode
        val current = poolValues[poolIndex] ?: 8
        val newValue = current + delta

        // Apply limits based on mode
        val (minValue, maxValue) = when (mode) {
            "buy" -> 8 to 15
            else -> 3 to 20
        }

        if (newValue < minValue || newValue > maxValue) return

        // Check point-buy limit
        if (mode == "buy") {
            val testPool = poolValues.toMutableList()
            testPool[poolIndex] = newValue
            if (calculatePointBuyCost(testPool.map { it ?: 8 }) > 27) return
        }

        poolValues[poolIndex] = newValue
        syncAbilityScoresToViewModel()
        updateAbilitiesUI(mode)
    }

    private fun calculatePointBuyCost(values: List<Int>): Int {
        // Point-buy costs: 8=0, 9=1, 10=2, 11=3, 12=4, 13=5, 14=7, 15=9
        val costs = mapOf(8 to 0, 9 to 1, 10 to 2, 11 to 3, 12 to 4, 13 to 5, 14 to 7, 15 to 9)
        return values.sumOf { costs[it] ?: 0 }
    }

    private fun roll4d6DropLowest(): Int {
        val rolls = (1..4).map { (1..6).random() }
        return rolls.sortedDescending().take(3).sum()
    }

    private fun setupDragAndDrop(ab: FragmentCreateAbilitiesBinding) {
        val abilities = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
        val slotViews = mapOf(
            "strength" to ab.slotStrength,
            "dexterity" to ab.slotDexterity,
            "constitution" to ab.slotConstitution,
            "intelligence" to ab.slotIntelligence,
            "wisdom" to ab.slotWisdom,
            "charisma" to ab.slotCharisma
        )
        val poolControls = listOf(
            ab.controlStrength, ab.controlDexterity, ab.controlConstitution,
            ab.controlIntelligence, ab.controlWisdom, ab.controlCharisma
        )

        // --- Drop targets: top slots ---
        slotViews.forEach { (targetAbility, slotView) ->
            slotView.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { view.alpha = 0.5f; true }
                    DragEvent.ACTION_DRAG_EXITED -> { view.alpha = 1.0f; true }
                    DragEvent.ACTION_DROP -> {
                        view.alpha = 1.0f
                        val localState = event.localState as? DragPayload ?: return@setOnDragListener false
                        handleDropToSlot(localState, targetAbility, ab)
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> { view.alpha = 1.0f; true }
                    else -> false
                }
            }
        }

        // --- Drop targets: bottom pool controls (return value to pool) ---
        poolControls.forEachIndexed { poolIndex, controlView ->
            controlView.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { view.alpha = 0.5f; true }
                    DragEvent.ACTION_DRAG_EXITED -> { view.alpha = 1.0f; true }
                    DragEvent.ACTION_DROP -> {
                        view.alpha = 1.0f
                        val localState = event.localState as? DragPayload ?: return@setOnDragListener false
                        handleDropToPool(localState, poolIndex, ab)
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> { view.alpha = 1.0f; true }
                    else -> false
                }
            }
        }

        // --- Drag sources: bottom pool controls (only unassigned values) ---
        val assignedIndices = slotAssignments.values.filterNotNull().toSet()
        poolControls.forEachIndexed { poolIndex, controlView ->
            controlView.setOnLongClickListener { v ->
                if (poolIndex in assignedIndices) return@setOnLongClickListener false // already assigned
                val value = poolValues[poolIndex] ?: return@setOnLongClickListener false
                val payload = DragPayload(sourcePoolIndex = poolIndex, sourceAbility = null, value = value)
                val data = ClipData.newPlainText("ability_value", value.toString())
                val shadow = View.DragShadowBuilder(v)
                v.startDragAndDrop(data, shadow, payload, 0)
                true
            }
        }

        // --- Drag sources: top slots ---
        slotViews.forEach { (ability, slotView) ->
            slotView.setOnLongClickListener { v ->
                val poolIndex = slotAssignments[ability] ?: return@setOnLongClickListener false
                val value = poolValues[poolIndex] ?: return@setOnLongClickListener false
                val payload = DragPayload(sourcePoolIndex = null, sourceAbility = ability, value = value)
                val data = ClipData.newPlainText("ability_value", value.toString())
                val shadow = View.DragShadowBuilder(v)
                v.startDragAndDrop(data, shadow, payload, 0)
                true
            }
        }
    }

    // Data class for drag payload
    private data class DragPayload(
        val sourcePoolIndex: Int?,   // not null if dragged from pool
        val sourceAbility: String?,   // not null if dragged from a slot
        val value: Int
    )

    // Handle drop onto a top slot
    private fun handleDropToSlot(payload: DragPayload, targetAbility: String, ab: FragmentCreateAbilitiesBinding) {
        val existingPoolIndex = slotAssignments[targetAbility] // what's currently in the target slot

        if (payload.sourceAbility != null) {
            // Dragging from one slot to another
            if (payload.sourceAbility == targetAbility) return // same slot, do nothing

            // Swap: source slot's pool index goes to target, target's pool index goes to source
            val sourcePoolIndex = slotAssignments[payload.sourceAbility]
            slotAssignments[payload.sourceAbility] = existingPoolIndex
            slotAssignments[targetAbility] = sourcePoolIndex
        } else if (payload.sourcePoolIndex != null) {
            // Dragging from pool to slot
            if (existingPoolIndex != null) {
                // Slot occupied: swap values in pool array, keep the slot's pool index
                val temp = poolValues[existingPoolIndex]
                poolValues[existingPoolIndex] = poolValues[payload.sourcePoolIndex]
                poolValues[payload.sourcePoolIndex] = temp
                // slotAssignments[targetAbility] stays as existingPoolIndex (unchanged)
            } else {
                // Slot empty: just assign the pool index
                slotAssignments[targetAbility] = payload.sourcePoolIndex
            }
        }

        syncAbilityScoresToViewModel()
        updateAbilitiesUI(vm.wizard.value.abilityScoreMode)
        setupDragAndDrop(ab)
    }

    // Handle drop onto a bottom pool control
    private fun handleDropToPool(payload: DragPayload, targetPoolIndex: Int, ab: FragmentCreateAbilitiesBinding) {
        if (payload.sourceAbility != null) {
            // Dragging from slot back to pool
            val sourcePoolIndex = slotAssignments[payload.sourceAbility] ?: return
            // Swap values in pool array
            val temp = poolValues[targetPoolIndex]
            poolValues[targetPoolIndex] = poolValues[sourcePoolIndex]
            poolValues[sourcePoolIndex] = temp
            // Update any slot that referenced targetPoolIndex → now points to sourcePoolIndex
            for ((ability, idx) in slotAssignments) {
                if (idx == targetPoolIndex) slotAssignments[ability] = sourcePoolIndex
            }
            // Clear the source slot
            slotAssignments[payload.sourceAbility] = null
        } else if (payload.sourcePoolIndex != null) {
            // Dragging within pool — reorder
            if (payload.sourcePoolIndex == targetPoolIndex) return
            val temp = poolValues[targetPoolIndex]
            poolValues[targetPoolIndex] = poolValues[payload.sourcePoolIndex]
            poolValues[payload.sourcePoolIndex] = temp
            // Update any slot assignments that referenced these pool indices
            for ((ability, idx) in slotAssignments) {
                if (idx == payload.sourcePoolIndex) slotAssignments[ability] = targetPoolIndex
                else if (idx == targetPoolIndex) slotAssignments[ability] = payload.sourcePoolIndex
            }
        }

        syncAbilityScoresToViewModel()
        updateAbilitiesUI(vm.wizard.value.abilityScoreMode)
        setupDragAndDrop(ab)
    }

    // Sync slot assignments to ViewModel's abilityScores
    private fun syncAbilityScoresToViewModel() {
        val scores = mutableMapOf<String, Int>()
        for ((ability, poolIndex) in slotAssignments) {
            scores[ability] = if (poolIndex != null) poolValues[poolIndex] ?: 8 else 8
        }
        vm.updateWizard { it.copy(abilityScores = scores) }
        updateNextButtonState()
    }

    private fun saveAbilitiesInput() {
        val ab = _abilitiesBinding ?: return
        val name = ab.nameInput.text?.toString()?.trim() ?: ""
        vm.updateWizard { it.copy(name = name) }
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

    private fun renderFeaturesStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(TextView(requireContext()).apply { text = "Умения класса (будет реализовано)" })
        container.addView(ScrollView(requireContext()).apply { addView(ll) })
    }

    private fun renderFeatsStep(container: FrameLayout) {
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        ll.addView(TextView(requireContext()).apply { text = "Черты (будет реализовано)" })
        container.addView(ScrollView(requireContext()).apply { addView(ll) })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _abilitiesBinding = null
    }
}
