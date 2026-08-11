package com.herocraft24.feature.characters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.Background
import com.herocraft24.core.model.GameClass
import com.herocraft24.core.model.Species
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
            repo.save(char.copy(hitPoints = HitPoints(max = hp, current = hp), equipment = equipment))
            _wizardStep.value = 0
        }
    }

    suspend fun finishWizardSuspend() {
        val char = _wizard.value
        val hp = calculateStartingHP(char)
        val equipment = calculateStartingEquipment(char)
        repo.save(char.copy(hitPoints = HitPoints(max = hp, current = hp), equipment = equipment))
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
        val ability = getClassInfo(char.classId)?.spellcasting?.ability ?: "intelligence"
        return char.proficiencyBonus + modifier(char.abilityScores[ability] ?: 10)
    }
    fun spellDC(char: CharacterData): Int {
        val ability = getClassInfo(char.classId)?.spellcasting?.ability ?: "intelligence"
        return 8 + char.proficiencyBonus + modifier(char.abilityScores[ability] ?: 10)
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