package app.olauncher.helper

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle

/** Routes package and shortcut changes to the launcher's coalesced refresh. */
class LauncherRefreshCallback(
    private val changed: (String, UserHandle) -> Unit,
) : LauncherApps.Callback() {
    override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName, user)
    override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName, user)
    override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName, user)
    override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        packageNames.forEach { changed(it, user) }
    }
    override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        packageNames.forEach { changed(it, user) }
    }
    override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = changed(packageName, user)
}
