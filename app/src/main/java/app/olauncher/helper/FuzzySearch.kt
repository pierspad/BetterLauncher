package app.olauncher.helper

import java.text.Normalizer

enum class SearchMode(val value: Int) {
    PRECISE(0),
    LOOSE_FUZZY(2);

    companion object {
        // Legacy Smart (0) and Word prefix (1) both become Precise; Flexible stays 2.
        fun fromValue(value: Int): SearchMode =
            entries.firstOrNull { it.value == value } ?: PRECISE
    }
}

/** Compact substrings and ordered word fragments, with opt-in subsequence matching. */
object FuzzySearch {

    private val whitespaceRegex = Regex("[\\s\\p{Z}]+")
    private val wordsRegex = Regex("[\\p{P}\\p{S}\\s\\p{Z}]+")
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = wordsRegex

    fun normalize(s: CharSequence): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")
            .lowercase()

    fun normalizeWithSpaces(s: CharSequence): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
            .lowercase()

    private fun getWords(label: String): List<String> =
        label.split(wordsRegex)
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

    /**
     * Each fragment must occur inside a distinct word, in query order.
     */
    private fun matchesWordFragments(
        labelWords: List<String>,
        queryTokens: List<String>,
        prefixesOnly: Boolean = false,
    ): Boolean {
        if (labelWords.size < queryTokens.size) return false

        var wordIdx = 0
        for (token in queryTokens) {
            var found = false
            while (wordIdx < labelWords.size) {
                val word = labelWords[wordIdx]
                if (if (prefixesOnly) word.startsWith(token) else word.contains(token)) {
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

    private fun isSubsequence(target: String, query: String): Boolean {
        var position = 0
        for (character in query) {
            val found = target.indexOf(character, position)
            if (found < 0) return false
            position = found + 1
        }
        return true
    }

    /** Immutable fields shared by every query until the source label changes. */
    class PreparedLabel internal constructor(
        internal val words: List<String>,
        internal val compact: String,
        internal val spaced: String,
        val sortKey: String,
    )

    /** Tokenize and normalize once per filter operation, rather than once per result. */
    class PreparedQuery internal constructor(
        internal val blank: Boolean,
        internal val tokens: List<String>,
        internal val compact: String,
    )

    fun prepareLabel(label: String): PreparedLabel = PreparedLabel(
        getWords(label), normalize(label), normalizeWithSpaces(label), label.lowercase(),
    )

    fun prepareQuery(query: String): PreparedQuery {
        val trimmed = query.trim()
        val tokens = getWords(trimmed)
        return PreparedQuery(trimmed.isEmpty(), tokens, tokens.joinToString(""))
    }

    fun score(label: String, query: String, mode: SearchMode = SearchMode.PRECISE): Int =
        score(prepareLabel(label), prepareQuery(query), mode)

    fun score(label: PreparedLabel, query: PreparedQuery, mode: SearchMode = SearchMode.PRECISE): Int {
        if (query.blank) return 0
        val parts = query.tokens
        if (parts.isEmpty()) return -1
        val labelWords = label.words
        val lNoSeparators = label.compact
        val qConcat = query.compact

        // Spaces explicitly separate fragments that must match distinct words.
        if (parts.size == 1 || matchesWordFragments(labelWords, parts)) {
            if (lNoSeparators == qConcat) return 1100
            if (lNoSeparators.startsWith(qConcat)) return 1000
            if (matchesWordFragments(labelWords, parts, prefixesOnly = true)) return 900
            if (lNoSeparators.contains(qConcat)) return 850
            if (parts.size > 1) return 800
        }

        if (mode == SearchMode.PRECISE || !isSubsequence(lNoSeparators, qConcat)) return -1

        val firstPart = parts.first()
        val startsWord = label.spaced.startsWith(firstPart) || label.spaced.contains(" $firstPart")
        return if (startsWord) 350 else 300
    }

    fun matches(label: String, query: String, mode: SearchMode = SearchMode.PRECISE): Boolean =
        score(label, query, mode) >= 0

    /**
     * Stricter variant for secondary sources (Android settings tiles, contacts) where the
     * loose subsequence pass is too permissive.
     */
    fun scoreStrict(label: String, query: String, mode: SearchMode = SearchMode.PRECISE): Int {
        val s = score(label, query, mode)
        return if (s >= 600) s else -1
    }

    fun scoreStrict(label: PreparedLabel, query: PreparedQuery, mode: SearchMode = SearchMode.PRECISE): Int {
        val score = score(label, query, mode)
        return if (score >= 600) score else -1
    }
}
