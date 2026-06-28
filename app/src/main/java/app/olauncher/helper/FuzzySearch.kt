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
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val l = normalize(label)
        if (l.isEmpty()) return -1

        val idx = l.indexOf(q)
        when {
            idx == 0 -> return 1000               // prefix: best
            idx > 0 -> return 700 - idx.coerceAtMost(200) // substring: strong, earlier is better
        }

        // Subsequence: every query char must appear in order.
        var li = 0
        var contiguous = 0
        var maxContiguous = 0
        for (qc in q) {
            var found = false
            while (li < l.length) {
                val match = l[li] == qc
                li++
                if (match) {
                    contiguous++
                    if (contiguous > maxContiguous) maxContiguous = contiguous
                    found = true
                    break
                } else {
                    contiguous = 0
                }
            }
            if (!found) return -1
        }
        return 300 + maxContiguous * 10
    }

    fun matches(label: String, query: String): Boolean = score(label, query) >= 0

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
