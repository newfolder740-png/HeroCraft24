package com.herocraft24.core.model

import kotlinx.serialization.Serializable

// ─── Class ───────────────────────────────────────────────────────────────────

@Serializable
data class GameClass(
    val id: String,
    val type: String = "class",
    val format_version: Int = 1,
    val name: LocalizedString,
    val short_description: LocalizedString? = null,
    val description: LocalizedString,
    val source: SourceInfo,
    val tags: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val image: ImageInfo? = null,
    val hit_die: Int,
    val primary_ability: String,
    val saving_throws: List<String>,
    val skills: SkillChoice? = null,
    val starting_proficiencies: Proficiencies? = null,
    val starting_equipment: List<EquipmentChoice> = emptyList(),
    val subclass_title: LocalizedString? = null,
    val subclass_level: Int? = null,
    val features: List<String> = emptyList(),
    val subclasses: List<String> = emptyList(),
    val class_table: ClassTable? = null,
    val acquisition: ClassAcquisition? = null,
    val key_attributes: Map<String, String> = emptyMap(),
    val spellcasting: SpellcastingInfo? = null,
    val multiclass_requirements: MulticlassRequirements? = null,
    val multiclass_proficiencies: Proficiencies? = null,
    val invocations: List<String> = emptyList(),
    val metamagics: List<String> = emptyList(),
    val wild_magic: List<WildMagicEntry> = emptyList()
)

@Serializable
data class ClassAcquisition(
    val first_class: LocalizedString,
    val multiclass: LocalizedString
)

@Serializable
data class Subclass(
    val id: String,
    val type: String = "subclass",
    val format_version: Int = 1,
    val name: LocalizedString,
    val description: LocalizedString,
    val source: SourceInfo,
    val class_id: String,
    val features: List<String> = emptyList()
)

@Serializable
data class Feature(
    val id: String,
    val type: String = "feature",
    val format_version: Int = 1,
    val name: LocalizedString,
    val description: LocalizedString,
    val level: Int? = null,
    val origin: String? = null,
    val requirements: LocalizedString? = null,
    val is_subclass_choice: Boolean = false,
    val is_placeholder: Boolean = false,
    val references: List<Reference> = emptyList()
)

@Serializable
data class SkillChoice(
    val count: Int,
    val from: List<String>
)

@Serializable
data class Proficiencies(
    val armor: List<String> = emptyList(),
    val weapons: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val saving_throws: List<String> = emptyList(),
    val skills: List<String> = emptyList()
)

@Serializable
data class EquipmentChoice(
    val description: LocalizedString? = null,
    val count: Int = 1,
    val options: List<EquipmentOption> = emptyList(),
    val default: String? = null
)

@Serializable
data class EquipmentOption(
    val item_id: String? = null,
    val description: LocalizedString? = null,
    val quantity: Int = 1,
    val options: List<EquipmentOption> = emptyList(),
    val gold: Int? = null,
    val items: List<EquipmentOption> = emptyList()
)

@Serializable
data class SpellcastingInfo(
    val ability: String,
    val type: String,
    val spell_list: String? = null,
    val cantrips_known: List<ProgressionValue> = emptyList(),
    val spells_known: List<ProgressionValue>? = null,
    val spell_slots: SpellSlotsTable? = null
)

@Serializable
data class ProgressionValue(
    val level: Int,
    val count: Int
)

@Serializable
data class SpellSlotsTable(
    val full_caster: Boolean = true,
    val pact_magic: Boolean = false,
    val slots: List<SlotRow> = emptyList()
)

@Serializable
data class SlotRow(
    val level: Int,
    val slots: Map<String, Int>
)

@Serializable
data class ClassTable(
    val columns: List<ClassTableColumn> = emptyList(),
    val rows: List<ClassTableRow> = emptyList()
)

@Serializable
data class ClassTableColumn(
    val key: String,
    val name: LocalizedString
)

@Serializable
data class ClassTableRow(
    val level: Int,
    val values: Map<String, String>
)

@Serializable
data class MulticlassRequirements(
    val ability_scores: Map<String, Int> = emptyMap()
)

@Serializable
data class Invocation(
    val id: String,
    val name: LocalizedString,
    val description: LocalizedString,
    val level: Int? = null,
    val requirements: InvocationRequirements? = null
)

@Serializable
data class InvocationRequirements(
    val warlock_level: Int? = null,
    val invocation_id: String? = null
)

@Serializable
data class Metamagic(
    val id: String,
    val name: LocalizedString,
    val description: LocalizedString,
    val cost: String
)

@Serializable
data class WildMagicEntry(
    val range: String,
    val description: LocalizedString
)
