package app.olauncher.helper

import java.util.Locale

/** Uses supported modes, since an ordinary adaptive display can temporarily run at 24 Hz. */
internal fun isEinkDevice(currentRate: Float, supportedRates: FloatArray, manufacturer: String, brand: String, model: String): Boolean {
    val einkBrands = setOf("onyx", "boox", "dasung", "bigme", "boyue", "meebook", "mudita")
    val makers = setOf(manufacturer.lowercase(Locale.ROOT), brand.lowercase(Locale.ROOT))
    if (makers.any { it in einkBrands }) return true
    if ("hisense" in makers && Regex("A[579](?:\\s.*)?|TOUCH(?:\\s.*)?|HI READER(?:\\s.*)?", RegexOption.IGNORE_CASE).matches(model)) return true
    val maximum = supportedRates.filter { it.isFinite() && it > 0 }.maxOrNull() ?: return false
    return maximum <= 30f
}
