package com.herocraft24.core.ui.local

/**
 * Consolidated, context-free localisers shared across feature modules.
 * Keeps the school/category/rarity/damage/property/ability maps in a single place
 * instead of duplicating them in every detail/list fragment.
 */
object UiLocalizer {

    // ─── Schools (люби Meyer) ────────────────────────────────────────
    fun school(value: String): String = when (value.lowercase()) {
        "abjuration" -> "Ограждение"
        "conjuration" -> "Вызов"
        "divination" -> "Прорицание"
        "enchantment" -> "Очарование"
        "evocation" -> "Воплощение"
        "illusion" -> "Иллюзия"
        "necromancy" -> "Некромантия"
        "transmutation" -> "Преобразование"
        else -> value.replaceFirstChar { it.uppercase() }
    }

    // ─── Item categories ────────────────────────────────────────────
    fun category(value: String?): String = when (value) {
        "weapon" -> "Оружие"
        "armor" -> "Доспех"
        "shield" -> "Щит"
        "adventuring_gear" -> "Снаряжение приключений"
        "pack" -> "Набор"
        "tool" -> "Ремесленный инструмент"
        "instrument" -> "Инструмент"
        "focus" -> "Фокусировка"
        "wand" -> "Волшебная палочка"
        "rod" -> "Жезл"
        "potion" -> "Зелье"
        "ring" -> "Кольцо"
        "staff" -> "Посох"
        "scroll" -> "Свиток"
        "wondrous_item" -> "Чудесная вещь"
        "ammunition" -> "Боеприпасы"
        "gear" -> "Снаряжение"
        else -> value?.replaceFirstChar { it.uppercase() } ?: ""
    }

    // ─── Item subcategories ─────────────────────────────────────────
    fun subcategory(value: String): String = when (value.lowercase()) {
        "simple_melee" -> "Простое рукопашное"
        "martial_melee" -> "Воинское рукопашное"
        "simple_ranged" -> "Простое дальнобойное"
        "martial_ranged" -> "Воинское дальнобойное"
        "ammunition" -> "Боеприпас"
        "light_armor" -> "Лёгкий"
        "medium_armor" -> "Средний"
        "heavy_armor" -> "Тяжёлый"
        "shield" -> "Щит"
        else -> value.replaceFirstChar { it.uppercase() }
    }

    // ─── Rarity ─────────────────────────────────────────────────────
    fun rarity(value: String?): String = when (value?.lowercase()) {
        null, "", "non-magic", "nonmagic" -> "Немагический"
        "common" -> "Обычный"
        "uncommon" -> "Необычный"
        "rare" -> "Редкий"
        "very-rare", "veryrare" -> "Очень редкий"
        "legendary" -> "Легендарный"
        "artifact" -> "Артефакт"
        "varies" -> "Варьируется"
        else -> value.replaceFirstChar { it.uppercase() }
    }

    // ─── Damage types (capitalised) ─────────────────────────────────
    fun damageType(type: String): String = when (type.lowercase()) {
        "bludgeoning" -> "Дробящий"
        "piercing" -> "Колющий"
        "slashing" -> "Рубящий"
        "acid" -> "Кислота"
        "cold" -> "Холод"
        "fire" -> "Огонь"
        "force" -> "Сила"
        "lightning" -> "Электричество"
        "necrotic" -> "Некротический"
        "poison" -> "Яд"
        "psychic" -> "Психический"
        "radiant" -> "Излучение"
        "thunder" -> "Звук"
        else -> type.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    // ─── Weapon/armour properties ───────────────────────────────────
    fun property(value: String): String = when (value.lowercase()) {
        "ammunition" -> "Амуниция"
        "finesse" -> "Фехтовальное"
        "heavy" -> "Тяжёлое"
        "light" -> "Лёгкое"
        "reach" -> "Длинное"
        "reload" -> "Перезарядка"
        "thrown" -> "Метательное"
        "two_handed" -> "Двуручное"
        "versatile" -> "Универсальное"
        else -> value.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    // ─── Abilities ──────────────────────────────────────────────────
    fun ability(value: String): String = when (value.lowercase()) {
        "strength" -> "Сила"
        "dexterity" -> "Ловкость"
        "constitution" -> "Телосложение"
        "intelligence" -> "Интеллект"
        "wisdom" -> "Мудрость"
        "charisma" -> "Харизма"
        else -> value.replaceFirstChar { it.uppercase() }
    }

    fun costUnit(unit: String): String = when (unit.lowercase()) {
        "gp" -> "ЗМ"
        "sp" -> "СМ"
        "cp" -> "ММ"
        "pp" -> "ПМ"
        else -> unit.uppercase()
    }

    fun weightUnit(unit: String): String = when (unit.lowercase()) {
        "lb" -> "фунт."
        "kg" -> "кг"
        "oz" -> "унц."
        else -> unit
    }
}