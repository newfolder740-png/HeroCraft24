package com.herocraft24.feature.characters

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Расчёт уровня заклинателя и ячеек заклинаний по правилам мультиклассового заклинателя.
 *
 * Полные заклинатели (Бард, Волшебник, Жрец, Друид, Чародей) — все уровни.
 * Половинные заклинатели (Артефактор, Паладин, Следопыт) — половина уровней, округление вверх.
 * Воин/Плут с подклассом Мистический рыцарь / Мистический ловкач — треть уровней, округление вниз.
 */
object SpellSlotsCounter {

    private val fullCasters = setOf(
        "bard", "cleric", "druid", "sorcerer", "wizard"
    )

    private val halfCasters = setOf(
        "artificer", "paladin", "ranger"
    )

    /** Подклассы Воина и Плута, дающие умение «Сотворение заклинаний». */
    private val fighterSpellSubclasses = setOf(
        "fighter_mystical_knight", "fighter_eldritch_knight"
    )
    private val rogueSpellSubclasses = setOf(
        "rogue_misticheskiy_lovkach", "rogue_arcane_trickster"
    )

    /**
     * Таблица ячеек заклинаний мультиклассового заклинателя.
     * Ключ — уровень заклинателя (1..20), значение — карта уровня ячейки -> количество.
     * Уровни ячеек: 1..9. 0 означает отсутствие ячеек этого уровня.
     */
    private val multiclassSlotsTable: Map<Int, Map<Int, Int>> = mapOf(
        1 to mapOf(1 to 2),
        2 to mapOf(1 to 3),
        3 to mapOf(1 to 4, 2 to 2),
        4 to mapOf(1 to 4, 2 to 3),
        5 to mapOf(1 to 4, 2 to 3, 3 to 2),
        6 to mapOf(1 to 4, 2 to 3, 3 to 3),
        7 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 1),
        8 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 2),
        9 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 1),
        10 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2),
        11 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1),
        12 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1),
        13 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1, 7 to 1),
        14 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1, 7 to 1),
        15 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1, 7 to 1, 8 to 1),
        16 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1, 7 to 1, 8 to 1),
        17 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1, 7 to 1, 8 to 1, 9 to 1),
        18 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 1, 7 to 1, 8 to 1, 9 to 1),
        19 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 2, 7 to 1, 8 to 1, 9 to 1),
        20 to mapOf(1 to 4, 2 to 3, 3 to 3, 4 to 3, 5 to 2, 6 to 2, 7 to 2, 8 to 1, 9 to 1)
    )

    data class CasterInfo(
        val casterLevel: Int,
        val slots: Map<Int, Int>
    )

    /**
     * Рассчитывает уровень заклинателя и доступные ячейки.
     *
     * @param classId ID класса персонажа (без префикса пакета, напр. "wizard")
     * @param level уровень персонажа
     * @param subclassId ID подкласса (без префикса пакета), может быть null
     * @return [CasterInfo] с уровнем заклинателя и картой ячеек, или null если персонаж не заклинатель
     */
    fun compute(
        classId: String,
        level: Int,
        subclassId: String? = null
    ): CasterInfo? {
        val rawClassId = classId.substringAfterLast(":")
        val rawSubclassId = subclassId?.substringAfterLast(":")

        val casterLevel = when {
            rawClassId in fullCasters -> level
            rawClassId in halfCasters -> ceil(level / 2.0).toInt()
            rawClassId == "fighter" && rawSubclassId in fighterSpellSubclasses -> floor(level / 3.0).toInt()
            rawClassId == "rogue" && rawSubclassId in rogueSpellSubclasses -> floor(level / 3.0).toInt()
            else -> 0
        }

        if (casterLevel <= 0) return null

        val slots = multiclassSlotsTable[casterLevel] ?: emptyMap()
        return CasterInfo(casterLevel, slots)
    }

    /**
     * Проверяет, является ли класс заклинательным (включая подклассы Воина/Плута).
     */
    fun isSpellcaster(classId: String, subclassId: String? = null): Boolean {
        val rawClassId = classId.substringAfterLast(":")
        val rawSubclassId = subclassId?.substringAfterLast(":")
        return rawClassId in fullCasters ||
            rawClassId in halfCasters ||
            (rawClassId == "fighter" && rawSubclassId in fighterSpellSubclasses) ||
            (rawClassId == "rogue" && rawSubclassId in rogueSpellSubclasses)
    }
}
