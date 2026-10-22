package app.olauncher

import app.olauncher.helper.isEinkDevice

fun main() = runDeviceClassificationRegression()

fun runDeviceClassificationRegression() {
    check(!isEinkDevice(24f, floatArrayOf(24f, 60f, 120f), "Samsung", "Samsung", "Galaxy")) {
        "Adaptive low refresh must not classify an LCD/OLED as e-ink"
    }
    check(isEinkDevice(12f, floatArrayOf(12f, 24f), "Generic", "Generic", "Reader"))
    check(isEinkDevice(60f, floatArrayOf(60f), "ONYX", "BOOX", "Note Air"))
    check(isEinkDevice(60f, floatArrayOf(60f), "Hisense", "Hisense", "A9"))
    check(!isEinkDevice(60f, floatArrayOf(60f), "Hisense", "Hisense", "Infinity H60"))
    check(!isEinkDevice(60f, floatArrayOf(60f), "NotOnyx", "Generic", "Touch Phone"))
    check(!isEinkDevice(0f, floatArrayOf(), "Generic", "Generic", "Unknown"))
    println("PASS: e-ink classification handles adaptive refresh and narrow identities")
}
