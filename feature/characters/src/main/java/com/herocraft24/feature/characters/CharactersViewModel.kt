package com.herocraft24.feature.characters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Background
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
            val hp = calculateStartingHP(char)
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
            val charWithAsi = charWithLevels.copy(abilityScores = asiScores)

            val (speciesSpellAbility, speciesInnate) = buildSpeciesInnateSpells(charWithAsi, 1)
            val classInnate = buildClassFeatureInnateSpells(charWithAsi)
            val mergedInnate = mergeInnateSpells(speciesInnate, classInnate)
            val sp = char.spells ?: CharacterSpells()
            repo.save(charWithAsi.copy(
                hitPoints = HitPoints(max = hp, current = hp),
                equipment = equipment,
                speciesSpellAbility = speciesSpellAbility,
                spells = sp.copy(innateSpells = mergedInnate)
            ))
            _wizardStep.value = 0
        }
    }

    suspend fun finishWizardSuspend() {
        val char = _wizard.value
        val hp = calculateStartingHP(char)
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
        val charWithAsi = charWithLevels.copy(abilityScores = asiScores)

        val (speciesSpellAbility, speciesInnate) = buildSpeciesInnateSpells(charWithAsi, 1)
        val classInnate = buildClassFeatureInnateSpells(charWithAsi)
        val mergedInnate = mergeInnateSpells(speciesInnate, classInnate)
        val sp = char.spells ?: CharacterSpells()
        repo.save(charWithAsi.copy(
            hitPoints = HitPoints(max = hp, current = hp),
            equipment = equipment,
            speciesSpellAbility = speciesSpellAbility,
            spells = sp.copy(innateSpells = mergedInnate)
        ))
        _wizard.value = CharacterData()
        _wizardStep.value = 0
    }

    fun loadForEdit(id: String) { _editingCharacter.value = repo.getById(id) }
    fun clearEdit() { _editingCharacter.value = null }

    // Computed
    fun modifier(score: Int) = kotlin.math.floor((score - 10).toDouble() / 2).toInt()
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

    fun resolveResourceTotal(formula: String, char: CharacterData): Int {
        val chaMod = modifier(char.abilityScores["charisma"] ?: 10)
        val conMod = modifier(char.abilityScores["constitution"] ?: 10)
        val strMod = modifier(char.abilityScores["strength"] ?: 10)
        val dexMod = modifier(char.abilityScores["dexterity"] ?: 10)
        val intMod = modifier(char.abilityScores["intelligence"] ?: 10)
        val wisMod = modifier(char.abilityScores["wisdom"] ?: 10)
        val profBonus = char.proficiencyBonus
        return when {
            formula.startsWith("max(") -> {
                // Parse "max(charisma_modifier,1)" pattern
                val inner = formula.removePrefix("max(").removeSuffix(")")
                val parts = inner.split(",")
                val first = resolveResourcePart(parts[0].trim(), char)
                val second = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                kotlin.math.max(first, second)
            }
            else -> resolveResourcePart(formula, char)
        }
    }

    private fun resolveResourcePart(part: String, char: CharacterData): Int {
        val chaMod = modifier(char.abilityScores["charisma"] ?: 10)
        val conMod = modifier(char.abilityScores["constitution"] ?: 10)
        val strMod = modifier(char.abilityScores["strength"] ?: 10)
        val dexMod = modifier(char.abilityScores["dexterity"] ?: 10)
        val intMod = modifier(char.abilityScores["intelligence"] ?: 10)
        val wisMod = modifier(char.abilityScores["wisdom"] ?: 10)
        val profBonus = char.proficiencyBonus
        return when (part) {
            "charisma_modifier" -> chaMod
            "constitution_modifier" -> conMod
            "strength_modifier" -> strMod
            "dexterity_modifier" -> dexMod
            "intelligence_modifier" -> intMod
            "wisdom_modifier" -> wisMod
            "proficiency_bonus" -> profBonus
            else -> part.toIntOrNull() ?: 0
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
                    val total = resolveResourceTotal(feature.resource!!.count_formula, char)
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
                        val total = resolveResourceTotal(feature.resource!!.count_formula, char)
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

    fun addPreparedSpell(charId: String, spellId: String, ability: String) {
        val char = getCharacter(charId) ?: return
        val sp = char.spells ?: CharacterSpells()
        val current = sp.preparedByAbility[ability] ?: emptyList()
        if (spellId in current) return
        val updated = sp.copy(
            preparedByAbility = sp.preparedByAbility.toMutableMap().apply {
                this[ability] = current + spellId
            }
        )
        saveCharacter(char.copy(spells = updated))
    }

    fun removePreparedSpell(charId: String, spellId: String, ability: String) {
        val char = getCharacter(charId) ?: return
        val sp = char.spells ?: return
        val current = sp.preparedByAbility[ability] ?: return
        val updated = sp.copy(
            preparedByAbility = sp.preparedByAbility.toMutableMap().apply {
                this[ability] = current - spellId
            }
        )
        saveCharacter(char.copy(spells = updated))
    }

    fun getAllSpellSummaries(): List<SpellSummary> {
        val ids = repository.getSpellIds()
        return ids.mapNotNull { fullId ->
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
    }

    fun getPreparedSpellSummaries(char: CharacterData, ability: String): List<SpellSummary> {
        val sp = char.spells ?: return emptyList()
        val preparedIds = sp.preparedByAbility[ability] ?: emptyList()
        val innateIds = sp.innateSpells[ability] ?: emptyList()
        val spellIds = (innateIds + preparedIds).distinct()
        if (spellIds.isEmpty()) return emptyList()
        val allSummaries = getAllSpellSummaries()
        val byId = allSummaries.associateBy { it.fullId }
        return spellIds.mapNotNull { byId[it] }
    }

    fun getInnateSpellIds(char: CharacterData, ability: String): Set<String> {
        val sp = char.spells ?: return emptySet()
        return (sp.innateSpells[ability] ?: emptyList()).toSet()
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

    fun buildSpeciesInnateSpells(char: CharacterData, upToLevel: Int): Pair<String?, Map<String, List<String>>> {
        val speciesId = char.speciesId.substringAfterLast(":")
        val species = getSpeciesIds().mapNotNull { getSpeciesInfo(it) }.find { it.id == speciesId }
            ?: return null to emptyMap()
        val selectedSub = char.subspeciesId?.let { id -> species.subspecies?.find { it.id == id } }

        val effectiveTraits = mutableListOf<com.herocraft24.core.model.SpeciesTrait>()
        for (trait in species.traits) {
            if (trait.is_placeholder && selectedSub != null) {
                effectiveTraits.addAll(selectedSub.traits)
            } else {
                effectiveTraits.add(trait)
            }
        }

        // Find the spellcasting ability choice from featureChoices
        var speciesSpellAbility: String? = char.speciesSpellAbility
        for (trait in effectiveTraits) {
            if (trait.choice?.type == "spellcasting_ability") {
                val traitId = "trait_${species.id}_${trait.name.get()}"
                char.featureChoices[traitId]?.let { speciesSpellAbility = it }
            }
        }

        val innateSpells = mutableMapOf<String, MutableList<String>>()
        // Preserve existing innate spells
        char.spells?.innateSpells?.forEach { (ability, spells) ->
            innateSpells[ability] = spells.toMutableList()
        }

        val ability = speciesSpellAbility ?: return speciesSpellAbility to innateSpells.mapValues { it.value.toList() }
        for (trait in effectiveTraits) {
            val spell = trait.spell ?: continue
            val traitLevel = trait.level ?: continue
            if (traitLevel > upToLevel) continue
            val list = innateSpells.getOrPut(ability) { mutableListOf() }
            if (spell !in list) list.add(spell)
        }

        return speciesSpellAbility to innateSpells.mapValues { it.value.toList() }
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

        val ability = char.speciesSpellAbility ?: return char
        val sp = char.spells ?: CharacterSpells()
        val innateMap = sp.innateSpells.toMutableMap()
        val spellList = innateMap.getOrPut(ability) { mutableListOf() }.toMutableList()

        for (trait in effectiveTraits) {
            val spell = trait.spell ?: continue
            val traitLevel = trait.level ?: continue
            if (traitLevel != newLevel) continue
            if (spell !in spellList) spellList.add(spell)
        }

        innateMap[ability] = spellList
        return char.copy(spells = sp.copy(innateSpells = innateMap))
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

    fun buildClassFeatureInnateSpells(char: CharacterData): Map<String, List<String>> {
        val innateSpells = mutableMapOf<String, MutableList<String>>()
        char.spells?.innateSpells?.forEach { (ability, spells) ->
            innateSpells[ability] = spells.toMutableList()
        }

        val allClassIds = (char.classLevels.keys + char.classId).distinct()
        for (classId in allClassIds) {
            val cls = getClassInfo(classId) ?: continue
            val spellAbility = cls.spellcasting?.ability ?: continue
            val levelInClass = char.classLevels[classId] ?: if (classId == char.classId) char.level else 0

            for (featureId in cls.features) {
                val feature = repository.getFeature(featureId) ?: continue
                val spell = feature.spell ?: continue
                val featureLevel = feature.level ?: continue
                if (featureLevel > levelInClass) continue
                val list = innateSpells.getOrPut(spellAbility) { mutableListOf() }
                if (spell !in list) list.add(spell)
            }

            // Also check subclass features
            val subclassId = char.subclassId
            if (subclassId != null) {
                val subclass = cls.subclasses.find { it == subclassId || it.substringAfterLast(":") == subclassId }
                // Subclass features are not stored on GameClass.subclasses directly
                // They are stored as features with subclass-related IDs
            }
        }

        return innateSpells.mapValues { it.value.toList() }
    }

    fun addClassFeatureSpellsAtLevel(char: CharacterData, classId: String, newClassLevel: Int): CharacterData {
        val cls = getClassInfo(classId) ?: return char
        val spellAbility = cls.spellcasting?.ability ?: return char
        val sp = char.spells ?: CharacterSpells()
        val innateMap = sp.innateSpells.toMutableMap()
        val spellList = innateMap.getOrPut(spellAbility) { mutableListOf() }.toMutableList()

        for (featureId in cls.features) {
            val feature = repository.getFeature(featureId) ?: continue
            val spell = feature.spell ?: continue
            val featureLevel = feature.level ?: continue
            if (featureLevel != newClassLevel) continue
            if (spell !in spellList) spellList.add(spell)
        }

        innateMap[spellAbility] = spellList
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