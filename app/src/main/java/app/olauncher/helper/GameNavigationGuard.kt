package app.olauncher.helper

/** Keep games navigable even when no system buttons are available. */
internal class GameNavigationGuard {
    private var foreground: String? = null
    private var isGame = false
    private var pendingAction: Int? = null
    private var pendingAt = 0L

    fun foreground(packageName: String, game: Boolean) {
        if (foreground != packageName || isGame != game) clearConfirmation()
        foreground = packageName
        isGame = game
    }

    fun allow(action: Int, now: Long): Boolean {
        if (!isGame) return true
        if (pendingAction == action && now - pendingAt in 0L..3000L) {
            clearConfirmation()
            return true
        }
        pendingAction = action
        pendingAt = now
        return false
    }

    fun clearConfirmation() { pendingAction = null; pendingAt = 0L }
}
