package app.olauncher

import app.olauncher.helper.FuzzySearch
import app.olauncher.helper.SearchMode

fun main() = runSearchMatchingRegression()

fun runSearchMatchingRegression() {
    val cases = listOf(
        Triple("Reddit", "edd", true),
        Triple("Reddit", "rdd", false),
        Triple("Proton Mail", "Prot", true),
        Triple("Pedometro - Contapassi", "Prot", false),
        Triple("Proton Mail", "pro ma", true),
        Triple("Proton Mail", "ton ail", true),
        Triple("Proton Mail", "ail ton", false),
        Triple("Proton Mail", "pro ton", false),
        Triple("Proton Mail", "tonma", false),
        Triple("Proton-Mail", "ton ail", true),
        Triple("Proton/Mail", "ton ail", true),
        Triple("Proton\u00a0Mail", "ton ail", true),
        Triple("Café Notes", "AFE ote", true),
        Triple("Proton Mail", "  ton   ail  ", true),
        Triple("Proton Mail", "!!!", false),
        Triple("Proton Mail", "proton l", true),
    )
    var failures = 0
    for ((label, query, expected) in cases) {
        val actual = FuzzySearch.matches(label, query)
        if (actual != expected) {
            println("FAIL: '$query' in '$label': expected $expected, got $actual")
            failures++
        }
    }
    val apps = listOf("Proton Mail", "Pedometro - Contapassi", "Reddit")
    check(apps.filter { FuzzySearch.matches(it, "Prot") } == listOf("Proton Mail"))
    check(apps.filter { FuzzySearch.matches(it, "edd") } == listOf("Reddit"))
    check((apps + "Reddit News").filter { FuzzySearch.matches(it, "edd") }.size == 2)
    check(FuzzySearch.matches("Reddit", "rdd", SearchMode.LOOSE_FUZZY))
    check(FuzzySearch.matches("Proton Mail", "ton ail", SearchMode.fromValue(0)))
    check(FuzzySearch.matches("Proton Mail", "ton ail", SearchMode.fromValue(1)))
    check(failures == 0) { "$failures matching failures" }
    val labels = listOf("Proton Mail", "Café Notes", "Reddit", "Reddit News", "Proton/Mail", "Wi-Fi", "地图", "")
    val queries = listOf("", " ", "pro ma", "ton ail", "pro ton", "rdd", "AFE ote", "!!!", "Wi", "地图")
    var preparedChecks = 0
    for (mode in SearchMode.entries) for (label in labels) for (query in queries) {
        val expected = FuzzySearch.score(label, query, mode)
        val preparedLabel = FuzzySearch.prepareLabel(label)
        val preparedQuery = FuzzySearch.prepareQuery(query)
        check(FuzzySearch.score(preparedLabel, preparedQuery, mode) == expected) {
            "Prepared scoring changed '$label' / '$query' / $mode"
        }
        val expectedStrict = if (expected >= 600) expected else -1
        check(FuzzySearch.scoreStrict(preparedLabel, preparedQuery, mode) == expectedStrict)
        preparedChecks++
    }
    println("PASS: $preparedChecks prepared scoring equivalence checks")
    println("PASS: ${cases.size} matching cases, unique/ambiguous results and flexible mode")
}
