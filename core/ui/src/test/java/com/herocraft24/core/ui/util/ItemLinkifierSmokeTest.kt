package com.herocraft24.core.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Smoke test for condition linkification inside spell descriptions.
 *
 * It mirrors the map built by SpellsViewModel.conditionNameToIdMap and checks
 * that ItemLinkifier.findRanges links condition names the same way the app would.
 */
class ItemLinkifierSmokeTest {

    private val conditionNameToIdMap = listOf(
        "blinded" to "Ослеплённый",
        "charmed" to "Очарованный",
        "deafened" to "Оглохший",
        "exhaustion" to "Истощение",
        "frightened" to "Испуганный",
        "grappled" to "Схваченный",
        "incapacitated" to "Недееспособный",
        "invisible" to "Невидимый",
        "paralyzed" to "Парализованный",
        "petrified" to "Окаменевший",
        "poisoned" to "Отравленный",
        "prone" to "Опрокинутый",
        "restrained" to "Опутанный",
        "stunned" to "Ошеломлённый",
        "unconscious" to "Бессознательный",
    ).flatMap { (id, ruName) ->
        listOf(id to "phb2024:$id", ruName to "phb2024:$id")
    }.toMap()

    private val cache = ItemLinkifier.BucketsCache(conditionNameToIdMap)

    @Test
    fun `fireball has no condition links`() {
        val text = loadDescription("fireball")
        val matches = ItemLinkifier.findRanges(text, cache)
        assertTrue("Expected no links in fireball, got $matches", matches.isEmpty())
    }

    @Test
    fun `zone of truth has no condition links`() {
        val text = loadDescription("zone_of_truth")
        val matches = ItemLinkifier.findRanges(text, cache)
        assertTrue("Expected no links in zone_of_truth, got $matches", matches.isEmpty())
    }

    @Test
    fun `charm person links Очарованный`() {
        val text = loadDescription("charm_person")
        val matches = ItemLinkifier.findRanges(text, cache)
            .filter { it.fullId == "phb2024:charmed" }
        assertTrue("Expected at least one 'Очарованный' link", matches.isNotEmpty())
    }

    @Test
    fun `blinding smite links Ослеплённый`() {
        val text = loadDescription("blinding_smite")
        val matches = ItemLinkifier.findRanges(text, cache)
            .filter { it.fullId == "phb2024:blinded" }
        assertTrue("Expected at least one 'Ослеплённый' link", matches.isNotEmpty())
    }

    @Test
    fun `entangle links Опутанный including inflected form`() {
        val text = loadDescription("entangle")
        val matches = ItemLinkifier.findRanges(text, cache)
            .filter { it.fullId == "phb2024:restrained" }
        assertTrue("Expected at least one 'Опутанный' link", matches.isNotEmpty())
    }

    @Test
    fun `contagion links Отравленный`() {
        val text = loadDescription("contagion")
        val matches = ItemLinkifier.findRanges(text, cache)
            .filter { it.fullId == "phb2024:poisoned" }
        assertTrue("Expected at least one 'Отравленный' link", matches.isNotEmpty())
    }

    @Test
    fun `paralyzed condition links Недееспособный`() {
        val text = loadConditionText("paralyzed")
        val matches = ItemLinkifier.findRanges(text, cache)
            .filter { it.fullId == "phb2024:incapacitated" }
        assertTrue("Expected at least one 'Недееспособный' link in paralyzed", matches.isNotEmpty())
    }

    @Test
    fun `petrified condition links Отравленный`() {
        val text = loadConditionText("petrified")
        val matches = ItemLinkifier.findRanges(text, cache)
            .filter { it.fullId == "phb2024:poisoned" }
        assertTrue("Expected at least one 'Отравленный' link in petrified", matches.isNotEmpty())
    }

    private fun loadConditionText(conditionId: String): String {
        val root = File("C:\\Users\\Newfo\\HeroCraft24\\app\\src\\main\\assets\\packs\\phb2024\\conditions")
        val file = File(root, "$conditionId.json")
        require(file.exists()) { "Missing test asset: $file" }
        val text = file.readText()
        val descKey = "\"description\":"
        val descStart = text.indexOf(descKey)
        require(descStart >= 0) { "No description block in $conditionId" }
        val ruKey = "\"ru\":"
        val start = text.indexOf(ruKey, descStart)
        require(start >= 0) { "No Russian description in $conditionId" }
        val valueStart = text.indexOf('"', start + ruKey.length) + 1
        val valueEnd = text.indexOf("\"", valueStart)
        val desc = text.substring(valueStart, valueEnd)
            .replace("\\n", "\n")

        val effects = mutableListOf<String>()
        val effectsKey = "\"effects\":"
        val effectsStart = text.indexOf(effectsKey)
        if (effectsStart >= 0) {
            var searchStart = effectsStart
            while (true) {
                val ruStart = text.indexOf(ruKey, searchStart)
                if (ruStart < 0) break
                val vStart = text.indexOf('"', ruStart + ruKey.length) + 1
                val vEnd = text.indexOf("\"", vStart)
                if (vEnd < 0) break
                effects.add(text.substring(vStart, vEnd).replace("\\n", "\n"))
                searchStart = vEnd + 1
            }
        }
        return listOf(desc).plus(effects).joinToString("\n")
    }

    private fun loadDescription(spellId: String): String {
        val root = File("C:\\Users\\Newfo\\HeroCraft24\\app\\src\\main\\assets\\packs\\phb2024\\spells")
        val file = File(root, "$spellId.json")
        require(file.exists()) { "Missing test asset: $file" }
        // Naive JSON value extraction for the Russian description is enough here.
        val text = file.readText()
        val descKey = "\"description\":"
        val descStart = text.indexOf(descKey)
        require(descStart >= 0) { "No description block in $spellId" }
        val ruKey = "\"ru\":"
        val start = text.indexOf(ruKey, descStart)
        require(start >= 0) { "No Russian description in $spellId" }
        val valueStart = text.indexOf('"', start + ruKey.length) + 1
        val valueEnd = text.indexOf("\"", valueStart)
        return text.substring(valueStart, valueEnd)
            .replace("\\n", "\n")
    }
}
