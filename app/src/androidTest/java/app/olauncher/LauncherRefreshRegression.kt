package app.olauncher

import android.os.Process
import android.os.UserHandle
import app.olauncher.helper.LauncherRefreshCallback

fun runLauncherRefreshRegression() {
    val personal = Process.myUserHandle()
    val work = UserHandle.getUserHandleForUid(10 * 100_000)
    val events = mutableListOf<Pair<String, UserHandle>>()
    val callback = LauncherRefreshCallback { pkg, user -> events += pkg to user }
    callback.onPackageAdded("app.added", personal)
    callback.onPackageChanged("app.updated", work)
    callback.onPackageRemoved("app.removed", work)
    callback.onPackagesAvailable(arrayOf("app.one", "app.two"), personal, false)
    callback.onPackagesUnavailable(arrayOf("app.one"), work, true)
    callback.onShortcutsChanged("app.browser", mutableListOf(), work)
    check(events == listOf(
        "app.added" to personal, "app.updated" to work, "app.removed" to work,
        "app.one" to personal, "app.two" to personal, "app.one" to work,
        "app.browser" to work,
    )) { "Package/shortcut events must refresh their own profile: $events" }
}
