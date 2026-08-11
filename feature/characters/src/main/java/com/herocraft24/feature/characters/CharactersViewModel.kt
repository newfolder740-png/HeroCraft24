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
            repo.save(char.copy(hitPoints = HitPoints(max = hp, current = hp)))
            _wizardStep.value = 0
        }
    }

    suspend fun finishWizardSuspend() {
        val char = _wizard.value
        val hp = calculateStartingHP(char)
        repo.save(char.copy(hitPoints = HitPoints(max = hp, current = hp)))
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

    fun calculateStartingHP(char: CharacterData): Int {
        val cls = repository.getClass(char.classId) ?: return 10
        return cls.hit_die + modifier(char.abilityScores["constitution"] ?: 10)
    }

    fun getClassInfo(classId: String): GameClass? = repository.getClass(classId)
    fun resolveName(id: String): String? = repository.resolveName(id)
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