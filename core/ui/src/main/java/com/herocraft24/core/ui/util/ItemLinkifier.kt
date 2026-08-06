package com.herocraft24.core.ui.util

/**
 * Finds ranges of item names inside a text, including simple inflected forms.
 *
 * Matching rules:
 * 1. Exact match of an item name (longest first).
 * 2. Stem match: the item name without its last character also matches a word
 *    that starts with that stem, as long as the whole word is not longer than
 *    the original name + 2 characters. This handles Russian case endings such
 *    as "Масло" / "Масла" without pulling in unrelated long words.
 */
object ItemLinkifier {

    data class Match(val start: Int, val end: Int, val fullId: String)

    data class ExplicitLink(val range: IntRange, val fullId: String)

    data class MarkerResult(
        val text: String,
        val excludedRanges: List<IntRange>,
        val explicitLinks: List<ExplicitLink> = emptyList()
    )

    /**
     * Pre-built index for fast lookups. Build once per name-map and reuse across calls.
     */
    class BucketsCache(itemMap: Map<String, String>) {
        val exactBuckets = HashMap<Char, List<Pair<String, String>>>()
        val stemBuckets = HashMap<Char, List<StemEntry>>()

        init {
            val exactMutable = HashMap<Char, MutableList<Pair<String, String>>>()
            val stemMutable = HashMap<Char, MutableList<StemEntry>>()
            for (entry in itemMap.entries) {
                val name = entry.key
                if (name.isBlank()) continue
                exactMutable.getOrPut(name[0].lowercaseChar()) { mutableListOf() }
                    .add(name to entry.value)
                if (name.length >= 4 && name.last().isLetterOrDigit()) {
                    val stem = name.dropLast(1)
                    stemMutable.getOrPut(stem[0].lowercaseChar()) { mutableListOf() }
                        .add(StemEntry(stem, name.length, entry.value))
                }
            }
            for ((k, v) in exactMutable) {
                exactBuckets[k] = v.sortedByDescending { it.first.length }
            }
            for ((k, v) in stemMutable) {
                stemBuckets[k] = v.sortedByDescending { it.stem.length }
            }
        }
    }

