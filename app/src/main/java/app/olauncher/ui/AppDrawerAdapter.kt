package app.olauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Folder
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterFolderHeaderBinding
import app.olauncher.databinding.AdapterGroupsSectionBinding
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.data.Prefs
import app.olauncher.helper.FuzzySearch
import app.olauncher.helper.IconManager
import app.olauncher.helper.SearchMode
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showToast
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
    private val appAddToHomeListener: (AppModel) -> Unit = {},
    private val folderManageListener: (String) -> Unit = {},
    private val folderEditListener: (String) -> Unit = {},
    private val folderMoveListener: (String, Boolean) -> Unit = { _, _ -> },
    private val groupsCollapsed: () -> Boolean = { false },
    private val startFolderDrag: (FolderHeaderViewHolder) -> Unit = {},
    private val groupsToggleListener: () -> Unit = {},
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
    // Per-item launch count, used to rank search results by frequency of use.
    private val usageProvider: (AppModel) -> Int = { 0 },
    private val searchModeProvider: () -> Int = { 0 },
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1
        const val VIEW_TYPE_FOLDER_HEADER = 2
        const val VIEW_TYPE_GROUPS_SECTION = 3
        const val VIEW_TYPE_GROUPS_DIVIDER = 4

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user
                        && oldItem.activityClassName == newItem.activityClassName
                        && oldItem.drawerGroupId == newItem.drawerGroupId

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.shortcutId == newItem.shortcutId && oldItem.user == newItem.user
                        && oldItem.appPackage == newItem.appPackage

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                oldItem is AppModel.FolderHeader && newItem is AppModel.FolderHeader ->
                    oldItem.folderId == newItem.folderId

                oldItem is AppModel.GroupsSection && newItem is AppModel.GroupsSection -> true
                oldItem === AppModel.GroupsDivider && newItem === AppModel.GroupsDivider -> true

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
    private var filterGeneration = 0L
    private var queryGeneration = 0L
    private var launchedQueryGeneration = -1L
    private var latestResults: List<AppModel> = emptyList()
    private var preparedLabels: Map<String, FuzzySearch.PreparedLabel> = emptyMap()

    val hasSearchResults: Boolean get() = latestResults.any { isActionable(it) }

    private fun isActionable(item: AppModel) = when (item) {
        is AppModel.App -> item.appPackage.isNotEmpty()
        is AppModel.PinnedShortcut, is AppModel.Contact, is AppModel.SettingTile -> true
        else -> false
    }

    private fun refreshSearchIndex() {
        preparedLabels = (appsList + searchOnly).map { it.appLabel }.distinct()
            .associateWith(FuzzySearch::prepareLabel)
    }

    private val myUserHandle = android.os.Process.myUserHandle()

    // appsList   : flat, searchable list of every app (+ a trailing padding row).
    // folderSections: header + member rows shown above appsList while not searching.
    // currentList: what is currently on screen (sections + apps, or filtered apps).
    private var appsList: List<AppModel> = emptyList()
    private var folderSections: List<AppModel> = emptyList()
    private var drawerFolders: List<Folder> = emptyList()
    private var reorderingGroups = false

    fun showGroupReorderRows(reordering: Boolean) {
        if (reorderingGroups == reordering) return
        reorderingGroups = reordering
        applyFilter(lastQuery)
    }


    // Extra items (settings tiles, contacts) shown only while a query is active.
    private var searchOnly: List<AppModel> = emptyList()

    fun setSearchSources(items: List<AppModel>) {
        searchOnly = items.toList()
        refreshSearchIndex()
        applyFilter(lastQuery)
    }

    fun groupTreeDecoration(): RecyclerView.ItemDecoration = object : RecyclerView.ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            paint.color = parent.context.getColorFromAttr(R.attr.cardStrokeColor)
            paint.strokeWidth = 1.dpToPx().toFloat()
            val trunkInset = 36.dpToPx()
            val branchWidth = 12.dpToPx()
            for (index in 0 until parent.childCount) {
                val row = parent.getChildAt(index)
                val position = parent.getChildAdapterPosition(row)
                val item = currentList.getOrNull(position) ?: continue
                val top = row.y
                val bottom = top + row.height
                val middle = (top + bottom) / 2f
                val rtl = row.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val trunk = (if (rtl) row.right - trunkInset else row.left + trunkInset) + row.translationX
                when (item) {
                    is AppModel.FolderHeader -> {
                        // The 24dp folder icon is vertically centered in the header.
                        val next = currentList.getOrNull(position + 1) as? AppModel.App
                        if (next?.drawerGroupId == item.folderId) {
                            canvas.drawLine(trunk, middle + 15.dpToPx(), trunk, bottom, paint)
                        }
                    }
                    is AppModel.App -> {
                        val group = item.drawerGroupId ?: continue
                        val next = currentList.getOrNull(position + 1) as? AppModel.App
                        val trunkEnd = if (next?.drawerGroupId == group) bottom else middle
                        val branchEnd = trunk + if (rtl) -branchWidth else branchWidth
                        canvas.drawLine(trunk, top, trunk, trunkEnd, paint)
                        canvas.drawLine(trunk, middle, branchEnd, middle, paint)
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            is AppModel.FolderHeader -> VIEW_TYPE_FOLDER_HEADER
            is AppModel.GroupsSection -> VIEW_TYPE_GROUPS_SECTION
            AppModel.GroupsDivider -> VIEW_TYPE_GROUPS_DIVIDER
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

            VIEW_TYPE_GROUPS_SECTION ->
                GroupsSectionViewHolder(AdapterGroupsSectionBinding.inflate(inflater, parent, false))

            VIEW_TYPE_GROUPS_DIVIDER ->
                GroupsDividerViewHolder(inflater.inflate(R.layout.adapter_groups_divider, parent, false))

            else -> ViewHolder(AdapterAppDrawerBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            val appModel = getItem(position)
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> holder.bind(
                    appLabelGravity,
                    privateSpaceToggleListener,
                    privateSpaceSettingsListener,
                )

                is FolderHeaderViewHolder -> if (appModel is AppModel.FolderHeader) {
                    val folderIds = visibleFolderIds().toList()
                    val index = folderIds.indexOf(appModel.folderId)
                    holder.bind(
                        appModel, appLabelGravity, folderManageListener, folderEditListener,
                        startDrag = if (folderIds.size > 1) startFolderDrag else null,
                        moveUp = if (index > 0) ({ folderMoveListener(appModel.folderId, true) }) else null,
                        moveDown = if (index in 0 until folderIds.lastIndex) {
                            { folderMoveListener(appModel.folderId, false) }
                        } else null,
                    )
                }

                is GroupsSectionViewHolder -> if (appModel is AppModel.GroupsSection)
                    holder.bind(appModel.collapsed, groupsToggleListener)

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
    fun applyFilter(query: CharSequence, onCommitted: (() -> Unit)? = null) {
        val q = query.toString()
        if (q != lastQuery) queryGeneration++
        lastQuery = q
        val generation = ++filterGeneration
        val preparedQuery = FuzzySearch.prepareQuery(q)
        isBangSearch = q.startsWith("!")
        autoLaunch = allowAutoLaunch && !q.startsWith(" ")

        val result: MutableList<AppModel> = if (q.isBlank()) {
            ArrayList<AppModel>(folderSections.size + appsList.size).apply {
                if (folderSections.isNotEmpty()) {
                    val collapsed = groupsCollapsed()
                    add(AppModel.GroupsSection(collapsed))
                    if (!collapsed) {
                        addAll(if (reorderingGroups) folderSections.filterIsInstance<AppModel.FolderHeader>() else folderSections)
                    }
                    add(AppModel.GroupsDivider)
                }
                addAll(appsList)
            }
        } else {
            // Rank matching names by quality, usage, then name.
            val mode = SearchMode.fromValue(searchModeProvider())
            val scored = ArrayList<Pair<AppModel, Int>>(appsList.size + searchOnly.size)
            for (item in appsList) {
                if (item is AppModel.PrivateSpaceHeader || item is AppModel.FolderHeader) continue
                val s = FuzzySearch.score(preparedLabels.getValue(item.appLabel), preparedQuery, mode)
                if (s >= 0) scored.add(item to s)
            }
            for (item in searchOnly) {
                val s = FuzzySearch.scoreStrict(preparedLabels.getValue(item.appLabel), preparedQuery, mode)
                if (s >= 0) scored.add(item to s)
            }

            val usageCounts = scored.associate { it.first to usageProvider(it.first) }
            scored.asSequence()
                .sortedWith(
                    compareByDescending<Pair<AppModel, Int>> { it.second }
                        .thenByDescending { usageCounts.getValue(it.first) }
                        .thenBy { preparedLabels.getValue(it.first.appLabel).sortKey }
                )
                .map { it.first }
                .toMutableList()
        }

        latestResults = result.toList()
        submitList(latestResults) {
            if (filterGeneration != generation) return@submitList
            // Models may be identical across queries; their highlighted presentation is not.
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount, "query")
            autoLaunch(preparedQuery)
            if (!firstNonEmptyCommitDone && result.isNotEmpty()) {
                firstNonEmptyCommitDone = true
                onFirstNonEmptyCommit?.invoke()
            }
            onCommitted?.invoke()
        }
    }

    private fun autoLaunch(query: FuzzySearch.PreparedQuery) {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && currentList.isNotEmpty()
                && lastQuery.isNotBlank()
                && currentList[0] is AppModel.App
                && launchedQueryGeneration != queryGeneration
            ) {
                val app = currentList[0]
                val label = preparedLabels.getValue(app.appLabel)
                if (FuzzySearch.score(label, query) < 0) return
                launchedQueryGeneration = queryGeneration
                appClickListener(app)
            }
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
        this.appsList = appsList.toList() + paddingApp()
        refreshSearchIndex()
        updateGroups(emptyList())
    }

    // Sets app list and folders for the main launcher drawer
    fun setAppList(appsList: MutableList<AppModel>, folders: List<Folder>) {
        this.appsList = appsList.toList() + paddingApp()
        refreshSearchIndex()
        updateGroups(folders)
    }

    fun updateGroups(folders: List<Folder>, onCommitted: (() -> Unit)? = null) {
        drawerFolders = folders
        folderSections = buildFolderSections(folders, appsList)
        applyFilter(lastQuery, onCommitted)
    }

    fun removeApp(app: AppModel) {
        appsList = appsList.filterNot { it.appPackage == app.appPackage && it.user == app.user }
        refreshSearchIndex()
        updateGroups(drawerFolders)
    }

    fun visibleFolderIds(): Set<String> = folderSections.filterIsInstance<AppModel.FolderHeader>()
        .mapTo(linkedSetOf()) { it.folderId }

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
            if (members.isEmpty() && folder.apps.isNotEmpty()) continue
            out.add(AppModel.FolderHeader(folder.id, folder.name))
            out.addAll(members.map { it.copy(drawerGroupId = folder.id) })
        }
        return out
    }

    fun launchFirstInList() {
        val first = latestResults.firstOrNull(::isActionable) ?: return
        if (launchedQueryGeneration == queryGeneration) return
        launchedQueryGeneration = queryGeneration
        appClickListener(first)
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

    class GroupsSectionViewHolder(private val binding: AdapterGroupsSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(collapsed: Boolean, toggle: () -> Unit) = with(binding.root) {
            val icon = if (collapsed) R.drawable.ic_section_expand else R.drawable.ic_section_collapse
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)
            val action = if (collapsed) R.string.settings_expand_section else R.string.settings_collapse_section
            ViewCompat.setAccessibilityHeading(this, true)
            setOnClickListener { toggle() }
            ViewCompat.replaceAccessibilityAction(
                this,
                AccessibilityActionCompat.ACTION_CLICK,
                context.getString(action, text),
                null,
            )
        }
    }

    class GroupsDividerViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class FolderHeaderViewHolder(private val binding: AdapterFolderHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var folderId: String = ""
            private set
        private val moveActions = mutableListOf<Int>()
        private val rowBackground = binding.root.background

        fun setDragging(dragging: Boolean) {
            if (dragging) binding.root.setBackgroundResource(R.drawable.rounded_dialog_surface)
            else binding.root.background = rowBackground
        }

        @SuppressLint("ClickableViewAccessibility") // Reordering also has explicit accessibility actions.
        fun bind(
            folder: AppModel.FolderHeader,
            appLabelGravity: Int,
            manageListener: (String) -> Unit,
            editListener: (String) -> Unit,
            startDrag: ((FolderHeaderViewHolder) -> Unit)?,
            moveUp: (() -> Unit)?,
            moveDown: (() -> Unit)?,
        ) = with(binding) {
            folderId = folder.folderId
            folderTitle.text = folder.name
            folderTitle.gravity = appLabelGravity
            root.setOnClickListener { manageListener(folder.folderId) }
            root.setOnLongClickListener {
                editListener(folder.folderId)
                true
            }
            ViewCompat.replaceAccessibilityAction(
                root, AccessibilityActionCompat.ACTION_LONG_CLICK, root.context.getString(R.string.edit_group_apps), null
            )
            dragHandle.isVisible = startDrag != null
            for (handle in listOf(folderIcon, dragHandle)) {
                handle.setOnClickListener { root.performClick() }
                handle.isFocusable = false
                handle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag?.invoke(this@FolderHeaderViewHolder)
                    false
                }
            }
            ViewCompat.setAccessibilityHeading(root, true)
            moveActions.forEach { ViewCompat.removeAccessibilityAction(root, it) }
            moveActions.clear()

            fun addMoveAction(label: Int, move: (() -> Unit)?) {
                if (move == null) return
                moveActions += ViewCompat.addAccessibilityAction(root, root.context.getString(label)) { _, _ ->
                    move()
                    true
                }
            }
            addMoveAction(R.string.move_group_up, moveUp)
            addMoveAction(R.string.move_group_down, moveDown)
        }
    }

    inner class ViewHolder(private val binding: AdapterAppDrawerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var renameWatcher: TextWatcher? = null
        private val prefs = Prefs(binding.root.context)

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
            // Use identical icon bounds: source vectors mix 22dp and 24dp sizes.
            val actionIconSize = (24 * root.resources.displayMetrics.density).toInt()
            listOf(appDelete, appRename, appAddToHome, appFolder, appHide, appInfo, appMenuClose).forEach { action ->
                val top = action.compoundDrawablesRelative[1]
                top?.setBounds(0, 0, actionIconSize, actionIconSize)
                action.setCompoundDrawablesRelative(null, top, null, null)
            }
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE
            val inGroup = (appModel as? AppModel.App)?.drawerGroupId != null
            appTitle.updatePaddingRelative(start = (if (inGroup) 56 else 24).dpToPx())

            // Show indicators in title based on app type and state
            val builder = SpannableStringBuilder(appModel.appLabel)

            // Highlight search query matches
            val queryText = lastQuery.trim()
            if (queryText.isNotEmpty()) {
                val labelLower = appModel.appLabel.lowercase()
                var startIdx = labelLower.indexOf(queryText.lowercase())
                if (startIdx >= 0) {
                    while (startIdx >= 0) {
                        val endIdx = startIdx + queryText.length
                        builder.setSpan(StyleSpan(Typeface.BOLD), startIdx, endIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(UnderlineSpan(), startIdx, endIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        startIdx = labelLower.indexOf(queryText.lowercase(), endIdx)
                    }
                } else {
                    val tokens = queryText.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    for (token in tokens) {
                        var idx = labelLower.indexOf(token)
                        while (idx >= 0) {
                            val end = idx + token.length
                            builder.setSpan(StyleSpan(Typeface.BOLD), idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            builder.setSpan(UnderlineSpan(), idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            idx = labelLower.indexOf(token, end)
                        }
                    }
                }
            }

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
                builder.append(if (showLock) "\u00A0" else "  ")
                builder.append(appTitle.context.getString(R.string.app_limit_level_short, level))

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
                    // Grouping and home assignment are only meaningful in the main launch drawer, for real apps.
                    appFolder.isVisible = flag == Constants.FLAG_LAUNCH_APP && appModel is AppModel.App
                    appAddToHome.isVisible = flag == Constants.FLAG_LAUNCH_APP && appModel.appPackage.isNotEmpty()
                    val p = Prefs(root.context)
                    val maxC = p.homeAppsNum.coerceIn(1, 8)
                    val freeS = (1..maxC).firstOrNull { p.getAppName(it).isEmpty() && !p.getIsFolder(it) }
                        ?: if (p.homeAppsNum < 8) p.homeAppsNum + 1 else 0
                    if (freeS > 0) {
                        appAddToHome.text = root.context.getString(R.string.add_to_home_slot, freeS)
                    } else {
                        appAddToHome.text = root.context.getString(R.string.add_to_home)
                    }
                }
                true
            }

            appAddToHome.setOnClickListener {
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                val ctx = root.context
                val currentPrefs = Prefs(ctx)
                val currentMax = currentPrefs.homeAppsNum.coerceIn(1, 8)
                val targetSlot = (1..currentMax).firstOrNull { currentPrefs.getAppName(it).isEmpty() && !currentPrefs.getIsFolder(it) }
                    ?: if (currentPrefs.homeAppsNum < 8) {
                        currentPrefs.homeAppsNum = currentPrefs.homeAppsNum + 1
                        currentPrefs.homeAppsNum
                    } else 0

                if (targetSlot > 0) {
                    currentPrefs.setAppName(targetSlot, appModel.appLabel)
                    currentPrefs.setAppPackage(targetSlot, appModel.appPackage)
                    currentPrefs.setAppUser(targetSlot, appModel.user.toString())
                    if (appModel is AppModel.App) {
                        currentPrefs.setAppActivityClassName(targetSlot, appModel.activityClassName)
                    }
                    ctx.showToast(ctx.getString(R.string.added_to_home, targetSlot))
                }
                appAddToHomeListener(appModel)
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
            renameWatcher?.let(etAppRename::removeTextChangedListener)
            renameWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    etAppRename.hint = appModel.appLabel
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
            }
            etAppRename.addTextChangedListener(renameWatcher)
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        renameLayout.visibility = View.GONE
                    }
                    true
                } else false
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
