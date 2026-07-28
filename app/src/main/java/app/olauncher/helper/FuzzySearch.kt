package app.olauncher.helper

import java.text.Normalizer

enum class SearchMode(val value: Int) {
    SMART(0),
    STRICT_PREFIX(1),
    LOOSE_FUZZY(2);

    companion object {
        fun fromValue(value: Int): SearchMode =
            values().firstOrNull { it.value == value } ?: SMART
    }
}

/**
 * Lightweight fuzzy matcher for the app drawer search.
 *
 * Supports three matching modes:
 *  - SMART (default): Word-prefix matching when spaces are present, with initialisms
 *    and subsequence matching for single tokens.
 *  - STRICT_PREFIX: Every query token must match the start of a word in the app label.
 *  - LOOSE_FUZZY: Legacy loose subsequence matching anywhere in the label.
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

    private fun getWords(label: String): List<String> =
        label.split(Regex("[\\s\\-_+,.`']+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

    /**
     * Checks if each query token matches the prefix of consecutive or ordered words in the label.
     */
    private fun matchesWordPrefixes(labelWords: List<String>, queryTokens: List<String>): Boolean {
        if (queryTokens.isEmpty()) return true
        if (labelWords.size < queryTokens.size) return false

        var wordIdx = 0
        for (token in queryTokens) {
            var found = false
            while (wordIdx < labelWords.size) {
                if (labelWords[wordIdx].startsWith(token)) {
                    found = true
                    wordIdx++
                    break
                }
                wordIdx++
            }
            if (!found) return false
        }
        return true
    }

    /**
     * Checks if single-token query matches the initial letters of words (e.g. "pm" -> "Proton Mail", "ytm" -> "YouTube Music").
     */
    private fun matchesInitials(labelWords: List<String>, queryToken: String): Boolean {
        if (queryToken.length < 2 || labelWords.size < queryToken.length) return false
        val initials = labelWords.mapNotNull { it.firstOrNull() }.joinToString("")
        return initials.startsWith(queryToken) || initials.contains(queryToken)
    }

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

    fun score(label: String, query: String, mode: SearchMode = SearchMode.SMART): Int {
        val qClean = query.trim()
        if (qClean.isEmpty()) return 0

        val parts = qClean.split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

        if (parts.isEmpty()) return -1

        val labelWords = getWords(label)
        val lNoSeparators = normalize(label)
        val qConcat = parts.joinToString("")

        return when (mode) {
            SearchMode.STRICT_PREFIX -> {
                if (matchesWordPrefixes(labelWords, parts)) {
                    if (lNoSeparators.startsWith(qConcat)) 1000 else 850
                } else -1
            }

            SearchMode.LOOSE_FUZZY -> {
                val lSpaces = normalizeWithSpaces(label)
                val matches = matchesParts(lSpaces, parts, 0, 0) || matchesParts(lNoSeparators, parts, 0, 0)
                if (!matches) return -1

                if (lNoSeparators.startsWith(qConcat)) return 1000
                val idx = lNoSeparators.indexOf(qConcat)
                if (idx > 0) return 700 - idx.coerceAtMost(200)

                for (word in labelWords) {
                    if (word.startsWith(qConcat)) return 650
                }

                var bonus = 0
                if (parts.isNotEmpty()) {
                    val firstPart = parts[0]
                    if (lSpaces.startsWith(firstPart) || lSpaces.contains(" $firstPart")) {
                        bonus += 50
                    }
                }
                300 + bonus
            }

            SearchMode.SMART -> {
                val hasSpaces = qClean.contains(" ")

                if (hasSpaces) {
                    // Multi-token query (e.g. "Proton M" or "Proton L")
                    if (matchesWordPrefixes(labelWords, parts)) {
                        if (lNoSeparators.startsWith(qConcat)) 1000 else 850
                    } else -1
                } else {
                    // Single-token query (e.g. "proton", "pm", "rdd", "tube")
                    // 1. Whole label prefix check
                    if (lNoSeparators.startsWith(qConcat)) return 1000

                    // 2. Word prefix check
                    for (word in labelWords) {
                        if (word.startsWith(qConcat)) return 850
                    }

                    // 3. Initials match (e.g. "pm" -> "Proton Mail")
                    if (matchesInitials(labelWords, qConcat)) return 800

                    // 4. Contiguous substring check (e.g. "tube" -> "YouTube")
                    val idx = lNoSeparators.indexOf(qConcat)
                    if (idx > 0) return 700 - idx.coerceAtMost(200)

                    // 5. Subsequence match (e.g. "rdd" -> "Reddit")
                    val lSpaces = normalizeWithSpaces(label)
                    val matches = matchesParts(lSpaces, parts, 0, 0) || matchesParts(lNoSeparators, parts, 0, 0)
                    if (matches) return 400

                    -1
                }
            }
        }
    }

    fun matches(label: String, query: String, mode: SearchMode = SearchMode.SMART): Boolean =
        score(label, query, mode) >= 0

    /**
     * Stricter variant for secondary sources (Android settings tiles, contacts) where the
     * loose subsequence pass is too permissive.
     */
    fun scoreStrict(label: String, query: String, mode: SearchMode = SearchMode.SMART): Int {
        val s = score(label, query, mode)
        return if (s >= 600) s else -1
    }
}
