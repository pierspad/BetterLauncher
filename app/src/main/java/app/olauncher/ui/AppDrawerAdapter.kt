package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.LeadingMarginSpan
import androidx.core.content.ContextCompat
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
import app.olauncher.helper.FuzzySearch
import app.olauncher.data.Prefs
import app.olauncher.helper.IconManager
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val isAppLocked: (AppModel) -> Boolean = { false },
    private val isAppLimited: (AppModel) -> Boolean = { false },
    private val appLimitLevel: (AppModel) -> Int = { 0 },
    private val appCooldownRemainingMillis: (AppModel) -> Long = { 0L },
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appFolderListener: (AppModel) -> Unit = {},
    private val folderManageListener: (String) -> Unit = {},
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
    // Per-item launch count, used to rank search results by frequency of use.
    private val usageProvider: (AppModel) -> Int = { 0 },
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

                oldItem is AppModel.SettingTile && newItem is AppModel.SettingTile ->
                    oldItem.intentAction == newItem.intentAction

                oldItem is AppModel.Contact && newItem is AppModel.Contact ->
                    oldItem.lookupUri == newItem.lookupUri

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    var allowAutoLaunch = true

    // Invoked once, after the first non-empty list is actually committed. The host uses it
    // to play the drawer entrance animation against real rows. Driving the animation from
    // here — instead of a persistent RecyclerView.layoutAnimation that evaluates while
    // submitList() is still diffing on a background thread — avoids the race that could
    // leave rows stuck at the animation's start alpha (0), i.e. an all-black drawer.
    var onFirstNonEmptyCommit: (() -> Unit)? = null
    private var firstNonEmptyCommitDone = false

    // The query is re-applied synchronously every time the underlying data changes,
    // so a query typed before the app list finished loading can never get stuck on an
    // empty result. See [applyFilter].
    private var lastQuery: String = ""

    private val myUserHandle = android.os.Process.myUserHandle()

    // appsList   : flat, searchable list of every app (+ a trailing padding row).
    // folderSections: header + member rows shown above appsList while not searching.
    // appFilteredList: what is currently on screen (sections + apps, or filtered apps).
    var appsList: MutableList<AppModel> = mutableListOf()
    private var folderSections: List<AppModel> = emptyList()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    // Extra items (settings tiles, contacts) shown only while a query is active.
    private var searchOnly: List<AppModel> = emptyList()

    fun setSearchSources(items: List<AppModel>) {
        searchOnly = items
        applyFilter(lastQuery)
    }

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
                    isAppLimited,
                    appLimitLevel,
                    appCooldownRemainingMillis,
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
     * Filters the app list based on [query] and submits the filtered results.
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
            // Apps use the loose fuzzy matcher (subsequence ok). The secondary sources
            // (settings tiles, contacts) use the strict matcher so a loose subsequence like
            // "pint" can't drag in "Opzioni sviluppatore". Then rank by match quality,
            // then usage, then name.
            val scored = ArrayList<Pair<AppModel, Int>>(appsList.size + searchOnly.size)
            for (item in appsList) {
                if (item is AppModel.PrivateSpaceHeader || item is AppModel.FolderHeader) continue
                val s = FuzzySearch.score(item.appLabel, q)
                if (s >= 0) scored.add(item to s)
            }
            for (item in searchOnly) {
                val s = FuzzySearch.scoreStrict(item.appLabel, q)
                if (s >= 0) scored.add(item to s)
            }

            scored.asSequence()
                .sortedWith(
                    compareByDescending<Pair<AppModel, Int>> { it.second }
                        .thenByDescending { usageProvider(it.first) }
                        .thenBy { it.first.appLabel.lowercase() }
                )
                .map { it.first }
                .toMutableList()
        }

        appFilteredList = result
        submitList(result) {
            autoLaunch()
            if (!firstNonEmptyCommitDone && result.isNotEmpty()) {
                firstNonEmptyCommitDone = true
                onFirstNonEmptyCommit?.invoke()
            }
        }
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

    private fun paddingApp(): AppModel.App = AppModel.App(
        appLabel = "",
        key = null,
        appPackage = "",
        activityClassName = "",
        isNew = false,
        user = myUserHandle,
    )

    // Sets app list for flat-list views (hidden/locked apps, pickers)
    fun setAppList(appsList: MutableList<AppModel>) {
        appsList.add(paddingApp())
        this.appsList = appsList
        this.folderSections = emptyList()
        applyFilter(lastQuery)
    }

    // Sets app list and folders for the main launcher drawer
    fun setAppList(appsList: MutableList<AppModel>, folders: List<Folder>) {
        appsList.add(paddingApp())
        this.appsList = appsList
        this.folderSections = buildFolderSections(folders, appsList)
        applyFilter(lastQuery)
    }

    // Groups apps under their respective folder headers
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

            val prefs = Prefs(folderTitle.context)
            if (prefs.showDrawerIcons) {
                val icon = ContextCompat.getDrawable(folderTitle.context, R.drawable.ic_sc_folder)
                icon?.let {
                    val iconSize = (folderTitle.textSize * 1.2).toInt()
                    it.setBounds(0, 0, iconSize, iconSize)
                    it.mutate().setTint(folderTitle.currentTextColor)
                    folderTitle.setCompoundDrawables(it, null, null, null)
                    folderTitle.compoundDrawablePadding = (8 * folderTitle.context.resources.displayMetrics.density).toInt()
                }
            } else {
                folderTitle.setCompoundDrawables(null, null, null, null)
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
            isAppLimited: (AppModel) -> Boolean,
            appLimitLevel: (AppModel) -> Int,
            appCooldownRemainingMillis: (AppModel) -> Long,
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
            val builder = SpannableStringBuilder(appModel.appLabel)
            if (appModel.isNew) builder.append(" ✦")

            val showLock = isAppLocked(appModel) &&
                (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_LOCKED_APPS)
            val showLimit = isAppLimited(appModel) &&
                (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_LIMITED_APPS)

            if (showLock) {
                builder.append("  ")
                val lockStart = builder.length
                builder.append(" ") // placeholder for lock icon
                val lockEnd = builder.length

                val context = appTitle.context
                val lockDrawable = ContextCompat.getDrawable(context, R.drawable.ic_lock)
                if (lockDrawable != null) {
                    val textColor = appTitle.currentTextColor
                    lockDrawable.mutate().setTint(textColor)
                    val iconSize = (appTitle.textSize * 0.75).toInt()
                    lockDrawable.setBounds(0, 0, iconSize, iconSize)
                    builder.setSpan(
                        CenteredImageSpan(lockDrawable),
                        lockStart,
                        lockEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            if (showLimit) {
                val level = appLimitLevel(appModel)
                if (showLock) {
                    builder.append("\u00A0liv.\u00A0$level")
                } else {
                    builder.append("  liv.\u00A0$level")
                }

                val remainingMs = appCooldownRemainingMillis(appModel)
                if (remainingMs > 0) {
                    val minutes = (remainingMs / 60_000L).coerceAtLeast(1)
                    val timeStr = if (minutes >= 60) {
                        val hours = minutes / 60
                        val mins = minutes % 60
                        "${hours}h\u00A0${mins}m"
                    } else {
                        "${minutes}m"
                    }
                    builder.append("  ⏳︎\u00A0$timeStr")
                } else {
                    builder.append("  ⏳︎")
                }
            }

            val indent = (24 * appTitle.context.resources.displayMetrics.density).toInt()
            builder.setSpan(
                LeadingMarginSpan.Standard(0, indent),
                0,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            appTitle.text = builder
            appTitle.gravity = appLabelGravity

            val prefs = Prefs(appTitle.context)
            if (prefs.showDrawerIcons) {
                val icon = IconManager.getModelIcon(appTitle.context, appModel, appTitle.currentTextColor)
                if (icon != null) {
                    val iconSize = (appTitle.textSize * 1.2).toInt()
                    icon.setBounds(0, 0, iconSize, iconSize)
                    appTitle.setCompoundDrawables(icon, null, null, null)
                    appTitle.compoundDrawablePadding = (8 * appTitle.context.resources.displayMetrics.density).toInt()
                } else {
                    appTitle.setCompoundDrawables(null, null, null, null)
                }
            } else {
                appTitle.setCompoundDrawables(null, null, null, null)
            }

            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            appTitle.setOnClickListener { clickListener(appModel) }

            appTitle.setOnLongClickListener {
                // In lock/limit selection mode a tap toggles the state; no hide/rename/delete menu.
                if (flag == Constants.FLAG_LOCKED_APPS || flag == Constants.FLAG_LIMITED_APPS)
                    return@setOnLongClickListener true
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
