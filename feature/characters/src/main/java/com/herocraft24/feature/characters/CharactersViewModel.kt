package com.herocraft24.feature.characters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Background
import com.herocraft24.core.model.ClassTableRow
import com.herocraft24.core.model.GameClass
import com.herocraft24.core.model.Spell
import com.herocraft24.core.model.SpellSummary
import com.herocraft24.core.model.Species
import com.herocraft24.core.ui.local.UiLocalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CharactersViewModel(application: Application) : AndroidViewModel(application) {

    val repo = CharacterRepository(application)
    val repository = ContentRepository.get(application)

    val characters: StateFlow<List<CharacterData>> = repo.characters

    private var allSpellSummariesCache: List<SpellSummary>? = null

    private val _editingCharacter = MutableStateFlow<CharacterData?>(null)
    val editingCharacter: StateFlow<CharacterData?> = _editingCharacter

    // Wizard state
    private val _wizardStep = MutableStateFlow(0)
    val wizardStep: StateFlow<Int> = _wizardStep
    private val _wizard = MutableStateFlow(CharacterData())
    val wizard: StateFlow<CharacterData> = _wizard

    init {
        repository.initialize()
        viewModelScope.launch {
            repo.loadAll()
            getAllSpellSummaries()
        }
    }

    fun deleteCharacter(id: String) { viewModelScope.launch { repo.delete(id) } }
    fun duplicateCharacter(id: String) { viewModelScope.launch { repo.duplicate(id) } }
    fun saveCharacter(char: CharacterData) { viewModelScope.launch { repo.save(char) } }
    fun getCharacter(id: String): CharacterData? = repo.getById(id)

    // Wizard
    fun startWizard() { _wizardStep.value = 0; _wizard.value = CharacterData() }
    fun setWizardStep(step: Int) { _wizardStep.value = step }
    fun updateWizard(update: (CharacterData) -> CharacterData) { _wizard.value = update(_wizard.value) }
    fun finishWizard() {
        viewModelScope.launch {
            val char = _wizard.value
            val hp = calculateStartingHP(char) + computeLevelUpFeatHpBonus(emptyList(), char.selectedFeats, 1)
            val equipment = calculateStartingEquipment(char)
            val classLevels = if (char.classLevels.isEmpty() && char.classId.isNotEmpty()) {
                mapOf(char.classId to 1)
            } else char.classLevels
            val charWithLevels = char.copy(classLevels = classLevels)

            // Apply ASI bonuses from asiChoices
            val asiScores = charWithLevels.abilityScores.toMutableMap()
            for ((_, asi) in charWithLevels.asiChoices) {
                if (asi.mode == "plus1x2") {
                    if (asi.ability1.isNotEmpty()) asiScores[asi.ability1] = (asiScores[asi.ability1] ?: 10) + 1
                    if (asi.ability2.isNotEmpty()) asiScores[asi.ability2] = (asiScores[asi.ability2] ?: 10) + 1
                } else {
                    if (asi.ability1.isNotEmpty()) asiScores[asi.ability1] = (asiScores[asi.ability1] ?: 10) + 2
                }
            }
            val charWithAsiBase = charWithLevels.copy(abilityScores = asiScores)
            val charWithAsi = charWithAsiBase.copy(
                abilityScores = applyFeatAsiToScores(
                    charWithAsiBase.abilityScores,
                    computeFeatAsiBonuses(charWithAsiBase, featParentKeys(charWithAsiBase.featureChoices))
                )
            )

            val (speciesSpellAbility, speciesInnate, speciesAlwaysPrepared) = buildSpeciesInnateSpells(charWithAsi, 1)
            val (classInnate, classAlwaysPrepared) = buildClassFeatureInnateSpells(charWithAsi)
            val featInnate = buildFeatInnateSpells(charWithAsi)
            val mergedInnate = mergeInnateSpells(speciesInnate, classInnate, featInnate)
            val alwaysPrepared = mergeInnateSpells(classAlwaysPrepared, speciesAlwaysPrepared)
            val sp = char.spells ?: CharacterSpells()
            repo.save(charWithAsi.copy(
                hitPoints = HitPoints(max = hp, current = hp),
                equipment = equipment,
                speciesSpellAbility = speciesSpellAbility,
                speed = repository.getSpecies(charWithAsi.speciesId)?.speed ?: charWithAsi.speed,
                feats = charWithAsi.selectedFeats.distinct(),
                spells = sp.copy(innateSpells = mergedInnate, innateSpellSources = emptyMap(), alwaysPreparedSpells = alwaysPrepared, spellbook = buildWizardSpellbook(charWithAsi))
            ))
            _wizardStep.value = 0
        }
    }

    suspend fun finishWizardSuspend() {
        val char = _wizard.value
        val hp = calculateStartingHP(char) + computeLevelUpFeatHpBonus(emptyList(), char.selectedFeats, 1)
        val equipment = calculateStartingEquipment(char)
        val classLevels = if (char.classLevels.isEmpty() && char.classId.isNotEmpty()) {
            mapOf(char.classId to 1)
        } else char.classLevels
        val charWithLevels = char.copy(classLevels = classLevels)

        // Apply ASI bonuses from asiChoices
        val asiScores = charWithLevels.abilityScores.toMutableMap()
        for ((_, asi) in charWithLevels.asiChoices) {
            if (asi.mode == "plus1x2") {
                if (asi.ability1.isNotEmpty()) asiScores[asi.ability1] = (asiScores[asi.ability1] ?: 10) + 1
                if (asi.ability2.isNotEmpty()) asiScores[asi.ability2] = (asiScores[asi.ability2] ?: 10) + 1
            } else {
                if (asi.ability1.isNotEmpty()) asiScores[asi.ability1] = (asiScores[asi.ability1] ?: 10) + 2
            }
        }
        val charWithAsiBase = charWithLevels.copy(abilityScores = asiScores)
        val charWithAsi = charWithAsiBase.copy(
            abilityScores = applyFeatAsiToScores(
                charWithAsiBase.abilityScores,
                computeFeatAsiBonuses(charWithAsiBase, featParentKeys(charWithAsiBase.featureChoices))
            )
        )

        val (speciesSpellAbility, speciesInnate, speciesAlwaysPrepared) = buildSpeciesInnateSpells(charWithAsi, 1)
        val (classInnate, classAlwaysPrepared) = buildClassFeatureInnateSpells(charWithAsi)
        val featInnate = buildFeatInnateSpells(charWithAsi)
        val mergedInnate = mergeInnateSpells(speciesInnate, classInnate, featInnate)
        val alwaysPrepared = mergeInnateSpells(classAlwaysPrepared, speciesAlwaysPrepared)
        val sp = char.spells ?: CharacterSpells()
        repo.save(charWithAsi.copy(
            hitPoints = HitPoints(max = hp, current = hp),
            equipment = equipment,
            speciesSpellAbility = speciesSpellAbility,
            speed = repository.getSpecies(charWithAsi.speciesId)?.speed ?: charWithAsi.speed,
            feats = charWithAsi.selectedFeats.distinct(),
            spells = sp.copy(innateSpells = mergedInnate, innateSpellSources = emptyMap(), alwaysPreparedSpells = alwaysPrepared, spellbook = buildWizardSpellbook(charWithAsi))
        ))
        _wizard.value = CharacterData()
        _wizardStep.value = 0
    }

    fun loadForEdit(id: String) { _editingCharacter.value = repo.getById(id) }
    fun clearEdit() { _editingCharacter.value = null }

    // Computed
    fun modifier(score: Int) = kotlin.math.floor((score - 10).toDouble() / 2).toInt()

    fun getEffectiveAbilityScores(char: CharacterData): Map<String, Int> {
        val scores = char.abilityScores.toMutableMap()
        val bgId = char.backgroundId.substringAfterLast(":")
        val bg = getAllBackgrounds().find { it.id == bgId }
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
        return scores
    }
    fun skillBonus(skill: SkillState, char: CharacterData): Int {
        val mod = modifier(char.abilityScores[abilityForSkill(skill.skill)] ?: 10)
        var bonus = if (skill.proficient) char.proficiencyBonus else 0
        if (skill.expertise) bonus += char.proficiencyBonus
        return mod + bonus
    }
    fun saveBonus(ability: String, char: CharacterData): Int =
        modifier(char.abilityScores[ability] ?: 10) + if (ability in char.savingThrows) char.proficiencyBonus else 0
    fun spellAttack(char: CharacterData): Int {
        val ability = getEffectiveSpellcastingAbility(char)
        return char.proficiencyBonus + modifier(char.abilityScores[ability] ?: 10)
    }
    fun spellDC(char: CharacterData): Int {
        val ability = getEffectiveSpellcastingAbility(char)
        return 8 + char.proficiencyBonus + modifier(char.abilityScores[ability] ?: 10)
    }

    fun getEffectiveSpellcastingAbility(char: CharacterData): String {
        char.spellcastingAbilityOverride?.let { return it }
        val cls = getClassInfo(char.classId)
        return cls?.spellcasting?.ability ?: "intelligence"
    }

    fun setSpellcastingAbilityOverride(charId: String, ability: String?) {
        val char = getCharacter(charId) ?: return
        saveCharacter(char.copy(spellcastingAbilityOverride = ability))
    }

    fun computeSpellSlots(char: CharacterData): SpellSlotsCounter.CasterInfo? {
        // Магия договора (колдун): ячейки берутся напрямую из таблицы класса (spell_slots / slot_level)
        val cls = getClassInfo(char.classId)
        val classLevel = char.classLevels[char.classId] ?: char.level
        val row = cls?.class_table?.rows?.find { it.level == classLevel }
        val pactSlots = row?.values?.get("spell_slots")?.toIntOrNull() ?: 0
        val pactLevel = row?.values?.get("slot_level")?.toIntOrNull() ?: 0
        if (pactSlots > 0 && pactLevel > 0) {
            return SpellSlotsCounter.CasterInfo(classLevel, mapOf(pactLevel to pactSlots))
        }
        return SpellSlotsCounter.compute(char.classId, char.level, char.subclassId)
    }

    fun getEffectiveSpellSlots(char: CharacterData): Map<String, SpellSlotState> {
        val computed = computeSpellSlots(char) ?: return emptyMap()
        val saved = char.spellSlots
        return computed.slots.map { (level, total) ->
            val savedState = saved[level.toString()]
            level.toString() to SpellSlotState(
                total = total,
                used = savedState?.used?.coerceIn(0, total) ?: 0
            )
        }.toMap()
    }

    fun toggleSpellSlot(charId: String, slotLevel: String) {
        val char = getCharacter(charId) ?: return
        val effective = getEffectiveSpellSlots(char)
        val state = effective[slotLevel] ?: return
        val newUsed = if (state.used < state.total) state.used + 1 else 0
        val updated = char.spellSlots.toMutableMap().apply { this[slotLevel] = SpellSlotState(state.total, newUsed) }
        saveCharacter(char.copy(spellSlots = updated))
    }

    fun incrementSpellSlot(charId: String, slotLevel: String) {
        val char = getCharacter(charId) ?: return
        val effective = getEffectiveSpellSlots(char)
        val state = effective[slotLevel] ?: return
        if (state.used <= 0) return
        val updated = char.spellSlots.toMutableMap().apply {
            this[slotLevel] = SpellSlotState(state.total, state.used - 1)
        }
        saveCharacter(char.copy(spellSlots = updated))
    }

    fun decrementSpellSlot(charId: String, slotLevel: String) {
        val char = getCharacter(charId) ?: return
        val effective = getEffectiveSpellSlots(char)
        val state = effective[slotLevel] ?: return
        if (state.used >= state.total) return
        val updated = char.spellSlots.toMutableMap().apply {
            this[slotLevel] = SpellSlotState(state.total, state.used + 1)
        }
        saveCharacter(char.copy(spellSlots = updated))
    }

    fun resolveResourceTotal(formula: String, char: CharacterData, classId: String? = null): Int {
        return when {
            formula.startsWith("max(") -> {
                // Parse "max(charisma_modifier,1)" pattern
                val inner = formula.removePrefix("max(").removeSuffix(")")
                val parts = inner.split(",")
                val first = resolveResourcePart(parts[0].trim(), char)
                val second = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                kotlin.math.max(first, second)
            }
            formula.startsWith("class_table:") -> {
                // Parse "class_table:<key>" to lookup the current level row of a class table.
                val tableKey = formula.removePrefix("class_table:")
                val targetClassId = classId ?: char.classId
                val levelInClass = char.classLevels[targetClassId] ?: if (targetClassId == char.classId) char.level else 0
                val cls = getClassInfo(targetClassId) ?: return 0
                cls.class_table?.rows?.find { it.level == levelInClass }?.values?.get(tableKey)?.toIntOrNull() ?: 0
            }
            else -> formula.toIntOrNull() ?: resolveResourcePart(formula, char)
        }
    }

    private fun resolveResourcePart(part: String, char: CharacterData): Int {
        val scores = getEffectiveAbilityScores(char)
        return when (part) {
            "charisma_modifier" -> modifier(scores["charisma"] ?: 10)
            "constitution_modifier" -> modifier(scores["constitution"] ?: 10)
            "strength_modifier" -> modifier(scores["strength"] ?: 10)
            "dexterity_modifier" -> modifier(scores["dexterity"] ?: 10)
            "intelligence_modifier" -> modifier(scores["intelligence"] ?: 10)
            "wisdom_modifier" -> modifier(scores["wisdom"] ?: 10)
            "proficiency_bonus" -> char.proficiencyBonus
            else -> 0
        }
    }

    fun getEffectiveFeatureResources(char: CharacterData): Map<String, FeatureResourceState> {
        val result = mutableMapOf<String, FeatureResourceState>()
        // Collect features with resources from all classes
        val allClassIds = char.classLevels.keys + char.classId
        for (cid in allClassIds) {
            val c = getClassInfo(cid) ?: continue
            val levelInClass = char.classLevels[cid] ?: if (cid == char.classId) char.level else 0
            c.features
                .filter { featureId ->
                    val levelMatch = Regex("_l(\\d+)_").find(featureId)
                    val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                    featureLevel <= levelInClass
                }
                .mapNotNull { featureId -> repository.getFeature(featureId)?.let { featureId to it } }
                .filter { (_, feature) -> feature.resource != null }
                .forEach { (fullFeatureId, feature) ->
                    val total = resolveResourceTotal(feature.resource!!.count_formula, char, cid)
                    val saved = char.featureResources[fullFeatureId]
                    result[fullFeatureId] = FeatureResourceState(total = total, used = saved?.used ?: 0)
                }
        }
        // Also from subclass
        if (char.subclassId != null) {
            val subclass = repository.getSubclass(char.subclassId!!)
            if (subclass != null) {
                subclass.features
                    .filter { featureId ->
                        val levelMatch = Regex("_l(\\d+)_").find(featureId)
                        val featureLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@filter false
                        featureLevel <= char.level
                    }
                    .mapNotNull { featureId -> repository.getFeature(featureId)?.let { featureId to it } }
                    .filter { (_, feature) -> feature.resource != null }
                    .forEach { (fullFeatureId, feature) ->
                        val total = resolveResourceTotal(feature.resource!!.count_formula, char, char.classId)
                        val saved = char.featureResources[fullFeatureId]
                        result[fullFeatureId] = FeatureResourceState(total = total, used = saved?.used ?: 0)
                    }
            }
        }
        return result
    }

    fun toggleFeatureResource(charId: String, featureId: String) {
        val char = getCharacter(charId) ?: return
        val resources = getEffectiveFeatureResources(char)
        val state = resources[featureId] ?: return
        val newUsed = if (state.used < state.total) state.used + 1 else 0
        val updated = char.featureResources.toMutableMap().apply {
            this[featureId] = FeatureResourceState(state.total, newUsed)
        }
        saveCharacter(char.copy(featureResources = updated))
    }

    fun incrementFeatureResource(charId: String, featureId: String) {
        val char = getCharacter(charId) ?: return
        val resources = getEffectiveFeatureResources(char)
        val state = resources[featureId] ?: return
        if (state.used <= 0) return
        val updated = char.featureResources.toMutableMap().apply {
            this[featureId] = FeatureResourceState(state.total, state.used - 1)
        }
        saveCharacter(char.copy(featureResources = updated))
    }

    fun decrementFeatureResource(charId: String, featureId: String) {
        val char = getCharacter(charId) ?: return
        val resources = getEffectiveFeatureResources(char)
        val state = resources[featureId] ?: return
        if (state.used >= state.total) return
        val updated = char.featureResources.toMutableMap().apply {
            this[featureId] = FeatureResourceState(state.total, state.used + 1)
        }
        saveCharacter(char.copy(featureResources = updated))
    }

    fun addPreparedSpell(charId: String, spellId: String, ability: String, source: String = SPELL_SOURCE_MANUAL) {
        val char = getCharacter(charId) ?: return
        val sp = char.spells ?: CharacterSpells()
        val current = sp.preparedByAbility[ability] ?: emptyList()
        val entry = spellEntry(spellId, source)
        if (entry in current) return
        val updated = sp.copy(
            preparedByAbility = sp.preparedByAbility.toMutableMap().apply {
                this[ability] = current + entry
            }
        )
        saveCharacter(char.copy(spells = updated))
    }

    fun removePreparedSpell(charId: String, spellEntryId: String, ability: String) {
        val char = getCharacter(charId) ?: return
        val sp = char.spells ?: return
        val current = sp.preparedByAbility[ability] ?: return
        val updated = sp.copy(
            preparedByAbility = sp.preparedByAbility.toMutableMap().apply {
                this[ability] = current - spellEntryId
            }
        )
        saveCharacter(char.copy(spells = updated))
    }

    fun getAllSpellSummaries(): List<SpellSummary> {
        allSpellSummariesCache?.let { return it }
        val ids = repository.getSpellIds()
        val result = ids.mapNotNull { fullId ->
            val entry = repository.getManifestEntry(fullId) ?: return@mapNotNull null
            val spell = repository.getSpell(fullId)
            SpellSummary(
                fullId = fullId,
                name = entry.name.get(),
                level = entry.level ?: 0,
                school = entry.school ?: "",
                concentration = entry.concentration ?: false,
                ritual = entry.ritual ?: false,
                components = spell?.components ?: emptyList(),
                classes = entry.classes ?: emptyList(),
                subclasses = spell?.subclasses ?: emptyList(),
                castingTime = spell?.casting_time ?: "",
                damageType = spell?.damage?.damage_type,
                tags = entry.tags,
                material = spell?.material,
                materialHasCost = entry.material_has_cost ?: false,
                materialConsumable = entry.material_consumable ?: false
            )
        }
        allSpellSummariesCache = result
        return result
    }

    data class PreparedSpellEntry(val entryId: String, val spell: SpellSummary)

    fun getPreparedSpellSummaries(char: CharacterData, ability: String): List<PreparedSpellEntry> {
        val sp = char.spells ?: return emptyList()
        val preparedIds = sp.preparedByAbility[ability] ?: emptyList()
        val innateIds = sp.innateSpells[ability] ?: emptyList()
        val entries = (innateIds + preparedIds).distinct()
        if (entries.isEmpty()) return emptyList()
        val byId = getAllSpellSummaries().associateBy { it.fullId }
        return entries.mapNotNull { entryId ->
            byId[entryId.spellFullId()]?.let { PreparedSpellEntry(entryId, it) }
        }
    }

    fun resolveSpellSourceName(source: String): String? {
        if (source.isEmpty() || source == SPELL_SOURCE_MANUAL) return null
        return repository.resolveName(source)
    }

    fun getInnateSpellIds(char: CharacterData, ability: String): Set<String> {
        val sp = char.spells ?: return emptySet()
        return (sp.innateSpells[ability] ?: emptyList()).toSet()
    }

    fun getAlwaysPreparedSpellIds(char: CharacterData, ability: String): Set<String> {
        val sp = char.spells ?: return emptySet()
        return (sp.alwaysPreparedSpells[ability] ?: emptyList()).toSet()
    }

    fun calculateStartingEquipment(char: CharacterData): List<CharacterItem> {
        val result = mutableListOf<CharacterItem>()
        android.util.Log.d("InventoryDebug", "calculateStartingEquipment for class=${char.classId}, bg=${char.backgroundId}")

        // Background fixed items
        val bg = repository.getBackgroundIds().find { it.endsWith(":${char.backgroundId.substringAfterLast(":")}") }
            ?.let { repository.getBackground(it) }
        bg?.equipment_items?.forEach { itemId ->
            result.add(CharacterItem(itemId = itemId))
        }
        // Background equipment choices (single index for now)
        bg?.equipment?.forEachIndexed { index, choice ->
            if (choice.options.isEmpty()) return@forEachIndexed
            val optionIndex = char.bgEquipmentChoice.coerceIn(choice.options.indices)
            val option = choice.options.getOrNull(optionIndex) ?: return@forEachIndexed
            collectEquipmentOption(option, result)
        }

        // Class starting equipment
        val cls = repository.getClass(char.classId)
        cls?.starting_equipment?.forEachIndexed { index, choice ->
            if (choice.options.isEmpty()) return@forEachIndexed
            val optionIndex = char.classEquipmentChoice.coerceIn(choice.options.indices)
            val option = choice.options.getOrNull(optionIndex) ?: return@forEachIndexed
            collectEquipmentOption(option, result)
        }

        return result
    }

    private fun collectEquipmentOption(option: com.herocraft24.core.model.EquipmentOption, result: MutableList<CharacterItem>) {
        option.item_id?.let { itemId ->
            val item = repository.getItem(itemId)
            if (item?.category == "pack" && item.contents.isNotEmpty()) {
                item.contents.forEach { content ->
                    collectEquipmentOption(com.herocraft24.core.model.EquipmentOption(item_id = content.item_id, quantity = content.quantity), result)
                }
            } else {
                result.add(CharacterItem(itemId = itemId, quantity = option.quantity))
            }
        }
        option.items.forEach { collectEquipmentOption(it, result) }
        option.options.forEach { collectEquipmentOption(it, result) }
    }

    fun calculateStartingHP(char: CharacterData): Int {
        val cls = repository.getClass(char.classId) ?: return 10
        val conScore = char.abilityScores["constitution"] ?: 10
        // Apply background ability bonus to constitution
        val effectiveCon = if (char.bgAbilityMode == false || char.bgAbilityMode == null) {
            // All +1 mode — check if constitution is in the background's ASI list
            val bg = repository.getBackgroundIds().find { it.endsWith(":${char.backgroundId.substringAfterLast(":")}") }
                ?.let { repository.getBackground(it) }
            if (bg?.ability_score_increases?.any { it.ability == "constitution" } == true) conScore + 1
            else conScore
        } else if (char.bgAbilityMode == true) {
            var score = conScore
            if (char.bgAbilityPlus2 == "constitution") score += 2
            if (char.bgAbilityPlus1 == "constitution") score += 1
            score
        } else conScore
        return cls.hit_die + modifier(effectiveCon)
    }

    fun getClassInfo(classId: String): GameClass? = repository.getClass(classId)
    fun resolveName(id: String): String? = repository.resolveName(id)
    fun resolveEquipmentName(composite: String?): String? {
        if (composite == null) return null
        val itemId = composite.substringBefore("|")
        val variantItemId = composite.substringAfter("|", "").takeIf { it.isNotBlank() }
        val item = getItem(itemId) ?: return null
        val variant = variantItemId?.let { getItem(it) }
        return if (variant != null) "${item.name.get()} (${variant.name.get()})" else item.name.get()
    }
    fun getSpeciesIds() = repository.getSpeciesIds()
    fun getAllSpecies(): List<Species> = repository.getSpeciesIds().mapNotNull { repository.getSpecies(it) }
    fun getBackgroundIds() = repository.getBackgroundIds()
    fun getAllBackgrounds(): List<Background> = repository.getBackgroundIds().mapNotNull { repository.getBackground(it) }
    fun getClassIds() = repository.getClassIds()
    fun getFeatIds() = repository.getFeatIds()
    fun getSpellIds() = repository.getSpellIds()
    fun getItemIds() = repository.getItemIds()
    fun getSpell(id: String) = repository.getSpell(id)
    fun getItem(id: String) = repository.getItem(id)

    fun addItemToBackpack(charId: String, itemId: String, variantItemId: String? = null) {
        val current = getCharacter(charId) ?: return
        saveCharacter(current.copy(equipment = current.equipment + CharacterItem(itemId = itemId, variantItemId = variantItemId)))
    }

    fun removeItemFromBackpack(charId: String, itemId: String, variantItemId: String? = null) {
        val current = getCharacter(charId) ?: return
        val index = current.equipment.indexOfFirst { it.itemId == itemId && it.variantItemId == variantItemId }
        if (index >= 0) {
            val removed = current.equipment[index]
            val updatedEquipment = current.equipment.toMutableList().apply { removeAt(index) }
            val composite = if (removed.variantItemId != null) "${removed.itemId}|${removed.variantItemId}" else removed.itemId

            val updatedMagicItems = current.equippedMagicItems.filter { it != composite && it != removed.itemId }
            saveCharacter(current.copy(
                equipment = updatedEquipment,
                equippedArmor = if (current.equippedArmor == composite || current.equippedArmor == removed.itemId) null else current.equippedArmor,
                equippedShield = if (current.equippedShield == composite || current.equippedShield == removed.itemId) null else current.equippedShield,
                equippedWeapon1 = if (current.equippedWeapon1 == composite || current.equippedWeapon1 == removed.itemId) null else current.equippedWeapon1,
                equippedWeapon2 = if (current.equippedWeapon2 == composite || current.equippedWeapon2 == removed.itemId) null else current.equippedWeapon2,
                equippedMagicItems = updatedMagicItems
            ))
        }
    }

    fun findMagicItemVariants(item: com.herocraft24.core.model.Item): List<Pair<String, com.herocraft24.core.model.Item>> {
        if (!item.magic) return emptyList()
        if (item.category !in listOf("armor", "weapon", "shield")) return emptyList()
        if (item.subcategory.isEmpty()) return emptyList()
        
        android.util.Log.d("MagicVariants", "Finding variants for ${item.name.ru}, category=${item.category}, subcategory=${item.subcategory}, view=${item.view}")
        
        val allItems = repository.getItemIds().mapNotNull { fullId -> getItem(fullId)?.let { fullId to it } }
        val hasSpecificView = item.view.isNotEmpty() && !item.view.any { isExclusionPhrase(it) }
        
        val candidates = allItems.filter { (_, candidate) ->
            if (candidate.category != item.category || candidate.magic) return@filter false
            if (hasSpecificView) {
                return@filter item.view.any { it.equals(candidate.name.ru, ignoreCase = true) }
            }
            return@filter candidate.subcategory.any { it in item.subcategory }
        }
        
        android.util.Log.d("MagicVariants", "Found ${candidates.size} candidates before exclusion filter")
        
        if (item.view.isNotEmpty()) {
            val exclusions = item.view.filter { isExclusionPhrase(it) }
            if (exclusions.isNotEmpty()) {
                val filtered = candidates.filter { (_, candidate) ->
                    val name = candidate.name.ru ?: return@filter true
                    val matches = exclusions.any { excluded -> nameMatchesExclusion(name, excluded) }
                    if (matches) android.util.Log.d("MagicVariants", "Excluding ${candidate.name.ru}")
                    !matches
                }
                android.util.Log.d("MagicVariants", "After exclusion: ${filtered.size} candidates")
                return filtered
            }
        }
        android.util.Log.d("MagicVariants", "Returning ${candidates.size} candidates")
        return candidates
    }

    private fun isExclusionPhrase(view: String): Boolean {
        return view.startsWith("кроме", ignoreCase = true) || view.startsWith("except", ignoreCase = true)
    }

    private fun nameMatchesExclusion(name: String, exclusion: String): Boolean {
        val normalizedName = name.lowercase().replace(Regex("[^\\p{L}\\d]"), "")
        val keywords = listOf("кроме", "except")
        val exclusionWords = exclusion.lowercase()
            .split(" ")
            .map { it.replace(Regex("[^\\p{L}\\d]"), "") }
            .filter { it.length > 2 && it !in keywords }
        if (exclusionWords.isEmpty()) return false
        return exclusionWords.all { word ->
            val stem = word.dropLast(minOf(3, word.length))
            normalizedName.contains(stem)
        }
    }

    fun buildSpeciesInnateSpells(char: CharacterData, upToLevel: Int): Triple<String?, Map<String, List<String>>, Map<String, List<String>>> {
        val speciesId = char.speciesId.substringAfterLast(":")
        val species = getSpeciesIds().mapNotNull { getSpeciesInfo(it) }.find { it.id == speciesId }
            ?: return Triple(null, emptyMap<String, List<String>>(), emptyMap<String, List<String>>())
        val selectedSub = char.subspeciesId?.let { id -> species.subspecies?.find { it.id == id } }

        val effectiveTraits = mutableListOf<com.herocraft24.core.model.SpeciesTrait>()
        for (trait in species.traits) {
            if (trait.is_placeholder && selectedSub != null) {
                effectiveTraits.addAll(selectedSub.traits)
            } else {
                effectiveTraits.add(trait)
            }
        }

        // Find the spellcasting ability choice from featureChoices or a fixed trait ability
        var speciesSpellAbility: String? = char.speciesSpellAbility
        for (trait in effectiveTraits) {
            if (trait.choice?.type == "spellcasting_ability") {
                val traitId = "trait_${species.id}_${trait.name.get()}"
                char.featureChoices[traitId]?.let { speciesSpellAbility = it }
            }
        }
        if (speciesSpellAbility == null) {
            // Fallback to a fixed spellcasting ability declared on any trait
            speciesSpellAbility = effectiveTraits.firstOrNull { it.spellcasting_ability != null }?.spellcasting_ability
        }

        val innateSpells = mutableMapOf<String, MutableList<String>>()
        val alwaysPrepared = mutableMapOf<String, MutableList<String>>()
        // Preserve existing innate spells
        char.spells?.innateSpells?.forEach { (ability, spells) ->
            innateSpells[ability] = spells.toMutableList()
        }
        char.spells?.alwaysPreparedSpells?.forEach { (ability, spells) ->
            alwaysPrepared[ability] = spells.toMutableList()
        }

        val ability = speciesSpellAbility ?: return Triple(speciesSpellAbility, innateSpells.mapValues { it.value.toList() }, alwaysPrepared.mapValues { it.value.toList() })
        for (trait in effectiveTraits) {
            val spell = trait.spell ?: continue
            val traitLevel = trait.level ?: continue
            if (traitLevel > upToLevel) continue
            val entry = spellEntry(spell, char.speciesId)
            val list = innateSpells.getOrPut(ability) { mutableListOf() }
            if (entry !in list) list.add(entry)
            if (trait.always_prepared) {
                val apList = alwaysPrepared.getOrPut(ability) { mutableListOf() }
                if (entry !in apList) apList.add(entry)
            }
        }

        return Triple(speciesSpellAbility, innateSpells.mapValues { it.value.toList() }, alwaysPrepared.mapValues { it.value.toList() })
    }

    fun addSpeciesInnateSpellsAtLevel(char: CharacterData, newLevel: Int): CharacterData {
        val speciesId = char.speciesId.substringAfterLast(":")
        val species = getSpeciesIds().mapNotNull { getSpeciesInfo(it) }.find { it.id == speciesId }
            ?: return char
        val selectedSub = char.subspeciesId?.let { id -> species.subspecies?.find { it.id == id } }

        val effectiveTraits = mutableListOf<com.herocraft24.core.model.SpeciesTrait>()
        for (trait in species.traits) {
            if (trait.is_placeholder && selectedSub != null) {
                effectiveTraits.addAll(selectedSub.traits)
            } else {
                effectiveTraits.add(trait)
            }
        }

        val ability = char.speciesSpellAbility
            ?: effectiveTraits.firstOrNull { it.spellcasting_ability != null }?.spellcasting_ability
            ?: return char
        val sp = char.spells ?: CharacterSpells()
        val innateMap = sp.innateSpells.toMutableMap()
        val alwaysPreparedMap = sp.alwaysPreparedSpells.toMutableMap()
        val spellList = innateMap.getOrPut(ability) { mutableListOf() }.toMutableList()
        val alwaysPreparedList = alwaysPreparedMap.getOrPut(ability) { mutableListOf() }.toMutableList()

        for (trait in effectiveTraits) {
            val spell = trait.spell ?: continue
            val traitLevel = trait.level ?: continue
            if (traitLevel != newLevel) continue
            val entry = spellEntry(spell, char.speciesId)
            if (entry !in spellList) spellList.add(entry)
            if (trait.always_prepared && entry !in alwaysPreparedList) alwaysPreparedList.add(entry)
        }

        innateMap[ability] = spellList
        alwaysPreparedMap[ability] = alwaysPreparedList
        val updated = char.copy(spells = sp.copy(innateSpells = innateMap, alwaysPreparedSpells = alwaysPreparedMap))
        return if (char.speciesSpellAbility == null) updated.copy(speciesSpellAbility = ability) else updated
    }

    private fun getSpeciesInfo(fullId: String): Species? = repository.getSpecies(fullId)

    private fun mergeInnateSpells(vararg maps: Map<String, List<String>>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        for (map in maps) {
            for ((ability, spells) in map) {
                val list = result.getOrPut(ability) { mutableListOf() }
                for (spell in spells) {
                    if (spell !in list) list.add(spell)
                }
            }
        }
        return result.mapValues { it.value.toList() }
    }

    fun buildClassFeatureInnateSpells(char: CharacterData): Pair<Map<String, List<String>>, Map<String, List<String>>> {
        val innateSpells = mutableMapOf<String, MutableList<String>>()
        val alwaysPrepared = mutableMapOf<String, MutableList<String>>()
        char.spells?.innateSpells?.forEach { (ability, spells) ->
            innateSpells[ability] = spells.toMutableList()
        }
        char.spells?.alwaysPreparedSpells?.forEach { (ability, spells) ->
            alwaysPrepared[ability] = spells.toMutableList()
        }

        fun addInnate(ability: String, spell: String, classId: String) {
            val entry = spellEntry(spell, classId)
            val list = innateSpells.getOrPut(ability) { mutableListOf() }
            if (entry !in list) list.add(entry)
        }

        fun addAlwaysPrepared(ability: String, spell: String, classId: String) {
            val entry = spellEntry(spell, classId)
            val apList = alwaysPrepared.getOrPut(ability) { mutableListOf() }
            if (entry !in apList) apList.add(entry)
        }

        val allClassIds = (char.classLevels.keys + char.classId).distinct()
        for (classId in allClassIds) {
            val cls = getClassInfo(classId) ?: continue
            val spellAbility = cls.spellcasting?.ability ?: continue
            val levelInClass = char.classLevels[classId] ?: if (classId == char.classId) char.level else 0

            for (featureId in cls.features) {
                val feature = repository.getFeature(featureId) ?: continue
                val featureLevel = feature.level ?: continue
                if (featureLevel > levelInClass) continue
                feature.spell?.let { spell -> addInnate(spellAbility, spell, classId) }
                // Always-prepared spells from feature (e.g. Warlock Contact Patron)
                for ((requiredLevel, spells) in feature.always_prepared) {
                    if (levelInClass < requiredLevel.toIntOrNull() ?: continue) continue
                    for (spell in spells) {
                        addInnate(spellAbility, spell, classId)
                        addAlwaysPrepared(spellAbility, spell, classId)
                    }
                }
                // Class spell choices (e.g. Sorcerer Spellcasting, Wizard Spellcasting)
                val choiceType = feature.choice?.type
                if (choiceType == "class_spells" || choiceType == "wizard_spells") {
                    val selected = char.featureMultiChoices[feature.id] ?: emptyList()
                    for (spell in selected) {
                        // У волшебника в подготовленные идут только заговоры; остальное — в книгу
                        if (choiceType == "wizard_spells" && (repository.getSpell(spell)?.level ?: 0) > 0) continue
                        addInnate(spellAbility, spell, classId)
                    }
                }
            }

            // Subclass features
            val subclassId = char.subclassId ?: continue
            val subclass = repository.getSubclass(subclassId) ?: continue
            if (subclass.class_id != classId) continue
            for (featureId in subclass.features) {
                val feature = repository.getFeature(featureId) ?: continue
                val featureLevel = feature.level ?: continue
                if (featureLevel > levelInClass) continue
                // Always-prepared subclass spells keyed by level thresholds
                for ((requiredLevel, spells) in feature.always_prepared) {
                    if (levelInClass < requiredLevel.toIntOrNull() ?: continue) continue
                    for (spell in spells) {
                        addInnate(spellAbility, spell, classId)
                        addAlwaysPrepared(spellAbility, spell, classId)
                    }
                }
            }
        }

        return Pair(innateSpells.mapValues { it.value.toList() }, alwaysPrepared.mapValues { it.value.toList() })
    }

    /** Бонусы к характеристикам от черт с choice "feat_asi" для указанных родительских ключей. */
    fun computeFeatAsiBonuses(char: CharacterData, parentKeys: Set<String>): Map<String, Int> {
        val bonuses = mutableMapOf<String, Int>()
        for (parentKey in parentKeys) {
            val featId = char.featureChoices[parentKey] ?: continue
            val feat = repository.getFeat(featId) ?: continue
            val choice = feat.choice ?: continue
            if (choice.type != "feat_asi") continue
            val ability = char.featureChoices["featcard_${parentKey}_asi"] ?: choice.abilities.singleOrNull() ?: continue
            bonuses[ability] = (bonuses[ability] ?: 0) + 1
        }
        return bonuses
    }

    /** Родительские ключи featureChoices, соответствующие взятым чертам (не служебные суффиксы). */
    fun featParentKeys(choices: Map<String, String?>): Set<String> =
        choices.keys.filter {
            !it.startsWith("featcard_") && !it.endsWith("_asi") &&
                !it.endsWith("_list") && !it.endsWith("_ability")
        }.toSet()

    fun applyFeatAsiToScores(scores: Map<String, Int>, bonuses: Map<String, Int>): Map<String, Int> {
        if (bonuses.isEmpty()) return scores
        return scores.toMutableMap().apply {
            for ((ability, amount) in bonuses) this[ability] = (this[ability] ?: 10) + amount
        }
    }

    /** Сумма значений эффекта указанного типа от всех черт персонажа. */
    fun featEffectTotal(char: CharacterData, type: String): Int {
        var total = 0
        for (featId in char.feats) {
            val feat = repository.getFeat(featId) ?: continue
            for (effect in feat.effects) if (effect.type == type) total += effect.value
        }
        return total
    }

    fun hasFeatEffect(char: CharacterData, type: String): Boolean {
        for (featId in char.feats) {
            val feat = repository.getFeat(featId) ?: continue
            if (feat.effects.any { it.type == type }) return true
        }
        return false
    }

    fun charHasFeat(char: CharacterData, featLocalId: String): Boolean =
        char.feats.any { it.substringAfterLast(":") == featLocalId }

    /**
     * Бонус ХП от черт при получении уровня: hp_per_level у уже имеющихся черт даёт +value за уровень,
     * у newly obtained — value*newLevel (ретроактивно) + max_hp_flat.
     */
    fun computeLevelUpFeatHpBonus(oldFeats: List<String>, featsAfter: List<String>, newTotalLevel: Int): Int {
        var bonus = 0
        for (featId in oldFeats) {
            val feat = repository.getFeat(featId) ?: continue
            for (e in feat.effects) if (e.type == "hp_per_level") bonus += e.value
        }
        val newly = featsAfter.filter { it !in oldFeats }
        for (featId in newly) {
            val feat = repository.getFeat(featId) ?: continue
            for (e in feat.effects) {
                if (e.type == "hp_per_level") bonus += e.value * newTotalLevel
                if (e.type == "max_hp_flat") bonus += e.value
            }
        }
        return bonus
    }

    /** Заклинания от черт («Посвящённый в магию», Mark of * и т.п.): характеристика → записи "fullId|featFullId". */
    fun buildFeatInnateSpells(char: CharacterData): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        for (parent in featParentKeys(char.featureChoices)) {
            val featFullId = char.featureChoices[parent] ?: continue
            val feat = repository.getFeat(featFullId) ?: continue
            val choice = feat.choice ?: continue
            val cardKey = "featcard_$parent"
            when (choice.type) {
                "magic_initiate" -> {
                    val ability = char.featureChoices["${cardKey}_ability"] ?: continue
                    val spells = char.featureMultiChoices[cardKey] ?: continue
                    val list = result.getOrPut(ability) { mutableListOf() }
                    for (spell in spells) {
                        val entry = spellEntry(spell, featFullId)
                        if (entry !in list) list.add(entry)
                    }
                }
                "feat_spells" -> {
                    val ability = char.featureChoices["${cardKey}_ability"] ?: choice.abilities.singleOrNull() ?: continue
                    val spells = mutableListOf<String>()
                    spells.addAll(choice.fixed_spells)
                    for ((lvlStr, lvlSpells) in choice.char_level_spells) {
                        if (char.level >= (lvlStr.toIntOrNull() ?: Int.MAX_VALUE)) spells.addAll(lvlSpells)
                    }
                    val list = result.getOrPut(ability) { mutableListOf() }
                    for (spell in spells) {
                        val entry = spellEntry(spell, featFullId)
                        if (entry !in list) list.add(entry)
                    }
                }
            }
        }
        return result.mapValues { it.value.toList() }
    }

    /** Пересобирает заклинания черт в листе: убирает старые записи от черт и добавляет актуальные. */
    fun rebuildFeatInnateSpells(char: CharacterData): CharacterData {
        val sp = char.spells ?: CharacterSpells()
        val allFeatIds = repository.getFeatIds().toSet()
        val innateMap = sp.innateSpells.mapValues { (_, entries) ->
            entries.filter { it.spellSource() !in allFeatIds }.toMutableList()
        }.toMutableMap()
        val featInnate = buildFeatInnateSpells(char)
        for ((ability, entries) in featInnate) {
            val list = innateMap.getOrPut(ability) { mutableListOf() }
            for (entry in entries) if (entry !in list) list.add(entry)
        }
        return char.copy(spells = sp.copy(innateSpells = innateMap.mapValues { it.value.toList() }))
    }

    // ── Spellbook (Wizard) ──

    /** Класс, дающий книгу заклинаний (умение с выбором "wizard_spells"). */
    fun getSpellbookClassId(char: CharacterData): String? {
        val allClassIds = (char.classLevels.keys + char.classId).distinct()
        for (classId in allClassIds) {
            val cls = getClassInfo(classId) ?: continue
            for (featureId in cls.features) {
                val feature = repository.getFeature(featureId) ?: continue
                if (feature.choice?.type == "wizard_spells") return classId
            }
        }
        return null
    }

    fun hasSpellbook(char: CharacterData): Boolean = getSpellbookClassId(char) != null

    /** Собирает книгу заклинаний из выборов умения "wizard_spells" (заклинания 1+ уровня). */
    fun buildWizardSpellbook(char: CharacterData): List<String> {
        val book = mutableListOf<String>()
        val allClassIds = (char.classLevels.keys + char.classId).distinct()
        for (classId in allClassIds) {
            val cls = getClassInfo(classId) ?: continue
            for (featureId in cls.features) {
                val feature = repository.getFeature(featureId) ?: continue
                if (feature.choice?.type != "wizard_spells") continue
                val selected = char.featureMultiChoices[feature.id] ?: continue
                for (spell in selected) {
                    if ((repository.getSpell(spell)?.level ?: 0) < 1) continue
                    val entry = spellEntry(spell, classId)
                    if (entry !in book) book.add(entry)
                }
            }
        }
        return book
    }

    /** Лимит подготовленных заклинаний из книги — столбец "prepared" таблицы класса. */
    fun getSpellbookPreparedLimit(char: CharacterData): Int {
        val classId = getSpellbookClassId(char) ?: return 0
        val cls = getClassInfo(classId) ?: return 0
        val level = char.classLevels[classId] ?: if (classId == char.classId) char.level else 0
        val row = cls.class_table?.rows?.find { it.level == level } ?: return 0
        return row.values["prepared"]?.toIntOrNull() ?: 0
    }

    /** Максимальный уровень заклинаний, доступный по ячейкам (столбцы slot1..slot9 таблицы). */
    fun getMaxSpellSlotLevel(classId: String, level: Int): Int {
        val cls = getClassInfo(classId) ?: return 0
        val row = cls.class_table?.rows?.find { it.level == level } ?: return 0
        var max = 0
        for (n in 1..9) {
            val v = row.values["slot$n"] ?: continue
            if (v != "-" && (v.toIntOrNull() ?: 0) > 0) max = n
        }
        return max
    }

    // ── Формулы листа от умений (КЗ без доспеха, скорость, инициатива) ──

    /** Умения всех классов и подкласса, доступные на текущих уровнях (класс, уровень в классе, умение). */
    fun grantedFeatures(char: CharacterData): List<Triple<com.herocraft24.core.model.GameClass, Int, com.herocraft24.core.model.Feature>> {
        val result = mutableListOf<Triple<com.herocraft24.core.model.GameClass, Int, com.herocraft24.core.model.Feature>>()
        val classIds = (char.classLevels.keys + char.classId).distinct()
        for (classId in classIds) {
            val cls = getClassInfo(classId) ?: continue
            val classLevel = char.classLevels[classId] ?: if (classId == char.classId) char.level else 0
            val featureIds = cls.features.toMutableList()
            val subclass = char.subclassId?.let { repository.getSubclass(it) }
            if (subclass != null && subclass.class_id == classId) featureIds.addAll(subclass.features)
            for (fid in featureIds) {
                val f = repository.getFeature(fid) ?: continue
                if ((f.level ?: 1) > classLevel) continue
                result.add(Triple(cls, classLevel, f))
            }
        }
        return result
    }

    fun getUnarmoredAcFormulas(char: CharacterData): List<com.herocraft24.core.model.AcFormula> =
        grantedFeatures(char).mapNotNull { (_, _, f) -> f.ac_formula }

    fun computeFeatureSpeedBonus(char: CharacterData): Int {
        val hasArmor = char.equippedArmor != null
        val hasShield = char.equippedShield != null
        val heavyArmor = char.equippedArmor?.let { composite ->
            getItem(composite.substringBefore("|"))?.subcategory?.contains("heavy_armor") == true
        } ?: false
        var total = 0
        for ((cls, classLevel, f) in grantedFeatures(char)) {
            val sb = f.speed_bonus ?: continue
            val ok = when (sb.requires) {
                "no_armor" -> !hasArmor
                "no_armor_no_shield" -> !hasArmor && !hasShield
                "no_heavy_armor" -> !heavyArmor
                else -> true
            }
            if (!ok) continue
            var value = sb.value
            if (sb.table_key != null) {
                val row = cls.class_table?.rows?.find { it.level == classLevel }
                val raw = row?.values?.get(sb.table_key) ?: ""
                value += raw.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
            total += value
        }
        return total
    }

    fun computeFeatureInitiativeBonus(char: CharacterData, scores: Map<String, Int>): Int {
        var bonus = 0
        for ((_, _, f) in grantedFeatures(char)) {
            f.initiative_ability?.let { bonus += modifier(scores[it] ?: 10) }
        }
        return bonus
    }

    fun addSpellToSpellbook(charId: String, spellFullId: String) {
        val char = getCharacter(charId) ?: return
        val classId = getSpellbookClassId(char) ?: return
        val sp = char.spells ?: CharacterSpells()
        val entry = spellEntry(spellFullId, classId)
        if (entry in sp.spellbook) return
        saveCharacter(char.copy(spells = sp.copy(spellbook = sp.spellbook + entry)))
    }

    /** Подготовить/снять подготовку заклинания из книги (в пределах лимита по таблице класса). */
    fun toggleBookSpellPrepared(charId: String, entry: String) {
        val char = getCharacter(charId) ?: return
        val sp = char.spells ?: return
        val classId = getSpellbookClassId(char) ?: return
        val ability = getClassInfo(classId)?.spellcasting?.ability ?: return
        val prepared = sp.preparedByAbility[ability] ?: emptyList()
        val updated = if (entry in prepared) {
            prepared - entry
        } else {
            val limit = getSpellbookPreparedLimit(char)
            val bookPreparedCount = prepared.count { it.spellSource() == classId }
            if (bookPreparedCount >= limit) return
            prepared + entry
        }
        val newMap = sp.preparedByAbility.toMutableMap().apply { this[ability] = updated }
        saveCharacter(char.copy(spells = sp.copy(preparedByAbility = newMap)))
    }

    /** Левелап волшебника: заговоры — в подготовленные, заклинания — в книгу (без замен). */
    fun applyWizardLevelUpSpells(
        char: CharacterData,
        classId: String,
        newCantrips: List<String>,
        newBookSpells: List<String>
    ): CharacterData {
        val cls = getClassInfo(classId) ?: return char
        val ability = cls.spellcasting?.ability ?: return char
        val sp = char.spells ?: CharacterSpells()
        val innateMap = sp.innateSpells.toMutableMap()
        val abilityList = innateMap.getOrPut(ability) { mutableListOf() }.toMutableList()
        for (cantrip in newCantrips) {
            val entry = spellEntry(cantrip, classId)
            if (entry !in abilityList) abilityList.add(entry)
        }
        innateMap[ability] = abilityList
        val book = sp.spellbook.toMutableList()
        for (spell in newBookSpells) {
            val entry = spellEntry(spell, classId)
            if (entry !in book) book.add(entry)
        }
        return char.copy(spells = sp.copy(innateSpells = innateMap, spellbook = book))
    }

    fun addClassFeatureSpellsAtLevel(char: CharacterData, classId: String, newClassLevel: Int): CharacterData {
        val cls = getClassInfo(classId) ?: return char
        val spellAbility = cls.spellcasting?.ability ?: return char
        val sp = char.spells ?: CharacterSpells()
        val innateMap = sp.innateSpells.toMutableMap()
        val alwaysPreparedMap = sp.alwaysPreparedSpells.toMutableMap()
        val spellList = innateMap.getOrPut(spellAbility) { mutableListOf() }.toMutableList()
        val alwaysPreparedList = alwaysPreparedMap.getOrPut(spellAbility) { mutableListOf() }.toMutableList()

        fun addSpell(spell: String) {
            val entry = spellEntry(spell, classId)
            if (entry !in spellList) spellList.add(entry)
        }

        for (featureId in cls.features) {
            val feature = repository.getFeature(featureId) ?: continue
            val featureLevel = feature.level ?: continue
            if (featureLevel > newClassLevel) continue
            feature.spell?.let { spell ->
                if (featureLevel == newClassLevel) addSpell(spell)
            }
            for ((requiredLevel, spells) in feature.always_prepared) {
                if (newClassLevel < requiredLevel.toIntOrNull() ?: continue) continue
                for (spell in spells) {
                    addSpell(spell)
                    val entry = spellEntry(spell, classId)
                    if (entry !in alwaysPreparedList) alwaysPreparedList.add(entry)
                }
            }
            // Class spell choices (e.g. Sorcerer Spellcasting, Wizard Spellcasting)
            val choiceType = feature.choice?.type
            if (choiceType == "class_spells" || choiceType == "wizard_spells") {
                val selected = char.featureMultiChoices[feature.id] ?: emptyList()
                for (spell in selected) {
                    if (choiceType == "wizard_spells" && (repository.getSpell(spell)?.level ?: 0) > 0) continue
                    addSpell(spell)
                }
            }
        }

        // Also process subclass features gained at this level
        val subclassId = char.subclassId
        if (subclassId != null) {
            val subclass = repository.getSubclass(subclassId) ?: return char
            if (subclass.class_id == classId) {
                for (featureId in subclass.features) {
                    val feature = repository.getFeature(featureId) ?: continue
                    val featureLevel = feature.level ?: continue
                    if (featureLevel > newClassLevel) continue
                    feature.spell?.let { spell ->
                        if (featureLevel == newClassLevel) addSpell(spell)
                    }
                    for ((requiredLevel, spells) in feature.always_prepared) {
                        if (newClassLevel < requiredLevel.toIntOrNull() ?: continue) continue
                        for (spell in spells) {
                            addSpell(spell)
                            val entry = spellEntry(spell, classId)
                            if (entry !in alwaysPreparedList) alwaysPreparedList.add(entry)
                        }
                    }
                }
            }
        }

        innateMap[spellAbility] = spellList
        alwaysPreparedMap[spellAbility] = alwaysPreparedList
        return char.copy(spells = sp.copy(
            innateSpells = innateMap,
            alwaysPreparedSpells = alwaysPreparedMap
        ))
    }

    data class LevelUpSpellGain(val cantrips: Int, val spells: Int)

    fun getClassLevelSpellGain(classId: String, previousLevel: Int, newLevel: Int): LevelUpSpellGain {
        val cls = getClassInfo(classId) ?: return LevelUpSpellGain(0, 0)
        val rows = cls.class_table?.rows ?: return LevelUpSpellGain(0, 0)
        val previousRow = rows.find { it.level == previousLevel }
        val newRow = rows.find { it.level == newLevel }
        if (previousRow == null || newRow == null) return LevelUpSpellGain(0, 0)
        fun parseCantrips(row: ClassTableRow): Int = row.values["cantrips"]?.toIntOrNull() ?: 0
        fun parsePrepared(row: ClassTableRow): Int = row.values["prepared"]?.toIntOrNull() ?: 0
        return LevelUpSpellGain(
            cantrips = parseCantrips(newRow) - parseCantrips(previousRow),
            spells = parsePrepared(newRow) - parsePrepared(previousRow)
        )
    }

    fun getClassLevelInvocationGain(classId: String, previousLevel: Int, newLevel: Int): Int {
        val cls = getClassInfo(classId) ?: return 0
        val rows = cls.class_table?.rows ?: return 0
        val previousRow = rows.find { it.level == previousLevel }
        val newRow = rows.find { it.level == newLevel }
        if (previousRow == null || newRow == null) return 0
        fun parseInvocations(row: ClassTableRow): Int = row.values["invocations"]?.toIntOrNull() ?: 0
        return parseInvocations(newRow) - parseInvocations(previousRow)
    }

    fun getAvailableInvocations(classId: String, warlockLevel: Int): List<String> {
        val cls = getClassInfo(classId) ?: return emptyList()
        return cls.invocations.filter { invocationId ->
            val invocation = repository.getInvocation(invocationId) ?: return@filter false
            val required = invocation.requirements?.warlock_level
            required == null || required <= warlockLevel
        }
    }

    fun applySorcererLevelUpSpells(
        char: CharacterData,
        classId: String,
        removeCantripEntry: String?,
        removeSpellEntry: String?,
        newCantrips: List<String>,
        newSpells: List<String>
    ): CharacterData {
        val cls = getClassInfo(classId) ?: return char
        val ability = cls.spellcasting?.ability ?: return char
        val sp = char.spells ?: CharacterSpells()
        val innateMap = sp.innateSpells.toMutableMap()
        val abilityList = innateMap.getOrPut(ability) { mutableListOf() }.toMutableList()

        fun removeIfPresent(entryId: String?) {
            entryId ?: return
            abilityList.removeAll { it == entryId }
        }

        fun addIfAbsent(spellId: String) {
            val entry = spellEntry(spellId, classId)
            if (entry !in abilityList) abilityList.add(entry)
        }

        val alwaysPreparedFullIds = sp.alwaysPreparedSpells[ability]
            ?.map { it.spellFullId() }?.toSet() ?: emptySet()
        if (removeCantripEntry != null && removeCantripEntry.spellFullId() !in alwaysPreparedFullIds) removeIfPresent(removeCantripEntry)
        if (removeSpellEntry != null && removeSpellEntry.spellFullId() !in alwaysPreparedFullIds) removeIfPresent(removeSpellEntry)
        newCantrips.forEach(::addIfAbsent)
        newSpells.forEach(::addIfAbsent)

        innateMap[ability] = abilityList
        return char.copy(spells = sp.copy(innateSpells = innateMap))
    }

    companion object {
        fun abilityForSkill(skill: String) = when (skill) {
            "athletics" -> "strength"
            "acrobatics", "sleight_of_hand", "stealth" -> "dexterity"
            "arcana", "history", "investigation", "nature", "religion" -> "intelligence"
            "animal_handling", "insight", "medicine", "perception", "survival" -> "wisdom"
            "deception", "intimidation", "performance", "persuasion" -> "charisma"
            else -> "strength"
        }
    }
}