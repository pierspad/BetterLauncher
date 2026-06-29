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

    fun score(label: String, query: String): Int {
        val strict = scoreStrict(label, query)
        if (strict >= 0) return strict
        return scoreSubsequencePerWord(label, query)
    }

    fun matches(label: String, query: String): Boolean = score(label, query) >= 0

    private fun findSubsequenceEnd(word: String, startIdx: Int, part: String): Int {
        if (part.isEmpty()) return startIdx
        var pi = 0
        for (i in startIdx until word.length) {
            if (word[i] == part[pi]) {
                pi++
                if (pi == part.length) return i + 1
            }
        }
        return -1
    }

    private fun scoreSubsequencePerWord(label: String, query: String): Int {
        val words = label.split(Regex("[\\s\\-_+,.`']+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
        
        val parts = query.split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

        if (parts.isEmpty() || words.isEmpty()) return -1

        var wi = 0
        var ci = 0
        var bonus = 0

        for (part in parts) {
            var found = false
            while (wi < words.size) {
                val endIdx = findSubsequenceEnd(words[wi], ci, part)
                if (endIdx >= 0) {
                    if (ci == 0 && words[wi].startsWith(part)) {
                        bonus += 50
                    }
                    ci = endIdx
                    found = true
                    break
                } else {
                    wi++
                    ci = 0
                }
            }
            if (!found) return -1
        }

        return 300 + bonus
    }

    /**
     * Stricter variant for secondary sources (Android settings tiles, contacts) where the
     * loose subsequence pass is too permissive — e.g. "pint" is a subsequence of
     * "opzioni sviluppatore" and would wrongly surface Developer Options. Here only a
     * prefix or a contiguous substring counts, and a per-word prefix (so "svil" still
     * finds "Opzioni sviluppatore"). Returns -1 when there is no such match.
     */
    fun scoreStrict(label: String, query: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val l = normalize(label)
        if (l.isEmpty()) return -1

        val idx = l.indexOf(q)
        if (idx == 0) return 1000              // whole-label prefix: best
        if (idx > 0) return 700 - idx.coerceAtMost(200) // contiguous substring

        // Per-word prefix: match the query against the start of any word in the label.
        // normalize() strips separators, so score against the *raw* words instead.
        for (word in label.split(Regex("[\\s\\-_+,.`']+"))) {
            if (word.isEmpty()) continue
            if (normalize(word).startsWith(q)) return 650
        }
        return -1
    }
}
