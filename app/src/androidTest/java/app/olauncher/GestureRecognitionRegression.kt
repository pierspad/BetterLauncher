package app.olauncher

import app.olauncher.helper.EdgeSwipeTracker
import app.olauncher.helper.GameNavigationGuard

fun runGestureRecognitionRegression() {
    val bottom = EdgeSwipeTracker(2)
    bottom.start(0f, 0f, 0)
    bottom.move(3f, -18f, 120)
    check(!bottom.armed) { "Short edge drags in games must remain ordinary touches" }
    bottom.start(0f, 0f, 1000)
    bottom.move(40f, -35f, 1100)
    check(!bottom.armed) { "Mostly diagonal game drags must not trigger Home" }
    bottom.start(0f, 0f, 2000)
    bottom.move(0f, -80f, 2150)
    bottom.move(18f, -68f, 2300)
    check(bottom.takeHold(2450)) { "Small natural finger drift must not restart the Recents hold" }
    check(bottom.fired && !bottom.takeHold(2800)) { "A hold must fire once, without a later Home" }
    bottom.start(0f, 0f, 3000)
    for (n in 1..8) {
        bottom.move(0f, -40f * n, 3000L + n * 100)
        check(!bottom.takeHold(3000L + n * 100)) { "Continuous travel is not a stationary hold" }
    }
    bottom.start(0f, 0f, 4000)
    bottom.move(0f, -100f, 4100)
    bottom.move(0f, -10f, 4200)
    check(bottom.cancelled && !bottom.takeHold(5000)) { "Reversing a swipe must cancel navigation" }
    bottom.start(0f, 0f, 6000)
    bottom.move(0f, -70f, 6100)
    bottom.cancel()
    check(!bottom.takeHold(6800)) { "Cancelled or multi-touch streams must not open Recents" }

    val guard = GameNavigationGuard()
    guard.foreground("ordinary.app", false)
    check(guard.allow(2, 0))
    guard.foreground("game.one", true)
    check(!guard.allow(2, 1000)) { "The first exit gesture must request confirmation" }
    check(guard.allow(2, 1500))
    check(!guard.allow(2, 1600)) { "A confirmation must be consumed once" }
    check(!guard.allow(3, 1700)) { "A different navigation action needs its own confirmation" }
    check(!guard.allow(3, 5000)) { "An expired confirmation must not allow a late accidental exit" }
    guard.foreground("game.two", true)
    check(!guard.allow(3, 5100)) { "Confirmations must not carry across games" }
    guard.clearConfirmation()
    check(!guard.allow(3, 5200))
    guard.foreground("ordinary.app", false)
    check(guard.allow(3, 5300)) { "Ordinary apps must not require a second gesture" }
}
