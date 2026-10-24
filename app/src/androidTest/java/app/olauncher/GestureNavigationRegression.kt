package app.olauncher

import app.olauncher.helper.GestureNavigationMode

/** Reproduces the Xiaomi's stale 130px navigation inset after entering gesture mode. */
fun runGestureNavigationRegression() {
    val physicalBottom = 2400
    val staleThreeButtonInset = 130
    val gestural = GestureNavigationMode.isGestural(androidMode = 2, vendorMode = 0)
    check(gestural) { "The vendor flag can remain zero after selecting Android gesture mode" }
    check(physicalBottom - GestureNavigationMode.bottomOffset(gestural, staleThreeButtonInset) == physicalBottom) {
        "Physical-edge Home swipes must reach the overlay despite stale navigation insets"
    }
    val buttons = GestureNavigationMode.isGestural(androidMode = 0, vendorMode = 0)
    check(!buttons)
    check(physicalBottom - GestureNavigationMode.bottomOffset(buttons, staleThreeButtonInset) == 2270) {
        "Gesture overlays must not intercept system navigation buttons"
    }
    check(GestureNavigationMode.isGestural(androidMode = -1, vendorMode = 1))
    check(!GestureNavigationMode.isGestural(androidMode = -1, vendorMode = 0))
}
