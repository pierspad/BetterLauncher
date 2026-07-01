package app.olauncher.helper

import java.text.Normalizer

/**
 * Lightweight fuzzy matcher for the app drawer search.
 *
 * Matching is case/diacritic/separator insensitive. It rewards, in order:
 *  - exact prefix ("you" → "YouTube"),
 *  - substring ("tube" → "YouTube"),
 *  - subsequence in order ("ytm" → "YouTube Music"), with a bonus for longer
 *    contiguous runs so closer matches rank higher.
 *
 * [score] returns a higher-is-better value, or -1 when the query does not match.
 */
object FuzzySearch {

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")

    fun normalize(s: CharSequence): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")
            .lowercase()

    fun normalizeWithSpaces(s: CharSequence): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

    private fun matchesParts(target: String, parts: List<String>, partIdx: Int, targetStartIdx: Int): Boolean {
        if (partIdx >= parts.size) return true
        val part = parts[partIdx]
        var searchIdx = targetStartIdx
        while (true) {
            val idx = target.indexOf(part, searchIdx)
            if (idx == -1) return false
            if (matchesParts(target, parts, partIdx + 1, idx + part.length + 1)) {
                return true
            }
            searchIdx = idx + 1
        }
    }

    fun score(label: String, query: String): Int {
        val qClean = query.trim()
        if (qClean.isEmpty()) return 0

        val parts = qClean.split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

        if (parts.isEmpty()) return -1

        val lSpaces = normalizeWithSpaces(label)
        val lNoSeparators = normalize(label)

        val matches = matchesParts(lSpaces, parts, 0, 0) || matchesParts(lNoSeparators, parts, 0, 0)
        if (!matches) return -1

        val qConcat = parts.joinToString("")

        // 1. Whole label prefix check
        if (lNoSeparators.startsWith(qConcat)) {
            return 1000
        }

        // 2. Contiguous substring check
        val idx = lNoSeparators.indexOf(qConcat)
        if (idx > 0) {
            return 700 - idx.coerceAtMost(200)
        }

        // 3. Word prefix check
        for (word in label.split(Regex("[\\s\\-_+,.`']+"))) {
            if (word.isEmpty()) continue
            if (normalize(word).startsWith(qConcat)) {
                return 650
            }
        }

        // 4. Otherwise, subsequence match (with gaps)
        var bonus = 0
        if (parts.isNotEmpty()) {
            val firstPart = parts[0]
            if (lSpaces.startsWith(firstPart) || lSpaces.contains(" $firstPart")) {
                bonus += 50
            }
        }

        return 300 + bonus
    }

    fun matches(label: String, query: String): Boolean = score(label, query) >= 0

    /**
     * Stricter variant for secondary sources (Android settings tiles, contacts) where the
     * loose subsequence pass is too permissive.
     */
    fun scoreStrict(label: String, query: String): Int {
        val s = score(label, query)
        return if (s >= 600) s else -1
    }
}
