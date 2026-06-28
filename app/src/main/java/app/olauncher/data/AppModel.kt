package app.olauncher.data

import android.os.UserHandle
import java.text.CollationKey

sealed class AppModel : Comparable<AppModel> {
    abstract val appLabel: String
    abstract val key: CollationKey?
    abstract val appPackage: String
    abstract val user: UserHandle
    abstract val isNew: Boolean

    data class App(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val activityClassName: String?,
        override val isNew: Boolean = false,
        override val user: UserHandle,
    ) : AppModel()

    data class PinnedShortcut(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val shortcutId: String,
        override val isNew: Boolean = false,
        override val user: UserHandle,
    ) : AppModel()

    data class PrivateSpaceHeader(
        val isLocked: Boolean = true,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val appLabel: String = ""
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean = false
    }

    // A shortcut to an Android system Settings screen, surfaced only while searching.
    data class SettingTile(
        override val appLabel: String,
        val intentAction: String,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean = false
    }

    // A device contact, surfaced only while searching. [lookupUri] opens the contact card.
    data class Contact(
        override val appLabel: String,
        val lookupUri: String,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean = false
    }

    // Section title for a user-defined folder, shown above the folder's apps in the drawer.
    data class FolderHeader(
        val folderId: String,
        val name: String,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val appLabel: String = name
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean = false
    }

    override fun compareTo(other: AppModel): Int = when {
        key != null && other.key != null -> key!!.compareTo(other.key)
        else -> appLabel.compareTo(other.appLabel, true)
    }
}