    /**
     * Removes link markers from the text, returning the clean display string plus:
     *  - `excludedRanges`: ranges (in the clean string) that must NOT be auto-linked.
     *  - `explicitLinks`: manual links `[[text|id]]` the author wants to force.
     *
     * Supported markers:
     *  - `{...}`  suppress auto-linking of the wrapped text (e.g. "{Тёмное зрение}").
     *  - `[[text|id]]` force a link to the given object id for the wrapped text
     *    (e.g. "[[Волшебные стрелы|phb2024:magic_missile]]").
     */
    fun stripMarkers(text: String): MarkerResult {
        if (text.indexOf('{') < 0 && text.indexOf('[') < 0) {
            return MarkerResult(text, emptyList())
        }
        val sb = StringBuilder(text.length)
        val excluded = mutableListOf<IntRange>()
        val explicit = mutableListOf<ExplicitLink>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '{') {
                val close = text.indexOf('}', i + 1)
                if (close != -1) {
                    val outStart = sb.length
                    val content = text.substring(i + 1, close)
                    sb.append(content)
                    excluded.add(outStart until outStart + content.length)
                    i = close + 1
                    continue
                }
            }
            if (c == '}') {
                // A stray closing brace: drop it.
                i++
                continue
            }
            if (c == '[') {
                val close = text.indexOf(']', i + 1)
                // Only treat as explicit link when it's a well-formed [[text|id]].
                if (close != -1 && text[close] == ']' && close + 1 < text.length && text[close + 1] == ']') {
                    val inner = text.substring(i + 1, close)
                    val pipe = inner.lastIndexOf('|')
                    if (pipe > 0) {
                        val display = inner.substring(0, pipe)
                        val id = inner.substring(pipe + 1).trim()
                        if (display.isNotBlank() && id.isNotBlank()) {
                            val outStart = sb.length
                            sb.append(display)
                            excluded.add(outStart until outStart + display.length)
                            explicit.add(ExplicitLink(outStart until outStart + display.length, id))
                            i = close + 2
                            continue
                        }
                    }
                }
            }
            sb.append(c)
            i++
        }
        return MarkerResult(sb.toString(), excluded.toList(), explicit.toList())
    }

    fun findRanges(
        text: String,
        itemMap: Map<String, String>,
        excludedRanges: List<IntRange> = emptyList()
    ): List<Match> {
        if (text.isBlank()) return emptyList()

        // Group candidates by their first letter so that for every position in the text
        // we only scan the few entries sharing the same starting character, instead of
        // scanning all ~1400 names on every character. Within a bucket, longer names go
        // first so the "longest match wins" behaviour is preserved.
        val exactBuckets = HashMap<Char, List<Pair<String, String>>>()
        val stemBuckets = HashMap<Char, List<StemEntry>>()
        val exactMutable = HashMap<Char, MutableList<Pair<String, String>>>()
        val stemMutable = HashMap<Char, MutableList<StemEntry>>()
        for (entry in itemMap.entries) {
            val name = entry.key
            if (name.isBlank()) continue
            exactMutable.getOrPut(name[0].lowercaseChar()) { mutableListOf() }
                .add(name to entry.value)
            if (name.length >= 4 && name.last().isLetterOrDigit()) {
                val stem = name.dropLast(1)
                stemMutable.getOrPut(stem[0].lowercaseChar()) { mutableListOf() }
                    .add(StemEntry(stem, name.length, entry.value))
            }
        }
        for ((k, v) in exactMutable) exactBuckets[k] = v.sortedByDescending { it.first.length }
        for ((k, v) in stemMutable) stemBuckets[k] = v.sortedByDescending { it.stem.length }

        return findRanges(text, exactBuckets, stemBuckets, excludedRanges)
    }

    /**
     * Fast path: uses pre-built [BucketsCache] to skip the expensive bucket construction.
     * Useful when the same name map is used repeatedly (e.g., linking many feature descriptions).
     */
    fun findRanges(
        text: String,
        cache: BucketsCache,
        excludedRanges: List<IntRange> = emptyList()
    ): List<Match> {
        if (text.isBlank()) return emptyList()
        return findRanges(text, cache.exactBuckets, cache.stemBuckets, excludedRanges)
    }

    private fun findRanges(
        text: String,
        exactBuckets: Map<Char, List<Pair<String, String>>>,
        stemBuckets: Map<Char, List<StemEntry>>,
        excludedRanges: List<IntRange>
    ): List<Match> {
        val used = mutableListOf<IntRange>()
        used.addAll(excludedRanges)
        val matches = mutableListOf<Match>()

        // Pass 1: exact item names.
        var i = 0
        while (i < text.length) {
            val candidates = exactBuckets[text[i].lowercaseChar()]
            var matched: Match? = null
            if (candidates != null) {
                for ((name, fullId) in candidates) {
                    // Quick length guard before the (cheap) boundary checks.
                    if (i + name.length > text.length) continue
                    if (text.regionMatches(i, name, 0, name.length, ignoreCase = true) &&
                        isWordBoundary(text, i - 1) &&
                        isWordBoundary(text, i + name.length)
                    ) {
                        matched = Match(i, i + name.length, fullId)
                        break
                    }
                }
            }
            if (matched != null) {
                used.add(matched.start..<matched.end)
                matches.add(matched)
                i = matched.end
            } else {
                i++
            }
        }

        // Pass 2: inflected forms via stems.
        i = 0
        while (i < text.length) {
            val candidates = stemBuckets[text[i].lowercaseChar()]
            var matched: Match? = null
            if (candidates != null) {
                for (entry in candidates) {
                    if (i + entry.stem.length > text.length) continue
                    if (!text.regionMatches(i, entry.stem, 0, entry.stem.length, ignoreCase = true)) continue
                    if (!isWordBoundary(text, i - 1)) continue

                    // Consume the rest of the word.
                    var end = i + entry.stem.length
                    while (end < text.length && text[end].isLetterOrDigit()) end++

                    if (end - i > entry.originalLength + 2) continue
                    if (!isWordBoundary(text, end)) continue
                    if (used.any { it.first < end && i < it.last }) continue

                    matched = Match(i, end, entry.fullId)
                    break
                }
            }
            if (matched != null) {
                used.add(matched.start..<matched.end)
                matches.add(matched)
                i = matched.end
            } else {
                i++
            }
        }

        return matches.sortedBy { it.start }
    }

    private fun isWordBoundary(text: String, index: Int): Boolean {
        if (index < 0 || index >= text.length) return true
        return !text[index].isLetterOrDigit()
    }

    data class StemEntry(
        val stem: String,
        val originalLength: Int,
        val fullId: String
    )
}
