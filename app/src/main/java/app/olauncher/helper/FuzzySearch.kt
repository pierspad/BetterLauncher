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

/** Consecutive fragments in ordered, distinct words; flexible subsequences are opt-in. */
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
    private fun matchesWordFragments(labelWords: List<String>, queryTokens: List<String>): Boolean {
        if (queryTokens.isEmpty()) return true
        if (labelWords.size < queryTokens.size) return false

        var wordIdx = 0
        for (token in queryTokens) {
            var found = false
            while (wordIdx < labelWords.size) {
                if (labelWords[wordIdx].contains(token)) {
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

        return when (mode) {
            SearchMode.PRECISE -> {
                if (matchesWordFragments(labelWords, parts)) {
                    if (lNoSeparators.startsWith(qConcat)) 1000 else 850
                } else -1
            }

            SearchMode.LOOSE_FUZZY -> {
                val lSpaces = label.spaced
                val matches = isSubsequence(lNoSeparators, qConcat)
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
        }
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
