package com.herocraft24.feature.characters

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.radiobutton.MaterialRadioButton
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Feature
import com.herocraft24.core.model.GameClass
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.ExpandableCard
import com.herocraft24.core.ui.util.dp
import com.herocraft24.core.ui.util.resolveColor
import com.herocraft24.feature.characters.databinding.CardClassCreateBinding
import com.herocraft24.feature.characters.databinding.FragmentLevelUpBinding

class LevelUpFragment : Fragment() {

    private var _binding: FragmentLevelUpBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()
    private var charId: String? = null
    private var step = 0

    // Step 0 state
    private var selectedClassId: String? = null
    private var isNewClass = false

    // Step 1 state
    private var featuresAdapter: FeaturesCreateAdapter? = null
    private val featureChoices = mutableMapOf<String, String>()
    private val featureMultiChoices = mutableMapOf<String, List<String>>()
    private val asiChoices = mutableMapOf<String, AsiChoice>()

    // Character state
    private var char: CharacterData? = null

    // Step 2 (spells) state
    private var removeCantripId: String? = null
    private var removeSpellId: String? = null
    private val newSelectedCantrips = mutableListOf<String>()
    private val newSelectedSpells = mutableListOf<String>()
    private var currentSorcererSpells: List<SpellSummary> = emptyList()
    private var availableNewSpells: List<SpellSummary> = emptyList()
    private var maxNewSpellLevel: Int = 1
    private var baseNewCantrips: Int = 0
    private var baseNewSpells: Int = 0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentLevelUpBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        charId = arguments?.getString("characterId")
        if (charId == null) {
            findNavController().navigateUp()
            return
        }

        char = vm.getCharacter(charId!!)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnBack.setOnClickListener { prevStep() }
        binding.btnNext.setOnClickListener { nextStep() }

        renderStep()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderStep() {
        binding.stepContainer.removeAllViews()
        when (step) {
            0 -> renderClassSelectionStep()
            1 -> renderFeaturesStep()
            2 -> renderSpellsStep()
        }
        updateButtons()
    }

    private fun updateButtons() {
        val selectedClass = selectedClassId
        val cls = selectedClass?.let { vm.getClassInfo(it) }
        val isSpellcaster = cls?.spellcasting != null
        val isLastStep = if (isSpellcaster) step == 2 else step == 1

        binding.btnBack.visibility = if (step > 0) View.VISIBLE else View.GONE

        if (isLastStep) {
            binding.btnNext.text = "Level-Up"
            binding.btnNext.isEnabled = when (step) {
                1 -> featuresAdapter?.areAllChoicesMade() ?: true
                2 -> areSpellSelectionsComplete()
                else -> true
            }
        } else {
            binding.btnNext.text = "Далее"
            binding.btnNext.isEnabled = when (step) {
                0 -> selectedClass != null
                1 -> featuresAdapter?.areAllChoicesMade() ?: true
                2 -> areSpellSelectionsComplete()
                else -> true
            }
        }
    }

    private fun nextStep() {
        val selectedClass = selectedClassId ?: return
        val cls = vm.getClassInfo(selectedClass)
        val isSpellcaster = cls?.spellcasting != null

        if (step == 0) {
            step = 1
        } else if (step == 1) {
            if (isSpellcaster) {
                step = 2
            } else {
                performLevelUp()
                return
            }
        } else if (step == 2) {
            performLevelUp()
            return
        }
        renderStep()
    }

    private fun prevStep() {
        if (step > 0) {
            step--
            renderStep()
        }
    }

    // ── Step 0: Class Selection ──

    private fun renderClassSelectionStep() {
        val ch = char ?: return
        val ctx = requireContext()
        val container = binding.stepContainer

        val title = TextView(ctx).apply {
            text = "Выберите класс"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            gravity = Gravity.CENTER
        }
        container.addView(title)

        val recyclerView = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(ctx)
            clipToPadding = false
            setPadding(0, 8.dp(ctx), 0, 0)
        }
        container.addView(recyclerView)

        // Build list of (fullId, GameClass) pairs — fullId is required for getClassInfo
        val existingFullIds = (ch.classLevels.keys + ch.classId).distinct()
        val classPairs = existingFullIds.mapNotNull { fullId ->
            vm.getClassInfo(fullId)?.let { fullId to it }
        }

