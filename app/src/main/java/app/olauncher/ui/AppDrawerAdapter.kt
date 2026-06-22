package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Folder
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterFolderHeaderBinding
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val isAppLocked: (AppModel) -> Boolean = { false },
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appFolderListener: (AppModel) -> Unit = {},
    private val folderManageListener: (String) -> Unit = {},
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1
        const val VIEW_TYPE_FOLDER_HEADER = 2

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.shortcutId == newItem.shortcutId && oldItem.user == newItem.user

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                oldItem is AppModel.FolderHeader && newItem is AppModel.FolderHeader ->
                    oldItem.folderId == newItem.folderId

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    var allowAutoLaunch = true

    // The query is re-applied synchronously every time the underlying data changes,
    // so a query typed before the app list finished loading can never get stuck on an
    // empty result. See [applyFilter].
    private var lastQuery: String = ""

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")
    private val myUserHandle = android.os.Process.myUserHandle()

    // appsList   : flat, searchable list of every app (+ a trailing padding row).
    // folderSections: header + member rows shown above appsList while not searching.
    // appFilteredList: what is currently on screen (sections + apps, or filtered apps).
    var appsList: MutableList<AppModel> = mutableListOf()
    private var folderSections: List<AppModel> = emptyList()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun getItemViewType(position: Int): Int {
        return when (appFilteredList.getOrNull(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            is AppModel.FolderHeader -> VIEW_TYPE_FOLDER_HEADER
            else -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PRIVATE_HEADER ->
                PrivateSpaceHeaderViewHolder(AdapterPrivateSpaceHeaderBinding.inflate(inflater, parent, false))

            VIEW_TYPE_FOLDER_HEADER ->
                FolderHeaderViewHolder(AdapterFolderHeaderBinding.inflate(inflater, parent, false))

            else -> ViewHolder(AdapterAppDrawerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            val appModel = appFilteredList.getOrNull(holder.bindingAdapterPosition) ?: return
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> holder.bind(
                    appLabelGravity,
                    privateSpaceToggleListener,
                    privateSpaceSettingsListener,
                )

                is FolderHeaderViewHolder -> if (appModel is AppModel.FolderHeader)
                    holder.bind(appModel, appLabelGravity, folderManageListener)

                is ViewHolder -> holder.bind(
                    flag,
                    appLabelGravity,
                    myUserHandle,
                    appModel,
                    isAppLocked,
                    appClickListener,
                    appDeleteListener,
                    appInfoListener,
                    appHideListener,
                    appRenameListener,
                    appFolderListener,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Filters synchronously on the main thread (the list is at most a few hundred entries,
     * so this is sub-millisecond) and re-renders. Doing this on the main thread — instead of
     * the old background [android.widget.Filter] — removes the data race between the worker
     * thread reading [appsList] and the UI thread rebuilding it, which is what made the drawer
     * occasionally show no results for a valid query.
     */
    fun applyFilter(query: CharSequence) {
        val q = query.toString()
        lastQuery = q
        isBangSearch = q.startsWith("!")
        autoLaunch = allowAutoLaunch && !q.startsWith(" ")

        val result: MutableList<AppModel> = if (q.isBlank()) {
            ArrayList<AppModel>(folderSections.size + appsList.size).apply {
                addAll(folderSections)
                addAll(appsList)
            }
        } else {
            appsList.filter {
                it !is AppModel.PrivateSpaceHeader &&
                    it !is AppModel.FolderHeader &&
                    appLabelMatches(it.appLabel, q)
            }.toMutableList()
        }

        appFilteredList = result
        submitList(result) { autoLaunch() }
    }

    private fun autoLaunch() {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && appFilteredList.isNotEmpty()
                && appFilteredList[0] is AppModel.App
            ) appClickListener(appFilteredList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun appLabelMatches(appLabel: String, charSearch: String): Boolean {
        if (appLabel.contains(charSearch.trim(), true)) return true
        val query = charSearch.normalizeForSearch()
        return query.isNotEmpty() && appLabel.normalizeForSearch().contains(query, true)
    }

    private fun CharSequence.normalizeForSearch(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")

    private fun paddingApp(): AppModel.App = AppModel.App(
        appLabel = "",
        key = null,
        appPackage = "",
        activityClassName = "",
        isNew = false,
        user = myUserHandle,
    )

    // Used for the hidden/lock/pick flows: a flat list with no folder sections.
    fun setAppList(appsList: MutableList<AppModel>) {
        appsList.add(paddingApp())
        this.appsList = appsList
        this.folderSections = emptyList()
        applyFilter(lastQuery)
    }

    // Used for the main launch drawer: the same flat list plus folder sections on top.
    fun setAppList(appsList: MutableList<AppModel>, folders: List<Folder>) {
        appsList.add(paddingApp())
        this.appsList = appsList
        this.folderSections = buildFolderSections(folders, appsList)
        applyFilter(lastQuery)
    }

    // Resolves each folder's member keys ("package|user") against the live app list and
    // emits [FolderHeader, member, member, …]. Unresolved/uninstalled members are skipped,
    // and folders that end up empty are omitted entirely.
    private fun buildFolderSections(folders: List<Folder>, apps: List<AppModel>): List<AppModel> {
        if (folders.isEmpty()) return emptyList()
        val byKey = HashMap<String, AppModel.App>(apps.size)
        for (app in apps) {
            if (app is AppModel.App && app.appPackage.isNotEmpty())
                byKey[app.appPackage + "|" + app.user.toString()] = app
        }
        val out = ArrayList<AppModel>()
        for (folder in folders) {
            val members = folder.apps.mapNotNull { byKey[it] }
            if (members.isEmpty()) continue
            out.add(AppModel.FolderHeader(folder.id, folder.name))
            out.addAll(members)
        }
        return out
    }

    fun launchFirstInList() {
        val first = appFilteredList.firstOrNull { it is AppModel.App || it is AppModel.PinnedShortcut }
        if (first != null) appClickListener(first)
    }

    class PrivateSpaceHeaderViewHolder(private val binding: AdapterPrivateSpaceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            toggleListener: () -> Unit,
            settingsListener: () -> Unit,
        ) = with(binding) {
            privateSpaceTitle.gravity = appLabelGravity
            privateSpaceTitle.setOnClickListener { toggleListener() }
            privateSpaceTitle.setOnLongClickListener {
                settingsListener()
                true
            }
        }
    }

    class FolderHeaderViewHolder(private val binding: AdapterFolderHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            folder: AppModel.FolderHeader,
            appLabelGravity: Int,
            manageListener: (String) -> Unit,
        ) = with(binding) {
            folderTitle.text = folder.name
            folderTitle.gravity = appLabelGravity
            folderTitle.setOnClickListener { manageListener(folder.folderId) }
            folderTitle.setOnLongClickListener {
                manageListener(folder.folderId)
                true
            }
        }
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            isAppLocked: (AppModel) -> Boolean,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            appFolderListener: (AppModel) -> Unit,
        ) = with(binding) {
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE

            // Show indicators in title based on app type and state
            appTitle.text = buildString {
                append(appModel.appLabel)
                if (appModel.isNew) append(" ✦")
                if (flag == Constants.FLAG_LOCKED_APPS && isAppLocked(appModel)) append("  🔒")
            }
            appTitle.gravity = appLabelGravity
            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            appTitle.setOnClickListener { clickListener(appModel) }

            appTitle.setOnLongClickListener {
                // In lock-selection mode a tap toggles the lock; no hide/rename/delete menu.
                if (flag == Constants.FLAG_LOCKED_APPS) return@setOnLongClickListener true
                if (appModel.appPackage.isNotEmpty()) {
                    appDelete.alpha = when (
                        appModel is AppModel.PinnedShortcut || !root.context.isSystemApp(appModel.appPackage, appModel.user)
                    ) {
                        true -> 1.0f
                        false -> 0.5f
                    }
                    appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                        root.context.getString(R.string.adapter_show)
                    else
                        root.context.getString(R.string.adapter_hide)
                    appTitle.visibility = View.INVISIBLE
                    appHide.alpha = when (appModel is AppModel.PinnedShortcut) {
                        true -> 0.5f
                        false -> 1.0f
                    }
                    appHideLayout.visibility = View.VISIBLE
                    // Only allow renaming non hidden apps
                    appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                    // Grouping is only meaningful in the main launch drawer, for real apps.
                    appFolder.isVisible = flag == Constants.FLAG_LAUNCH_APP && appModel is AppModel.App
                }
                true
            }

            appFolder.setOnClickListener {
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                appFolderListener(appModel)
            }

            // Configure rename behavior
            appRename.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.visibility = View.VISIBLE
                    appHideLayout.visibility = View.GONE
                    etAppRename.showKeyboard()
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }
            etAppRename.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                appTitle.visibility = if (hasFocus) View.INVISIBLE else View.VISIBLE
            }
            etAppRename.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    etAppRename.hint = ""
                }
            })
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        renameLayout.visibility = View.GONE
                    }
                    true
                }
                false
            }
            tvSaveRename.setOnClickListener {
                etAppRename.hideKeyboard()
                val renameLabel = etAppRename.text.toString().trim()
                if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                    appRenameListener(appModel, renameLabel)
                    renameLayout.visibility = View.GONE
                } else {
                    appRenameListener(
                        appModel,
                        getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    )
                    renameLayout.visibility = View.GONE
                }
            }
            appInfo.setOnClickListener { appInfoListener(appModel) }
            appDelete.setOnClickListener { appDeleteListener(appModel) }
            appMenuClose.setOnClickListener {
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appRenameClose.setOnClickListener {
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
        }

        private fun getAppName(context: Context, appPackage: String, user: UserHandle): String {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            return try {
                val activityList = launcherApps.getActivityList(appPackage, user)
                if (activityList.isNotEmpty()) {
                    activityList.first().label.toString()
                } else {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(appPackage, 0)
                    ).toString()
                }
            } catch (_: Exception) {
                "" // As a fallback, display an empty string.
            }
        }
    }
}
