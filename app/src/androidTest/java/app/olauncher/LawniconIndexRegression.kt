package app.olauncher

import app.olauncher.helper.LawniconIndex

fun main() = runLawniconIndexRegression()

fun runLawniconIndexRegression() {
    val index = LawniconIndex(linkedMapOf(
        "alpha/First" to "first", "alphabet/Main" to "other",
        "alpha/Second" to "second", "beta/Main" to "beta"
    ))
    check(index.drawableFor("alpha", "Second") == "second")
    check(index.drawableFor("alpha", "Unknown") == "first")
    check(index.drawableFor("alpha", null) == "first")
    check(index.drawableFor("alphabet", null) == "other")
    check(index.drawableFor("missing", "First") == null)
    println("PASS: exact Lawnicon match, first package fallback, package boundaries and absent package")
}
