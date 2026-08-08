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
        "ep" -> "ЭМ"
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

    // ─── Reference-detail-only localizers ───────────────────────────

    fun sense(sense: String): String = when (sense.lowercase()) {
        "darkvision" -> "Тёмное зрение"
        "blindsight" -> "Слепое зрение"
        "tremorsense" -> "Вибрационное чутьё"
        "truesight" -> "Истинное зрение"
        "passive_perception" -> "пассивное Восприятие"
        else -> sense.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun condition(condition: String): String = when (condition.lowercase()) {
        "blinded" -> "Ослеплённый"
        "charmed" -> "Очарованный"
        "deafened" -> "Оглохший"
        "frightened" -> "Испуганный"
        "grappled" -> "Схваченный"
        "incapacitated" -> "Недееспособный"
        "invisible" -> "Невидимый"
        "paralyzed" -> "Парализованный"
        "petrified" -> "Окаменевший"
        "poisoned" -> "Отравленный"
        "prone" -> "Опрокинутый"
        "restrained" -> "Опутанный"
        "stunned" -> "Ошеломлённый"
        "unconscious" -> "Без сознания"
        "exhaustion" -> "Истощение"
        else -> condition.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun alignment(alignment: String): String {
        val parts = alignment.trim().lowercase().split(" ")
        val lawAxis = when (parts.firstOrNull()) {
            "lawful" -> "Законно"
            "neutral" -> "Нейтрально"
            "chaotic" -> "Хаотично"
            else -> null
        }
        val goodAxis = when (parts.getOrNull(1) ?: "") {
            "good" -> "доброе"
            "evil" -> "злое"
            "neutral" -> "нейтральное"
            else -> null
        }
        if (parts.size == 1) {
            return when (parts[0]) {
                "neutral" -> "Нейтральное"
                "lawful" -> "Законноправное"
                "chaotic" -> "Хаотичное"
                "good" -> "Доброе"
                "evil" -> "Злое"
                "any" -> "Любое"
                "unaligned" -> "Без мировоззрения"
                else -> alignment.trim().replaceFirstChar { it.uppercase() }
            }
        }
        return when {
            lawAxis != null && goodAxis != null -> "$lawAxis-$goodAxis"
            else -> alignment.trim().replaceFirstChar { it.uppercase() }
        }
    }

    fun environment(environment: String): String = when (environment.lowercase()) {
        "any" -> "Любая"
        "arctic" -> "Арктика"
        "coastal" -> "Прибрежье"
        "desert" -> "Пустыня"
        "forest" -> "Леса"
        "grassland" -> "Луга"
        "hills", "hill" -> "Холмы"
        "mountains", "mountain" -> "Горы"
        "underdark" -> "Подземье"
        "swamp" -> "Болото"
        "underwater" -> "Под водой"
        "urban" -> "Город"
        "astral plane" -> "Астральный план"
        "upper planes" -> "Верхние планы"
        "lower planes" -> "Нижние планы"
        "acheron" -> "Ахерон"
        "abyss" -> "Бездна"
        "gehenna" -> "Геенна"
        "nine hells" -> "Девять преисподних"
        "beastlands" -> "Звериные земли"
        "limbo" -> "Лимбо"
        "mechanus" -> "Механус"
        "elemental water" -> "Стихийный план воды"
        "elemental fire" -> "Стихийный план огня"
        "elemental earth" -> "Стихийный план земли"
        "elemental air" -> "Стихийный план воздуха"
        "elemental planes" -> "Стихийные планы"
        "elemental chaos" -> "Стихийный хаос"
        "feywild" -> "Страна фей"
        "shadowfell" -> "Царство теней"
        "ethereal plane" -> "Эфирный план"
        "underground" -> "Подземелье"
        else -> environment.replaceFirstChar { it.uppercase() }
    }

    fun size(size: String): String = when (size.lowercase()) {
        "small" -> "Маленький"
        "medium" -> "Средний"
        "large" -> "Большой"
        "huge" -> "Огромный"
        "gargantuan" -> "Громадный"
        "tiny" -> "Крошечный"
        else -> size.replaceFirstChar { it.uppercase() }
    }

    fun monsterSizeDetail(size: String): String = when (size.lowercase()) {
        "tiny" -> "Крошечный рой"
        "small" -> "Маленький рой"
        "medium" -> "Средний рой"
        "large" -> "Большой рой"
        "huge" -> "Огромный рой"
        "gargantuan" -> "Громадный рой"
        else -> "Рой"
    }

    fun type(type: String): String = when (type.lowercase()) {
        "humanoid" -> "Гуманоид"
        "fey" -> "Фея"
        "fiend" -> "Исчадие"
        "undead" -> "Нежить"
        "monstrosity" -> "Чудовище"
        "aberration" -> "Аберрация"
        "celestial" -> "Небожитель"
        "elemental" -> "Элементаль"
        "construct" -> "Конструкт"
        "dragon" -> "Дракон"
        "giant" -> "Великан"
        "ooze" -> "Желе"
        "plant" -> "Растение"
        "beast" -> "Зверь"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    fun skill(skill: String): String = when (skill.lowercase().replace(" ", "_")) {
        "acrobatics" -> "Акробатика"
        "animal_handling" -> "Уход за животными"
        "arcana" -> "Магия"
        "athletics" -> "Атлетика"
        "deception" -> "Обман"
        "history" -> "История"
        "insight" -> "Проницательность"
        "intimidation" -> "Запугивание"
        "investigation" -> "Расследование"
        "medicine" -> "Медицина"
        "nature" -> "Природа"
        "perception" -> "Восприятие"
        "performance" -> "Выступление"
        "persuasion" -> "Убеждение"
        "religion" -> "Религия"
        "sleight_of_hand" -> "Ловкость рук"
        "stealth" -> "Скрытность"
        "survival" -> "Выживание"
        else -> skill.replaceFirstChar { it.uppercase() }
    }
}