        val adapter = LevelUpClassAdapter(
            classPairs = classPairs,
            onClassSelected = { fullId ->
                selectedClassId = fullId
                isNewClass = false
                updateButtons()
            },
            onNewClassSelected = {
                selectedClassId = null
                isNewClass = true
                updateButtons()
            },
            initialSelectedId = selectedClassId
        )
        recyclerView.adapter = adapter
    }

    // ── Step 1: Features ──

    private fun renderFeaturesStep() {
        val ch = char ?: return
        val selectedClass = selectedClassId ?: return
        val ctx = requireContext()
        val container = binding.stepContainer

        val cls = vm.getClassInfo(selectedClass) ?: return
        val currentClassLevel = ch.classLevels[selectedClass] ?: if (selectedClass == ch.classId) ch.level else 0
        val nextClassLevel = currentClassLevel + 1
        val nextTotalLevel = ch.level + 1

        val title = TextView(ctx).apply {
            text = "Умения класса и вида"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            gravity = Gravity.CENTER
        }
        container.addView(title)

        val recyclerView = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(ctx)
            clipToPadding = false
            setPadding(0, 8.dp(ctx), 0, 0)
        }
        container.addView(recyclerView)

        // Collect features gained at this level
        val features = mutableListOf<Feature>()

        // Class features at nextClassLevel
        cls.features
            .filter { featureId ->
                val localId = featureId.substringAfterLast(":")
                val levelMatch = Regex("_l(\\d+)_").find(localId)
                val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                featureLevel == nextClassLevel
            }
            .mapNotNull { vm.repository.getFeature(it) }
            .filter { !it.is_placeholder }
            .let { features.addAll(it) }

        // Subclass features at nextClassLevel (if subclass already chosen)
        if (ch.subclassId != null) {
            val subclass = vm.repository.getSubclass(ch.subclassId!!)
            if (subclass != null) {
                subclass.features
                    .filter { featureId ->
                        val localId = featureId.substringAfterLast(":")
                        val levelMatch = Regex("_l(\\d+)_").find(localId)
                        val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                        featureLevel == nextClassLevel
                    }
                    .mapNotNull { vm.repository.getFeature(it) }
                    .filter { !it.is_placeholder }
                    .let { features.addAll(it) }
            }
        }

        // Species traits at nextTotalLevel
        val species = vm.getAllSpecies().find { it.id == ch.speciesId.substringAfterLast(":") }
        val subspeciesId = ch.subspeciesId
        val selectedSub = subspeciesId?.let { id -> species?.subspecies?.find { it.id == id } }
        if (species != null) {
            val effectiveTraits = buildEffectiveTraits(species, selectedSub)
            for (trait in effectiveTraits) {
                val level = trait.level
                if (level != null && level == nextTotalLevel) {
                    features.add(Feature(
                        id = "trait_${species.id}_${trait.name.get()}",
                        name = trait.name,
                        description = trait.description,
                        level = level,
                        choice = trait.choice,
                        spell = trait.spell
                    ))
                }
            }
        }

        val proficientSkills = computeProficientSkills(ch)

        featuresAdapter = FeaturesCreateAdapter(
            onFeatureChoiceChanged = { featureId, choiceId ->
                if (choiceId != null) featureChoices[featureId] = choiceId else featureChoices.remove(featureId)
                updateButtons()
            },
            onFeatureMultiChoiceChanged = { featureId, choices ->
                featureMultiChoices[featureId] = choices
                updateButtons()
            },
            onAsiChoiceChanged = { featureId, asiChoice ->
                if (asiChoice != null) asiChoices[featureId] = asiChoice else asiChoices.remove(featureId)
                updateButtons()
            },
            onFeatSelected = { parentFeatureId, featId ->
                if (featId != null) {
                    // Load the feat and add its card to the adapter
                    val feat = vm.repository.getFeat(featId)
                    if (feat != null) {
                        val featFeature = featToFeature(feat, parentFeatureId)
                        featuresAdapter?.addFeatCard(parentFeatureId, featFeature)
                    }
                } else {
                    featuresAdapter?.removeFeatCard(parentFeatureId)
                }
                updateButtons()
            },
            onSubclassSelected = { featureId, subclassId ->
                // Store subclass choice in featureChoices
                if (subclassId != null) featureChoices[featureId] = subclassId else featureChoices.remove(featureId)
                // Show subclass features immediately
                featuresAdapter?.removeSubclassFeatures()
                if (subclassId != null) {
                    val subclass = vm.repository.getSubclass(subclassId)
                    if (subclass != null) {
                        val nextClsLevel = currentClassLevel + 1
                        val subFeatures = subclass.features
                            .filter { fid ->
                                val localId = fid.substringAfterLast(":")
                                val lm = Regex("_l(\\d+)_").find(localId)
                                val fl = lm?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                                fl == nextClsLevel
                            }
                            .mapNotNull { vm.repository.getFeature(it) }
                            .filter { !it.is_placeholder }
                        featuresAdapter?.addSubclassFeatures(subFeatures)
                    }
                }
                updateButtons()
            },
            onPickClassSpells = { featureId, current, choice ->
                val clsId = selectedClassId ?: char?.classId ?: ""
                val ability = clsId.let { it -> if (it.isNotBlank()) vm.getClassInfo(it)?.spellcasting?.ability else null } ?: "intelligence"
                ClassSpellPickerDialogFragment.newInstance(
                    classFilter = choice.class_filter ?: "",
                    cantrips = choice.cantrips,
                    spells = choice.spells,
                    selected = current,
                    charId = charId ?: char?.id ?: "",
                    ability = ability
                ).apply {
                    setOnResultListener { selected ->
                        featuresAdapter?.updateClassSpells(featureId, selected)
                    }
                }.show(childFragmentManager, "ClassSpellPicker")
            },
            initialFeatureChoices = featureChoices,
            initialFeatureMultiChoices = featureMultiChoices,
            initialAsiChoices = asiChoices,
            proficientSkills = proficientSkills,
            characterLevel = ch.level + 1,
            selectedFeats = ch.feats.toSet(),
            classId = selectedClass,
            allowEpicBoons = nextTotalLevel >= 19
        )

        recyclerView.adapter = featuresAdapter
        featuresAdapter?.submitList(features)
    }

    // ── Step 2: Spells ──

    private fun renderSpellsStep() {
        val ctx = requireContext()
        val ch = char ?: return
        val selectedClass = selectedClassId ?: return
        val cls = vm.getClassInfo(selectedClass) ?: return
        val spellFeature = cls.features
            .mapNotNull { vm.repository.getFeature(it) }
            .find { feature ->
                val choice = feature.choice
                choice != null && choice.type == "class_spells" && choice.level_up != null
            }

        if (spellFeature == null) {
            val title = TextView(ctx).apply {
                text = "Заклинания"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                gravity = Gravity.CENTER
            }
            binding.stepContainer.addView(title)
            val placeholder = TextView(ctx).apply {
                text = "Выбор заклинаний будет доступен в будущей версии"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                gravity = Gravity.CENTER
                setPadding(0, 32.dp(ctx), 0, 0)
            }
            binding.stepContainer.addView(placeholder)
            return
        }

        renderSorcererSpellsStep(ch, selectedClass, spellFeature)
    }

    private fun renderSorcererSpellsStep(ch: CharacterData, selectedClass: String, spellFeature: Feature) {
        val ctx = requireContext()
        val cls = vm.getClassInfo(selectedClass) ?: return
        val ability = cls.spellcasting?.ability ?: return
        val currentClassLevel = ch.classLevels[selectedClass] ?: if (selectedClass == ch.classId) ch.level else 0
        val newClassLevel = currentClassLevel + 1
        val gain = vm.getClassLevelSpellGain(selectedClass, currentClassLevel, newClassLevel)
        baseNewCantrips = gain.cantrips
        baseNewSpells = gain.spells

        val newRow = cls.class_table?.rows?.find { it.level == newClassLevel }
        maxNewSpellLevel = newRow?.values?.entries
            ?.filter { it.key.startsWith("slot") && it.value.toIntOrNull() ?: 0 > 0 }
            ?.maxOfOrNull { it.key.removePrefix("slot").toIntOrNull() ?: 0 }
            ?: 1

        loadSorcererSpellData(ch, selectedClass, ability)

        val scroll = NestedScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(content)
        binding.stepContainer.addView(scroll)

        content.addView(TextView(ctx).apply {
            text = "Заклинания"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            gravity = Gravity.CENTER
        })

        // ── New spells section (declare first so current adapter can reference it) ──
        lateinit var newAdapter: SpellPickerAdapter
        lateinit var newCounter: TextView
        val newRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            isNestedScrollingEnabled = false
        }
        val searchView = androidx.appcompat.widget.SearchView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setIconifiedByDefault(false)
            queryHint = "Поиск заклинания"
        }
        val chipGroup = com.google.android.material.chip.ChipGroup(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
        }

        // ── Current sorcerer spells (collapsible) ──
        val arrow = TextView(ctx).apply {
            text = "▼"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 8.dp(ctx), 0)
        }
        val currentRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            isNestedScrollingEnabled = false
        }
        val currentHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16.dp(ctx), 0, 8.dp(ctx))
            setOnClickListener {
                currentRecycler.visibility = if (currentRecycler.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                arrow.text = if (currentRecycler.visibility == View.VISIBLE) "▼" else "▶"
            }
        }
        val currentTitle = TextView(ctx).apply {
            text = "Чародей"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        currentHeader.addView(arrow)
        currentHeader.addView(currentTitle)
        content.addView(currentHeader)
        content.addView(currentRecycler)

        val currentAdapter = SpellPickerAdapter(
            onItemClick = { spell ->
                SpellDetailSheetDialog.newInstance(spell.fullId, ch.id, ability).show(childFragmentManager, "SpellDetail")
            },
            onAddClick = { spell ->
                val isCantrip = spell.level == 0
                if ((isCantrip && removeCantripId == spell.fullId) || (!isCantrip && removeSpellId == spell.fullId)) {
                    if (isCantrip) removeCantripId = null else removeSpellId = null
                    trimNewSelections()
                } else {
                    if (isCantrip) removeCantripId = spell.fullId else removeSpellId = spell.fullId
                }
                currentRecycler.adapter?.notifyDataSetChanged()
                refreshNewSpellsSection(newAdapter, newCounter)
                updateButtons()
            },
            isSelected = { spell ->
                (spell.level == 0 && removeCantripId == spell.fullId) ||
                (spell.level > 0 && removeSpellId == spell.fullId)
            },
            isLocked = { spell -> spell.fullId in (ch.spells?.alwaysPreparedSpells?.get(ability)?.toSet() ?: emptySet()) },
            selectedIcon = "✕",
            unselectedIcon = "–",
            lockedIcon = "🔒"
        )
        currentRecycler.adapter = currentAdapter
        currentAdapter.submitList(currentSorcererSpells)

        // ── New spells UI ──
        newCounter = TextView(ctx).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            gravity = Gravity.END
        }
        val newHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24.dp(ctx), 0, 8.dp(ctx))
        }
        val newTitle = TextView(ctx).apply {
            text = "Новые"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        newCounter.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        newHeader.addView(newTitle)
        newHeader.addView(newCounter)
        content.addView(newHeader)
        content.addView(searchView)
        content.addView(chipGroup)
        content.addView(newRecycler)

        var selectedLevel: Int? = null
        var searchQuery = ""

        newAdapter = SpellPickerAdapter(
            onItemClick = { spell ->
                SpellDetailSheetDialog.newInstance(spell.fullId, ch.id, ability).show(childFragmentManager, "SpellDetail")
            },
            onAddClick = { spell ->
                if (spell.level == 0) {
                    if (spell.fullId in newSelectedCantrips) {
                        newSelectedCantrips.remove(spell.fullId)
                    } else if (canSelectNewCantrip()) {
                        newSelectedCantrips.add(spell.fullId)
                    }
                } else {
                    if (spell.fullId in newSelectedSpells) {
                        newSelectedSpells.remove(spell.fullId)
                    } else if (canSelectNewSpell()) {
                        newSelectedSpells.add(spell.fullId)
                    }
                }
                refreshNewSpellsList(newAdapter, selectedLevel, searchQuery)
                updateNewCounter(newCounter)
                updateButtons()
            },
            isSelected = { spell ->
                (spell.level == 0 && spell.fullId in newSelectedCantrips) ||
                (spell.level > 0 && spell.fullId in newSelectedSpells)
            }
        )
        newRecycler.adapter = newAdapter

        fun setupLevelChips() {
            chipGroup.removeAllViews()
            val chipAll = Chip(ctx).apply {
                text = "Все"
                isCheckable = true
                isChecked = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedLevel = null
                        uncheckChips(chipGroup, this)
                        refreshNewSpellsList(newAdapter, selectedLevel, searchQuery)
                    }
                }
            }
            chipGroup.addView(chipAll)
            val levels = availableNewSpells.map { it.level }.distinct().sorted()
            for (level in levels) {
                val chip = Chip(ctx).apply {
                    text = if (level == 0) "Заговор" else "$level"
                    isCheckable = true
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedLevel = level
                            uncheckChips(chipGroup, this)
                            refreshNewSpellsList(newAdapter, selectedLevel, searchQuery)
                        }
                    }
                }
                chipGroup.addView(chip)
            }
        }

        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText?.lowercase()?.trim() ?: ""
                refreshNewSpellsList(newAdapter, selectedLevel, searchQuery)
                return true
            }
        })

        setupLevelChips()
        refreshNewSpellsList(newAdapter, selectedLevel, searchQuery)
        updateNewCounter(newCounter)
    }

    private fun loadSorcererSpellData(ch: CharacterData, selectedClass: String, ability: String) {
        val allSpells = vm.getAllSpellSummaries()
        val innate = ch.spells?.innateSpells?.get(ability) ?: emptyList()
        val sources = ch.spells?.innateSpellSources ?: emptyMap()
        val sorcererIds = innate.filter { sources[it] == selectedClass }
        currentSorcererSpells = allSpells
            .filter { it.fullId in sorcererIds }
            .sortedWith(compareBy<SpellSummary> { it.level }.thenBy { it.name.lowercase() })

        availableNewSpells = allSpells
            .filter { spell ->
                spell.classes.any { it == selectedClass || it.substringAfterLast(":") == selectedClass.substringAfterLast(":") } &&
                spell.level <= maxNewSpellLevel
            }
            .sortedWith(compareBy<SpellSummary> { it.level }.thenBy { it.name.lowercase() })
    }

    private fun uncheckChips(chipGroup: com.google.android.material.chip.ChipGroup, keep: Chip) {
        for (i in 0 until chipGroup.childCount) {
            val child = chipGroup.getChildAt(i)
            if (child is Chip && child !== keep) child.isChecked = false
        }
    }

    private fun canSelectNewCantrip(): Boolean {
        val max = baseNewCantrips + if (removeCantripId != null) 1 else 0
        return newSelectedCantrips.size < max
    }

    private fun canSelectNewSpell(): Boolean {
        val max = baseNewSpells + if (removeSpellId != null) 1 else 0
        return newSelectedSpells.size < max
    }

    private fun trimNewSelections() {
        while (newSelectedCantrips.size > baseNewCantrips) newSelectedCantrips.removeAt(newSelectedCantrips.lastIndex)
        while (newSelectedSpells.size > baseNewSpells) newSelectedSpells.removeAt(newSelectedSpells.lastIndex)
    }

    private fun areSpellSelectionsComplete(): Boolean {
        val requiredCantrips = baseNewCantrips + if (removeCantripId != null) 1 else 0
        val requiredSpells = baseNewSpells + if (removeSpellId != null) 1 else 0
        return newSelectedCantrips.size == requiredCantrips && newSelectedSpells.size == requiredSpells
    }

    private fun refreshNewSpellsList(adapter: SpellPickerAdapter?, selectedLevel: Int?, searchQuery: String) {
        var filtered = availableNewSpells
        selectedLevel?.let { lvl -> filtered = filtered.filter { it.level == lvl } }
        if (searchQuery.isNotBlank()) {
            val tokens = searchQuery.split("\\s+".toRegex()).filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                filtered = filtered.filter { spell ->
                    tokens.all { token ->
                        spell.name.lowercase().contains(token) ||
                        spell.school.lowercase().contains(token) ||
                        spell.tags.any { it.lowercase().contains(token) }
                    }
                }
            }
        }
        adapter?.submitList(filtered.sortedWith(compareBy<SpellSummary> { it.level }.thenBy { it.name.lowercase() }))
    }

    private fun refreshNewSpellsSection(adapter: SpellPickerAdapter?, newCounter: android.widget.TextView) {
        refreshNewSpellsList(adapter, null, "")
        updateNewCounter(newCounter)
    }

    private fun updateNewCounter(newCounter: android.widget.TextView) {
        val maxCantrips = baseNewCantrips + if (removeCantripId != null) 1 else 0
        val maxSpells = baseNewSpells + if (removeSpellId != null) 1 else 0
        newCounter.text = "Заговоры: ${newSelectedCantrips.size}/$maxCantrips, Заклинания: ${newSelectedSpells.size}/$maxSpells"
    }

    // ── Perform Level Up ──

    private fun performLevelUp() {
        val ch = char ?: return
        val selectedClass = selectedClassId ?: return
        val charId = charId ?: return

        val updatedClassLevels = ch.classLevels.toMutableMap()
        val currentClassLevel = updatedClassLevels[selectedClass] ?: if (selectedClass == ch.classId) ch.level else 0
        updatedClassLevels[selectedClass] = currentClassLevel + 1

        val newTotalLevel = ch.level + 1

        // Compute new proficiency bonus
        val newProfBonus = when {
            newTotalLevel <= 4 -> 2
            newTotalLevel <= 8 -> 3
            newTotalLevel <= 12 -> 4
            newTotalLevel <= 16 -> 5
            else -> 6
        }

        // Collect new features
        val cls = vm.getClassInfo(selectedClass)
        val newFeatures = mutableListOf<String>()
        cls?.features
            ?.filter { featureId ->
                val localId = featureId.substringAfterLast(":")
                val levelMatch = Regex("_l(\\d+)_").find(localId)
                val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                featureLevel == currentClassLevel + 1
            }
            ?.let { newFeatures.addAll(it) }

        // Also add subclass features if subclass already chosen
        val effectiveSubclassId = featureChoices.values.firstOrNull { fc ->
            featuresAdapter?.currentBaseItems?.any { it.choice?.type == "subclass" && featureChoices[it.id] == fc } == true
        } ?: ch.subclassId
        if (effectiveSubclassId != null) {
            val subclass = vm.repository.getSubclass(effectiveSubclassId)
            if (subclass != null) {
                subclass.features
                    .filter { featureId ->
                        val localId = featureId.substringAfterLast(":")
                        val levelMatch = Regex("_l(\\d+)_").find(localId)
                        val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                        featureLevel == currentClassLevel + 1
                    }
                    .let { newFeatures.addAll(it) }
            }
        }

        // Collect expertise skills from featureMultiChoices
        val newExpertiseSkills = ch.expertiseSkills.toMutableSet()
        for ((_, choices) in featureMultiChoices) {
            newExpertiseSkills.addAll(choices)
        }

        // Merge feature choices
        val mergedFeatureChoices = ch.featureChoices.toMutableMap()
        mergedFeatureChoices.putAll(featureChoices)

        val mergedFeatureMultiChoices = ch.featureMultiChoices.toMutableMap()
        mergedFeatureMultiChoices.putAll(featureMultiChoices)

        // Add new features to the character's features list
        val mergedFeatures = (ch.features + newFeatures).distinct().toMutableList()

        // Collect chosen feats from asi_or_feat choices
        val newFeats = ch.feats.toMutableList()
        for ((featureId, featId) in featureChoices) {
            val feature = featuresAdapter?.currentBaseItems?.find { it.id == featureId } ?: continue
            if (feature.choice?.type != "asi_or_feat") continue
            if (featId != null && featId !in newFeats) {
                newFeats.add(featId)
            }
        }

        // Handle subclass selection
        var updatedSubclassId = ch.subclassId
        for ((featureId, subclassId) in featureChoices) {
            val feature = featuresAdapter?.currentBaseItems?.find { it.id == featureId } ?: continue
            if (feature.choice?.type != "subclass") continue
            if (subclassId != null) {
                updatedSubclassId = subclassId
                // Add subclass features at this level
                val subclass = vm.repository.getSubclass(subclassId)
                if (subclass != null) {
                    val newSubLevel = currentClassLevel + 1
                    for (subFeatureId in subclass.features) {
                        val subFeature = vm.repository.getFeature(subFeatureId)
                        if (subFeature != null && (subFeature.level == null || subFeature.level == newSubLevel) && subFeatureId !in mergedFeatures) {
                            mergedFeatures.add(subFeatureId)
                        }
                    }
                }
            }
        }

        // Apply ASI bonuses
        val mergedAsiChoices = ch.asiChoices.toMutableMap()
        mergedAsiChoices.putAll(asiChoices)
        val updatedAbilityScores = ch.abilityScores.toMutableMap()
        for ((_, asi) in asiChoices) {
            if (asi.mode == "plus1x2") {
                if (asi.ability1.isNotEmpty()) {
                    updatedAbilityScores[asi.ability1] = (updatedAbilityScores[asi.ability1] ?: 10) + 1
                }
                if (asi.ability2.isNotEmpty()) {
                    updatedAbilityScores[asi.ability2] = (updatedAbilityScores[asi.ability2] ?: 10) + 1
                }
            } else {
                if (asi.ability1.isNotEmpty()) {
                    updatedAbilityScores[asi.ability1] = (updatedAbilityScores[asi.ability1] ?: 10) + 2
                }
            }
        }

        // Compute new HP: add hit die + CON mod (effective, with background bonus)
        val hitDie = cls?.hit_die ?: 6
        val hpRoll = (1..hitDie).random()
        val effectiveScores = vm.getEffectiveAbilityScores(ch)
        val conMod = vm.modifier(effectiveScores["constitution"] ?: 10)
        val newMaxHp = ch.hitPoints.max + hpRoll + conMod
        val newCurrentHp = ch.hitPoints.current + hpRoll + conMod

        val updated = ch.copy(
            level = newTotalLevel,
            classLevels = updatedClassLevels,
            subclassId = updatedSubclassId,
            proficiencyBonus = newProfBonus,
            features = mergedFeatures,
            featureChoices = mergedFeatureChoices,
            featureMultiChoices = mergedFeatureMultiChoices,
            expertiseSkills = newExpertiseSkills,
            feats = newFeats,
            asiChoices = mergedAsiChoices,
            abilityScores = updatedAbilityScores,
            hitPoints = ch.hitPoints.copy(max = newMaxHp, current = newCurrentHp),
            hitDice = ch.hitDice.copy(
                total = "${ch.level + 1}d$hitDie",
                remaining = ch.hitDice.remaining + 1
            )
        )

        // Add species innate spells at the new level
        val withSpeciesInnate = vm.addSpeciesInnateSpellsAtLevel(updated, newTotalLevel)

        // Add class feature spells at the new class level
        val newClassLevel = currentClassLevel + 1
        val withInnateSpells = vm.addClassFeatureSpellsAtLevel(withSpeciesInnate, selectedClass, newClassLevel)

        // Apply sorcerer level-up spell replacement/learning
        val withSorcererSpells = if (selectedClass.substringAfterLast(":").startsWith("sorcerer")) {
            vm.applySorcererLevelUpSpells(withInnateSpells, selectedClass, removeCantripId, removeSpellId, newSelectedCantrips, newSelectedSpells)
        } else {
            withInnateSpells
        }

        // If a spellcasting_ability choice was made during this level-up, store it
        val finalChar = if (updated.speciesSpellAbility == null) {
            val speciesId = updated.speciesId.substringAfterLast(":")
            val species = vm.getAllSpecies().find { it.id == speciesId }
            val selectedSub = updated.subspeciesId?.let { id -> species?.subspecies?.find { it.id == id } }
            var foundAbility: String? = null
            if (species != null) {
                val traits = buildEffectiveTraits(species, selectedSub)
                for (trait in traits) {
                    if (trait.choice?.type == "spellcasting_ability") {
                        val traitId = "trait_${species.id}_${trait.name.get()}"
                        mergedFeatureChoices[traitId]?.let { foundAbility = it }
                    }
                }
            }
            if (foundAbility != null) withSorcererSpells.copy(speciesSpellAbility = foundAbility) else withSorcererSpells
        } else withSorcererSpells

        vm.saveCharacter(finalChar)

        // Wait for the save to complete and StateFlow to update before navigating back
        lifecycleScope.launch {
            kotlinx.coroutines.delay(150)
            findNavController().navigateUp()
        }
    }

    // ── Helpers ──

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

    private fun computeProficientSkills(char: CharacterData): Set<String> {
        val proficient = mutableSetOf<String>()
        proficient.addAll(char.classSkillChoices)
        val bg = vm.getAllBackgrounds().find { it.id == char.backgroundId.substringAfterLast(":") }
        bg?.skill_proficiencies?.let { proficient.addAll(it) }
        return proficient
    }

    // ── Class Selection Adapter (matches ClassCreateAdapter look & feel) ──

    class LevelUpClassAdapter(
        private val classPairs: List<Pair<String, GameClass>>,  // (fullId, GameClass)
        private val onClassSelected: (String) -> Unit,          // receives fullId
        private val onNewClassSelected: () -> Unit,
        initialSelectedId: String?
    ) : RecyclerView.Adapter<LevelUpClassAdapter.ClassViewHolder>() {

        private var selectedId: String? = initialSelectedId
        private var expandedPosition = -1
        private val openIds = mutableSetOf<String>()

        override fun getItemCount() = classPairs.size + 1 // +1 for "New Class"

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
            val binding = CardClassCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ClassViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
            if (position < classPairs.size) {
                val (fullId, cls) = classPairs[position]
                val isExpanded = position == expandedPosition
                val isSelected = fullId == selectedId

                holder.binding.className.text = cls.name.get()
                holder.binding.hitDieLabel.text = "Кость хитов d${cls.hit_die}"
                holder.binding.radioButton.isChecked = isSelected
                holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE

                holder.binding.headerRow.setOnClickListener {
                    if (!isSelected) {
                        selectedId = fullId
                        onClassSelected(fullId)
                    }
                    val prev = expandedPosition
                    expandedPosition = if (isExpanded) -1 else position
                    if (prev >= 0) notifyItemChanged(prev)
                    if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
                }

                if (isExpanded) {
                    buildClassExpandedContent(holder.binding.expandedContent, cls)
                }
            } else {
                // "New Class" placeholder card
                val isExpanded = position == expandedPosition

                holder.binding.className.text = "Новый класс"
                holder.binding.hitDieLabel.text = ""
                holder.binding.radioButton.isChecked = false
                holder.binding.expandedContent.visibility = View.GONE

                holder.binding.headerRow.setOnClickListener {
                    onNewClassSelected()
                    val prev = expandedPosition
                    expandedPosition = if (isExpanded) -1 else position
                    if (prev >= 0) notifyItemChanged(prev)
                    if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
                }
            }
        }

        private fun buildClassExpandedContent(container: LinearLayout, cls: GameClass) {
            container.removeAllViews()
            val ctx = container.context

            // Description (same as ClassCreateAdapter)
            val (descCard, _) = ExpandableCard.createExpandableCard(
                ctx, title = "Описание", openId = "lu_class_desc_${cls.id}", openIdsSet = openIds
            ) { body ->
                body.addView(TextView(ctx).apply {
                    text = cls.description.get()
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                })
            }
            container.addView(descCard)

            // Class table (progression) (same as ClassCreateAdapter)
            cls.class_table?.let { table ->
                val (tableCard, _) = ExpandableCard.createExpandableCard(
                    ctx, title = "Таблица прогрессии", openId = "lu_class_table_${cls.id}", openIdsSet = openIds
                ) { body ->
                    body.addView(buildTable(ctx, table.columns, table.rows))
                }
                container.addView(tableCard)
            }

            // Key attributes (same as ClassCreateAdapter)
            if (cls.key_attributes.isNotEmpty()) {
                val attrContainer = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8.dp(ctx), 0, 8.dp(ctx))
                }
                attrContainer.addView(TextView(ctx).apply {
                    text = "Ключевые атрибуты:"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setPadding(0, 0, 0, 4.dp(ctx))
                })
                for ((key, value) in cls.key_attributes) {
                    attrContainer.addView(makeAttributeRow(ctx, key, value))
                }
                container.addView(attrContainer)
            }

            // Class features (same as ClassCreateAdapter)
            if (cls.features.isNotEmpty()) {
                val contentRepo = ContentRepository.get(ctx)
                val baseFeatures = cls.features.mapNotNull { contentRepo.getFeature(it) }
                if (baseFeatures.isNotEmpty()) {
                    val (featuresCard, _) = ExpandableCard.createExpandableCard(
                        ctx, title = "Умения класса", openId = "lu_class_features_${cls.id}", openIdsSet = openIds
                    ) { body ->
                        for (feature in baseFeatures) {
                            val levelSuffix = feature.level?.let { "Ур. $it: " } ?: ""
                            val (featureCard, _) = ExpandableCard.createExpandableCard(
                                ctx,
                                title = "$levelSuffix${feature.name.get()}",
                                openId = "lu_class_feat_${feature.id}",
                                openIdsSet = openIds
                            ) { fb ->
                                fb.addView(TextView(ctx).apply {
                                    text = feature.description.get()
                                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                                })
                            }
                            body.addView(featureCard)
                        }
                    }
                    container.addView(featuresCard)
                }
            }
        }

        // ─── Table (same as ClassCreateAdapter) ────────────────────────────

        private fun buildTable(
            ctx: android.content.Context,
            columns: List<com.herocraft24.core.model.ClassTableColumn>,
            rows: List<com.herocraft24.core.model.ClassTableRow>
        ): View {
            val surface = ctx.resolveColor(com.google.android.material.R.attr.colorSurface)
            val surfaceVariant = ctx.resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurface = ctx.resolveColor(com.google.android.material.R.attr.colorOnSurface)

            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val leftTable = android.widget.TableLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(8.dp(ctx), 8.dp(ctx), 0, 8.dp(ctx))
            }
            root.addView(leftTable)

            val scroll = android.widget.HorizontalScrollView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isHorizontalScrollBarEnabled = false
            }
            val rightTable = android.widget.TableLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, 8.dp(ctx), 8.dp(ctx), 8.dp(ctx))
            }
            scroll.addView(rightTable)
            root.addView(scroll)

            val headerHeight = 48.dp(ctx)
            val leftHeader = android.widget.TableRow(ctx).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, headerHeight)
                setBackgroundColor(surfaceVariant)
            }
            leftHeader.addView(makeTableCell(ctx, "Ур.", headerHeight, onSurface, isHeader = true))
            leftTable.addView(leftHeader)

            val rightHeader = android.widget.TableRow(ctx).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, headerHeight)
                setBackgroundColor(surfaceVariant)
            }
            for (col in columns) {
                rightHeader.addView(makeTableCell(ctx, breakColumnName(col.name.get()), headerHeight, onSurface, isHeader = true))
            }
            rightTable.addView(rightHeader)

            val rowHeight = 40.dp(ctx)
            val sortedRows = rows.sortedBy { it.level }
            sortedRows.forEachIndexed { index, row ->
                val rowColor = if (index % 2 == 0) surface else surfaceVariant

                val leftRow = android.widget.TableRow(ctx).apply {
                    layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight)
                    setBackgroundColor(rowColor)
                }
                leftRow.addView(makeTableCell(ctx, row.level.toString(), rowHeight, onSurface))
                leftTable.addView(leftRow)

                val rightRow = android.widget.TableRow(ctx).apply {
                    layoutParams = android.widget.TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight)
                    setBackgroundColor(rowColor)
                }
                for (col in columns) {
                    rightRow.addView(makeTableCell(ctx, row.values[col.key] ?: "—", rowHeight, onSurface))
                }
                rightTable.addView(rightRow)
            }

            return root
        }

        private fun makeTableCell(ctx: android.content.Context, text: String, height: Int, textColor: Int, isHeader: Boolean = false): TextView {
            return TextView(ctx).apply {
                this.text = text
                setTextColor(textColor)
                gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(8.dp(ctx), 4.dp(ctx), 8.dp(ctx), 4.dp(ctx))
                if (isHeader) setTypeface(null, Typeface.BOLD)
                layoutParams = android.widget.TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height)
                minWidth = 40.dp(ctx)
            }
        }

        private fun breakColumnName(name: String): String {
            if (name.length <= 4) return name
            val words = name.split(" ")
            if (words.size <= 1) return name
            return words.joinToString("\n")
        }

        // ─── Key attributes ──────────────────────────────────────────────────

        private fun makeAttributeRow(ctx: android.content.Context, label: String, value: String): View {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 2.dp(ctx), 0, 2.dp(ctx))
                addView(TextView(ctx).apply {
                    text = label
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTypeface(null, Typeface.BOLD)
                })
                addView(TextView(ctx).apply {
                    text = value
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setPadding(0, 0, 0, 4.dp(ctx))
                })
            }
        }

        class ClassViewHolder(val binding: CardClassCreateBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun resolveColor(attr: Int): Int {
        val ta = requireContext().theme?.obtainStyledAttributes(intArrayOf(attr))
        val color = ta?.getColor(0, 0) ?: 0
        ta?.recycle()
        return color
    }

    private fun featToFeature(feat: com.herocraft24.core.model.Feat, parentFeatureId: String): Feature {
        return Feature(
            id = "featcard_$parentFeatureId",
            name = feat.name,
            description = feat.description,
            level = null,
            choice = feat.choice
        )
    }
}
