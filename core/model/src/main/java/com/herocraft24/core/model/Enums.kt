package com.herocraft24.core.model

/**
 * UI-facing enums for fixed-value string fields.
 *
 * These enums are NOT used for JSON serialization; they are built from the raw
 * string values already stored in the model objects. Each enum provides a
 * `fromValue(String)` factory with a fallback to an `UNKNOWN` value, so UI code
 * stays safe even when new/unexpected values appear in the data.
 */

enum class SpellSchool(val raw: String) {
    ABJURATION("abjuration"),
    CONJURATION("conjuration"),
    DIVINATION("divination"),
    ENCHANTMENT("enchantment"),
    EVOCATION("evocation"),
    ILLUSION("illusion"),
    NECROMANCY("necromancy"),
    TRANSMUTATION("transmutation"),
    UNKNOWN("");

    companion object {
        fun fromValue(value: String?): SpellSchool =
            entries.find { it.raw.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ItemCategory(val raw: String) {
    WEAPON("weapon"),
    ARMOR("armor"),
    SHIELD("shield"),
    ADVENTURING_GEAR("adventuring_gear"),
    PACK("pack"),
    TOOL("tool"),
    INSTRUMENT("instrument"),
    FOCUS("focus"),
    WAND("wand"),
    ROD("rod"),
    POTION("potion"),
    RING("ring"),
    STAFF("staff"),
    SCROLL("scroll"),
    WONDROUS_ITEM("wondrous_item"),
    AMMUNITION("ammunition"),
    GEAR("gear"),
    UNKNOWN("");

    companion object {
        fun fromValue(value: String?): ItemCategory =
            entries.find { it.raw.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ItemRarity(val raw: String) {
    NON_MAGIC("non-magic"),
    COMMON("common"),
    UNCOMMON("uncommon"),
    RARE("rare"),
    VERY_RARE("very-rare"),
    VERY_RARE_ALT("veryrare"),
    LEGENDARY("legendary"),
    ARTIFACT("artifact"),
    VARIES("varies"),
    UNKNOWN("");

    companion object {
        fun fromValue(value: String?): ItemRarity =
            entries.find { it.raw.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class CreatureSize(val raw: String) {
    TINY("tiny"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    HUGE("huge"),
    GARGANTUAN("gargantuan"),
    UNKNOWN("");

    companion object {
        fun fromValue(value: String?): CreatureSize =
            entries.find { it.raw.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class CreatureType(val raw: String) {
    ABERRATION("aberration"),
    BEAST("beast"),
    CELESTIAL("celestial"),
    CONSTRUCT("construct"),
    DRAGON("dragon"),
    ELEMENTAL("elemental"),
    FEY("fey"),
    FIEND("fiend"),
    GIANT("giant"),
    HUMANOID("humanoid"),
    MONSTROSITY("monstrosity"),
    OOZE("ooze"),
    PLANT("plant"),
    UNDEAD("undead"),
    UNKNOWN("");

    companion object {
        fun fromValue(value: String?): CreatureType =
            entries.find { it.raw.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
