package app.olauncher.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.result.contract.ActivityResultContracts
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.BaseInputConnection
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.widget.SearchView
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.olauncher.MainViewModel
import app.olauncher.helper.FontHelper
import app.olauncher.helper.IconManager
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Folder
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogFolderInputBinding
import app.olauncher.databinding.DialogListPickerBinding
import app.olauncher.databinding.DialogMessageBinding
import app.olauncher.databinding.FragmentAppDrawerBinding
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.view.ViewCompat
import app.olauncher.helper.AndroidSettingsCatalog
import app.olauncher.helper.ContactsHelper
import app.olauncher.helper.SearchMode
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.dpToPx
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.scrimColor
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import java.util.UUID

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var groupAppsEditor: android.app.Dialog? = null
    private var groupDragHelper: ItemTouchHelper? = null
    private var groupDragActive = false

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var settingTiles: List<AppModel> = emptyList()
    private var contactItems: List<AppModel> = emptyList()

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.loadDrawerContacts()
            else {
                prefs.searchContactsEnabled = false
                contactItems = emptyList()
                updateSearchSources()
            }
        }
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false

        private val viewModel: MainViewModel by activityViewModels()
    private var searchOptionsPopup: android.widget.PopupWindow? = null
    private var alertJob: kotlinx.coroutines.Job? = null
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
        }

        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
        setupDrawerSearchSources()
    }

    // Settings tiles + contacts are surfaced only in the main drawer, and only while
    // a query is typed (the adapter hides them otherwise). Each category is opt-in and now
    // toggled from the drawer itself (the gear/tune button) rather than from Settings.
    private fun setupDrawerSearchSources() {
        if (flag != Constants.FLAG_LAUNCH_APP) return
        binding.searchOptions.visibility = View.VISIBLE
        binding.newGroup.isVisible = binding.search.query.isBlank()
        binding.newGroup.isEnabled = currentAppList != null
        binding.newGroup.setOnClickListener {
            binding.search.hideKeyboard()
            showCreateFolderDialog(null)
        }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.newGroup, getString(R.string.new_folder))
        binding.searchOptions.setOnClickListener { showSearchOptionsMenu() }
        refreshSettingTiles()
        refreshContacts()
    }

    private fun refreshSettingTiles() {
        settingTiles = if (prefs.searchSettingsEnabled)
            AndroidSettingsCatalog.tiles(requireContext())
        else
            emptyList()
        updateSearchSources()
    }

    // Loads contacts when enabled (requesting permission if needed) and clears them otherwise.
    private fun refreshContacts() {
        if (prefs.searchContactsEnabled) {
            if (ContactsHelper.hasPermission(requireContext()))
                viewModel.loadDrawerContacts()
            else
                requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        } else {
            contactItems = emptyList()
            updateSearchSources()
        }
    }

    // Custom rounded popup anchored to the (right-aligned) tune button. A plain PopupMenu
    // grows rightwards and runs off-screen, so we right-align this one to open leftwards.
    private fun showSearchOptionsMenu() {
        val content = layoutInflater.inflate(R.layout.popup_search_options, null)
        val settingsCheck = content.findViewById<View>(R.id.optSettingsCheck)
        val contactsCheck = content.findViewById<View>(R.id.optContactsCheck)
        val searchModeSub = content.findViewById<TextView>(R.id.optSearchModeSub)

        fun render() {
            settingsCheck.visibility = if (prefs.searchSettingsEnabled) View.VISIBLE else View.INVISIBLE
            contactsCheck.visibility = if (prefs.searchContactsEnabled) View.VISIBLE else View.INVISIBLE
            val currentMode = SearchMode.fromValue(prefs.searchMode)
            searchModeSub.text = when (currentMode) {
                SearchMode.PRECISE -> getString(R.string.search_mode_smart)
                SearchMode.LOOSE_FUZZY -> getString(R.string.search_mode_loose)
            }
        }
        render()

        val popup = android.widget.PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true, // focusable: dismiss on outside touch / back
        ).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = 16f
        }
        searchOptionsPopup = popup

        content.findViewById<View>(R.id.optSettings).setOnClickListener {
            prefs.searchSettingsEnabled = !prefs.searchSettingsEnabled
            render()
            refreshSettingTiles()
        }
        content.findViewById<View>(R.id.optContacts).setOnClickListener {
            prefs.searchContactsEnabled = !prefs.searchContactsEnabled
            render()
            refreshContacts()
        }
        content.findViewById<View>(R.id.optSearchMode).setOnClickListener {
            popup.dismiss()
            showSearchModeDialog()
        }

        // Right-align the popup with the anchor so it expands to the left, on-screen.
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOffset = binding.searchOptions.width - content.measuredWidth
        popup.showAsDropDown(binding.searchOptions, xOffset, 0)
    }

    private fun showSearchModeDialog() {
        if (!isAdded) return
        val view = layoutInflater.inflate(R.layout.dialog_search_mode, null)
        val checkSmart = view.findViewById<View>(R.id.checkSmart)
        val checkLoose = view.findViewById<View>(R.id.checkLoose)
        val btnCancel = view.findViewById<View>(R.id.btnCancel)

        val currentMode = SearchMode.fromValue(prefs.searchMode)
        checkSmart.visibility = if (currentMode == SearchMode.PRECISE) View.VISIBLE else View.INVISIBLE
        checkLoose.visibility = if (currentMode == SearchMode.LOOSE_FUZZY) View.VISIBLE else View.INVISIBLE

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fun selectMode(mode: SearchMode) {
            prefs.searchMode = mode.value
            if (_binding != null) {
                adapter.applyFilter(binding.search.query?.toString() ?: "")
            }
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.modeSmart).setOnClickListener { selectMode(SearchMode.PRECISE) }
        view.findViewById<View>(R.id.modeLoose).setOnClickListener { selectMode(SearchMode.LOOSE_FUZZY) }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateSearchSources() {
        val contacts = if (prefs.searchContactsEnabled && ContactsHelper.hasPermission(requireContext()))
            contactItems else emptyList()
        adapter.setSearchSources(settingTiles + contacts)
    }

    private fun initViews() {
        applyOpacityScrim()
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag == Constants.FLAG_LOCKED_APPS)
            binding.search.queryHint = getString(R.string.lock_apps)
        else if (flag == Constants.FLAG_LIMITED_APPS)
            binding.search.queryHint = getString(R.string.limit_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP
            || flag in Constants.FLAG_SET_SHORTCUT_ICON_1..Constants.FLAG_SET_SHORTCUT_ICON_6)
            binding.search.queryHint = "Please select an app"
        try {
            val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
            if (searchTextView != null) searchTextView.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Darkens the wallpaper behind the drawer. Alpha is baked into the background color
    // (not View.alpha) and the scrim stays VISIBLE for consistent, flicker-free rendering.
    private fun applyOpacityScrim() {
        binding.drawerOpacityScrim.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private fun initSearch() {
        binding.btnClearSearch?.setOnClickListener {
            binding.search.setQuery("", false)
        }
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else if (!adapter.hasSearchResults)
                    requireContext().openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    binding.btnClearSearch?.isVisible = newText.isNotEmpty()
                    binding.newGroup.isVisible = flag == Constants.FLAG_LAUNCH_APP && newText.isBlank()
                    adapter.allowAutoLaunch = !isSearchComposing()
                    adapter.applyFilter(newText)
                    binding.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    // While an IME is composing (e.g. typing pinyin before selecting a Chinese character),
    // the search field holds a composing region. Don't auto-launch a single match during
    // composition — the typed letters are not a final query (issue #629).
    private fun isSearchComposing(): Boolean {
        val text = binding.search.findViewById<TextView>(R.id.search_src_text)?.text
        if (text is Spannable) {
            val start = BaseInputConnection.getComposingSpanStart(text)
            val end = BaseInputConnection.getComposingSpanEnd(text)
            return start in 0 until end
        }
        return false
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            isAppLocked = { viewModel.isAppLocked(it) },
            isAppLimited = { viewModel.isAppLimited(it) },
            appLimitLevel = { viewModel.appLimitLevel(it) },
            appCooldownRemainingMillis = { viewModel.appCooldownRemainingMillis(it) },
            appClickListener = { appModel ->
                if (appModel is AppModel.SettingTile) {
                    AndroidSettingsCatalog.launchSettingTile(requireContext(), appModel)
                    findNavController().popBackStack(R.id.mainFragment, false)
                } else if (appModel is AppModel.Contact) {
                    ContactsHelper.openContact(requireContext(), appModel)
                    findNavController().popBackStack(R.id.mainFragment, false)
                } else if (flag == Constants.FLAG_LOCKED_APPS) {
                    if (appModel is AppModel.App) {
                        val nowLocked = viewModel.toggleAppLock(appModel)
                        requireContext().showToast(
                            getString(if (nowLocked) R.string.app_locked_toast else R.string.app_unlocked_toast)
                        )
                        adapter.notifyDataSetChanged()
                    }
                } else if (flag == Constants.FLAG_LIMITED_APPS) {
                    if (appModel is AppModel.App) {
                        when (val result = viewModel.toggleAppLimit(appModel)) {
                            is MainViewModel.ToggleLimitResult.Success -> {
                                requireContext().showToast(
                                    getString(if (result.nowLimited) R.string.app_limited_toast else R.string.app_unlimited_toast)
                                )
                                adapter.notifyDataSetChanged()
                            }
                            is MainViewModel.ToggleLimitResult.PreventedBanned -> {
                                showCustomAlert(getString(R.string.cannot_remove_limit_banned))
                            }
                            is MainViewModel.ToggleLimitResult.PreventedCompulsiveness -> {
                                showCustomAlert(getString(R.string.cannot_remove_limit_compulsiveness, result.level))
                            }
                        }
                    }
                } else {
                    viewModel.selectedApp(appModel, flag)
                    if (appModel.appPackage.isEmpty() && flag != Constants.FLAG_LAUNCH_APP) {
                        requireContext().showToast(getString(R.string.default_app_restored))
                    }
                    if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                        findNavController().popBackStack(R.id.mainFragment, false)
                    else
                        findNavController().popBackStack()
                }
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { appModel ->
                when (appModel) {
                    is AppModel.PrivateSpaceHeader -> {}
                    is AppModel.FolderHeader, is AppModel.GroupsSection, AppModel.GroupsDivider -> {}
                    is AppModel.SettingTile -> {}
                    is AppModel.Contact -> {}
                    is AppModel.PinnedShortcut ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().deletePinnedShortcut(
                                packageName = appModel.appPackage,
                                shortcutIdToDelete = appModel.shortcutId,
                                user = appModel.user,
                            )
                        }

                    is AppModel.App -> {
                        if (appModel.user != Process.myUserHandle()) {
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else if (requireContext().isSystemApp(appModel.appPackage, appModel.user)) {
                            showCustomAlert(getString(R.string.system_app_cannot_delete))
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else {
                            requireContext().uninstall(appModel.appPackage)
                        }
                    }
                }
                viewModel.getAppList()
            },
            appHideListener = { appModel, _ ->
                if (appModel is AppModel.PinnedShortcut) {
                    showCustomAlert(getString(R.string.hiding_pinned_shortcuts_not_supported))
                    return@AppDrawerAdapter
                }
                adapter.removeApp(appModel)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage) // for backward compatibility
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                when (appModel) {
                    is AppModel.PinnedShortcut -> prefs.setShortcutRenameLabel(
                        appModel.appPackage, appModel.user.toString(), appModel.shortcutId, renameLabel
                    )
                    is AppModel.App -> prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                    else -> return@AppDrawerAdapter
                }
                viewModel.getAppList()
            },
            appFolderListener = { appModel -> showFolderAssignDialog(appModel) },
            appAddToHomeListener = { _ -> viewModel.refreshHome(false) },
            folderManageListener = ::showFolderManageDialog,
            folderEditListener = { id -> prefs.getFolder(id)?.let { editFolderApps(it) } },
            folderMoveListener = ::moveFolder,
            startFolderDrag = { groupDragHelper?.startDrag(it) },
            groupsCollapsed = { prefs.drawerGroupsCollapsed },
            groupsToggleListener = {
                prefs.drawerGroupsCollapsed = !prefs.drawerGroupsCollapsed
                adapter.applyFilter(binding.search.query)
            },
            privateSpaceToggleListener = {
                viewModel.togglePrivateSpaceLock()
            },
            privateSpaceSettingsListener = {
                viewModel.openPrivateSpaceSettings()
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            usageProvider = { model ->
                if (model.appPackage.isEmpty()) 0
                else prefs.getUsageCount(model.appPackage + "|" + model.user.toString())
            },
            searchModeProvider = { prefs.searchMode },
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                if (!groupDragActive && overScroll < -10 &&
                    binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING
                ) {
                    checkMessageAndExit()
                }
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        if (flag == Constants.FLAG_LAUNCH_APP) {
            binding.recyclerView.addItemDecoration(adapter.groupTreeDecoration(requireContext()))
            attachGroupDragHelper()
        }
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        // Play the entrance animation once, *after* the first real list is committed.
        // A persistent recyclerView.layoutAnimation is evaluated against the still-empty
        // list while ListAdapter.submitList() diffs on a background thread, and the rows
        // could end up stuck at the animation's start alpha (0) → an all-black drawer until
        // a relayout (lock/unlock or reopen). Triggering it from the commit callback keeps
        // the animation but guarantees the rows are always visible. E-ink keeps no animation.
        adapter.onFirstNonEmptyCommit = {
            if (requireContext().isEinkDisplay().not()) {
                _binding?.recyclerView?.let { rv ->
                    rv.layoutAnimation =
                        AnimationUtils.loadLayoutAnimation(rv.context, R.anim.layout_anim_from_bottom)
                    rv.scheduleLayoutAnimation()
                }
            }
        }
    }

    private fun attachGroupDragHelper() {
        var draggedHeader: AppDrawerAdapter.FolderHeaderViewHolder? = null
        val moveAnimator = object : DefaultItemAnimator() {
            override fun animateMove(holder: RecyclerView.ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
                if (holder === draggedHeader) {
                    dispatchMoveFinished(holder)
                    return false
                }
                return super.animateMove(holder, fromX, fromY, toX, toY)
            }
        }.apply {
            supportsChangeAnimations = false
            addDuration = 0L
            removeDuration = 0L
            moveDuration = 125L
        }
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun isLongPressDragEnabled() = false

            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return if (viewHolder is AppDrawerAdapter.FolderHeaderViewHolder &&
                    adapter.visibleFolderIds().size > 1
                ) super.getDragDirs(recyclerView, viewHolder) else 0
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = target is AppDrawerAdapter.FolderHeaderViewHolder

            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (source !is AppDrawerAdapter.FolderHeaderViewHolder ||
                    target !is AppDrawerAdapter.FolderHeaderViewHolder
                ) return false
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (!prefs.moveFolderTo(source.folderId, target.folderId, after = from < to)) return false
                adapter.updateGroups(prefs.folders)
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState != ItemTouchHelper.ACTION_STATE_DRAG) return
                val header = viewHolder as? AppDrawerAdapter.FolderHeaderViewHolder ?: return
                draggedHeader = header
                groupDragActive = true
                header.setDragging(true)
                binding.search.hideKeyboard()
                if (!requireContext().isEinkDisplay()) binding.recyclerView.itemAnimator = moveAnimator
                adapter.showGroupReorderRows(true)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                (viewHolder as? AppDrawerAdapter.FolderHeaderViewHolder)?.setDragging(false)
                if (viewHolder === draggedHeader) {
                    draggedHeader = null
                    groupDragActive = false
                    recyclerView.itemAnimator = null
                    if (_binding != null) adapter.showGroupReorderRows(false)
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }
        groupDragHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(binding.recyclerView) }
    }

    private fun initObservers() {
        IconManager.ready.observe(viewLifecycleOwner) { ready ->
            if (ready) adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        viewModel.firstOpen.observe(viewLifecycleOwner) {
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                currentAppList = it
                binding.newGroup.isEnabled = it != null
                updateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.drawerContacts.observe(viewLifecycleOwner) {
                    contactItems = if (prefs.searchContactsEnabled && ContactsHelper.hasPermission(requireContext()))
                        it ?: emptyList() else emptyList()
                    updateSearchSources()
                }
                viewModel.privateSpaceAvailable.observe(viewLifecycleOwner) {
                    currentPrivateSpaceAvailable = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                    currentPrivateSpaceLocked = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                    currentPrivateSpaceApps = it
                    updateCombinedAppList()
                }
            }
        }
    }

    private fun updateCombinedAppList() {
        val apps = currentAppList ?: return
        val combined = apps.toMutableList()

        when (flag) {
            Constants.FLAG_SET_CLOCK_APP -> {
                combined.add(0, AppModel.App(
                    appLabel = getString(R.string.default_clock_app_option),
                    key = null,
                    appPackage = "",
                    activityClassName = null,
                    isNew = false,
                    user = Process.myUserHandle()
                ))
            }
            Constants.FLAG_SET_CALENDAR_APP -> {
                combined.add(0, AppModel.App(
                    appLabel = getString(R.string.default_calendar_app_option),
                    key = null,
                    appPackage = "",
                    activityClassName = null,
                    isNew = false,
                    user = Process.myUserHandle()
                ))
            }
            Constants.FLAG_SET_SCREEN_TIME_APP -> {
                combined.add(0, AppModel.App(
                    appLabel = getString(R.string.default_screentime_app_option),
                    key = null,
                    appPackage = "",
                    activityClassName = null,
                    isNew = false,
                    user = Process.myUserHandle()
                ))
            }
            Constants.FLAG_SET_SWIPE_LEFT_APP -> {
                combined.add(0, AppModel.App(
                    appLabel = getString(R.string.default_swipe_left_option),
                    key = null,
                    appPackage = "",
                    activityClassName = null,
                    isNew = false,
                    user = Process.myUserHandle()
                ))
            }
            Constants.FLAG_SET_SWIPE_RIGHT_APP -> {
                combined.add(0, AppModel.App(
                    appLabel = getString(R.string.default_swipe_right_option),
                    key = null,
                    appPackage = "",
                    activityClassName = null,
                    isNew = false,
                    user = Process.myUserHandle()
                ))
            }
        }

        if (flag == Constants.FLAG_LAUNCH_APP && currentPrivateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = currentPrivateSpaceLocked))
            if (!currentPrivateSpaceLocked) {
                currentPrivateSpaceApps?.let { combined.addAll(it) }
            }
        }

        // Only show folders in the main launcher drawer
        if (flag == Constants.FLAG_LAUNCH_APP)
            adapter.setAppList(combined, prefs.folders)
        else
            adapter.setAppList(combined)
    }

    private fun initClickListeners() {
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                showCustomAlert(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            // FLAG_SET_HOME_APP_n == n, i.e. the flag *is* the home-slot location.
            if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8)
                prefs.setAppName(flag, name)
            findNavController().popBackStack()
        }
    }

    // ---- Folder management (manual, via long-press) ----

    private fun showFolderInputDialog(
        titleRes: Int,
        prefill: String?,
        buttonTextRes: Int,
        onConfirm: (String) -> Unit
    ) {
        val form = DialogFolderInputBinding.inflate(layoutInflater)
        form.dialogTitle.setText(titleRes)
        form.btnConfirm.setText(buttonTextRes)
        form.dialogInput.setText(prefill.orEmpty())
        form.dialogInput.selectAll()

        val dialog = AlertDialog.Builder(requireContext()).setView(form.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fun updateConfirm() {
            val hasName = form.dialogInput.text?.isNotBlank() == true
            form.btnConfirm.isEnabled = hasName
            form.btnConfirm.alpha = if (hasName) 1f else 0.45f
        }
        fun submit() {
            val name = form.dialogInput.text.toString().trim()
            if (name.isEmpty()) return
            dialog.dismiss()
            onConfirm(name)
        }

        updateConfirm()
        form.dialogInput.doAfterTextChanged { updateConfirm() }
        form.btnCancel.setOnClickListener { dialog.dismiss() }
        form.btnConfirm.setOnClickListener { submit() }
        form.dialogInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            submit()
            true
        }

        dialog.show()
        form.dialogInput.showKeyboard()
    }

    private fun showCreateFolderDialog(appKey: String?) {
        showFolderInputDialog(
            titleRes = R.string.new_folder,
            prefill = null,
            buttonTextRes = R.string.create,
            onConfirm = { name ->
                val folder = Folder(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    apps = if (appKey != null) mutableListOf(appKey) else mutableListOf(),
                )
                editFolderApps(folder, creating = true)
            }
        )
    }

    private fun editFolderApps(folder: Folder, creating: Boolean = false) {
        binding.search.hideKeyboard()
        val privateApps = if (currentPrivateSpaceLocked) emptyList() else currentPrivateSpaceApps.orEmpty()
        val apps = (currentAppList.orEmpty() + privateApps).filterIsInstance<AppModel.App>()
            .filter { it.appPackage.isNotBlank() }.sorted()
        groupAppsEditor?.dismiss()
        groupAppsEditor = showGroupAppsPicker(layoutInflater, folder, apps, SearchMode.fromValue(prefs.searchMode)) {
            prefs.upsertFolder(it)
            viewModel.refreshHome(false)
            if (creating) {
                prefs.drawerGroupsCollapsed = false
                binding.search.setQuery("", false)
                adapter.updateGroups(prefs.folders) { _binding?.recyclerView?.scrollToPosition(0) }
            } else {
                adapter.updateGroups(prefs.folders)
            }
        }
    }

    private fun showRenameFolderDialog(folder: Folder) {
        showFolderInputDialog(
            titleRes = R.string.rename_folder,
            prefill = folder.name,
            buttonTextRes = R.string.rename,
            onConfirm = { name ->
                folder.name = name
                prefs.upsertFolder(folder)
                for (i in 1..8) {
                    if (prefs.getIsFolder(i) && prefs.getFolderIdAt(i) == folder.id) {
                        prefs.setAppName(i, name)
                    }
                }
                viewModel.refreshHome(false)
                updateCombinedAppList()
            }
        )
    }

    private fun showFolderAssignDialog(appModel: AppModel) {
        if (appModel !is AppModel.App || appModel.appPackage.isEmpty()) return
        val key = appModel.appPackage + "|" + appModel.user.toString()
        val currentFolders = prefs.folders
        if (currentFolders.isEmpty()) {
            showCreateFolderDialog(key)
            return
        }

        showGroupOptions(getString(R.string.add_to_group)) { list, dialog ->
            for (folder in currentFolders) {
                val row = AppCompatCheckBox(list.context).apply {
                    text = folder.name
                    textSize = 16f
                    FontHelper.applyTo(this)
                    setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                    buttonTintList = android.content.res.ColorStateList.valueOf(
                        context.getColorFromAttr(R.attr.primaryColor)
                    )
                    minimumHeight = 52.dpToPx()
                    setPaddingRelative(8.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
                    isChecked = key in folder.apps
                    setOnCheckedChangeListener { _, _ ->
                        prefs.toggleAppInFolder(folder.id, key)
                        updateCombinedAppList()
                    }
                }
                list.addView(row)
            }
            addGroupAction(list, getString(R.string.new_folder), R.drawable.ic_add) {
                dialog.dismiss()
                showCreateFolderDialog(key)
            }
            addGroupAction(list, getString(R.string.done), R.drawable.ic_check) { dialog.dismiss() }
        }
    }

    private fun showFolderManageDialog(folderId: String) {
        val folder = prefs.getFolder(folderId) ?: return
        showGroupOptions(folder.name) { list, dialog ->
            fun action(label: Int, icon: Int, run: () -> Unit) {
                addGroupAction(list, getString(label), icon) {
                    dialog.dismiss()
                    run()
                }
            }

            action(R.string.edit_group_apps, R.drawable.ic_sc_grid) { editFolderApps(folder) }
            action(R.string.rename_folder, R.drawable.ic_rename) { showRenameFolderDialog(folder) }
            action(R.string.add_to_home, R.drawable.ic_sc_grid) { showAddFolderToHomeDialog(folder) }
            action(R.string.delete_folder, R.drawable.ic_delete) { confirmDeleteFolder(folder) }
        }
    }

    private fun confirmDeleteFolder(folder: Folder) {
        val context = requireContext()
        val content = DialogMessageBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(context).setView(content.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        content.dialogTitle.setText(R.string.delete_folder)
        content.dialogMessage.text = folder.name
        content.dialogNegativeButton.apply {
            isVisible = true
            setText(R.string.close)
            setOnClickListener { dialog.dismiss() }
        }
        content.dialogPositiveButton.apply {
            setText(R.string.delete_folder)
            setOnClickListener {
                dialog.dismiss()
                prefs.deleteFolder(folder.id)
                context.showToast(getString(R.string.folder_deleted))
                viewModel.refreshHome(false)
                updateCombinedAppList()
            }
        }
        dialog.show()
    }

    private fun moveFolder(folderId: String, up: Boolean) {
        val visibleFolderIds = adapter.visibleFolderIds()
        if (prefs.moveFolder(folderId, up) { it.id in visibleFolderIds }) updateCombinedAppList()
    }

    private fun showAddFolderToHomeDialog(folder: Folder) {
        val count = prefs.homeAppsNum.coerceIn(1, 8)
        showGroupOptions(getString(R.string.choose_home_position)) { list, dialog ->
            for (position in 1..count) {
                val currentTarget = prefs.getAppName(position).ifEmpty { getString(R.string.app) }
                addGroupAction(list, "$position.  $currentTarget") {
                    prefs.assignFolderToHome(position, folder)
                    viewModel.refreshHome(false)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showGroupOptions(title: String, populate: (LinearLayout, AlertDialog) -> Unit) {
        val picker = DialogListPickerBinding.inflate(layoutInflater)
        picker.pickerTitle.text = title
        picker.pickerTitle.isVisible = true
        ViewCompat.setAccessibilityHeading(picker.pickerTitle, true)
        val dialog = AlertDialog.Builder(requireContext()).setView(picker.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        populate(picker.pickerList, dialog)
        dialog.show()
    }

    private fun addGroupAction(list: LinearLayout, label: String, icon: Int = 0, onClick: () -> Unit) {
        val row = androidx.appcompat.widget.AppCompatButton(
            list.context, null, androidx.appcompat.R.attr.borderlessButtonStyle
        ).apply {
            text = label
            textSize = 16f
            FontHelper.applyTo(this)
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minimumHeight = 52.dpToPx()
            setPaddingRelative(10.dpToPx(), 8.dpToPx(), 10.dpToPx(), 8.dpToPx())
            val color = context.getColorFromAttr(R.attr.primaryColor)
            setTextColor(color)
            if (icon != 0) {
                val drawable = ContextCompat.getDrawable(context, icon)?.mutate()?.apply {
                    setTint(color)
                    setBounds(0, 0, 20.dpToPx(), 20.dpToPx())
                }
                setCompoundDrawablesRelative(drawable, null, null, null)
                compoundDrawablePadding = 14.dpToPx()
            }
            setOnClickListener { onClick() }
        }
        list.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            binding.search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            binding.search.hideKeyboard()
                        else if (!recyclerView.canScrollVertically(-1))
                            if (!onTop && isRemoving.not())
                                binding.search.showKeyboard(prefs.autoShowKeyboard)
                    }
                }
            }
        }
    }

    private fun checkMessageAndExit() {
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    override fun onStop() {
        searchOptionsPopup?.dismiss()
        searchOptionsPopup = null
        binding.search.hideKeyboard()
        super.onStop()
    }

    private fun showCustomAlert(message: String) {
        alertJob?.cancel()
        val container = binding.customAlertContainer
        val textView = binding.customAlertText
        
        textView.text = message
        container.alpha = 0f
        container.translationY = -50f
        container.visibility = View.VISIBLE
        
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .setListener(null)
            
        alertJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(1500)
            container.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(200)
                .setInterpolator(AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        container.visibility = View.GONE
                    }
                })
        }
    }

    override fun onDestroyView() {
        groupAppsEditor?.dismiss()
        groupAppsEditor = null
        groupDragHelper?.attachToRecyclerView(null)
        groupDragHelper = null
        groupDragActive = false
        _binding?.recyclerView?.adapter = null
        try {
            searchOptionsPopup?.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        searchOptionsPopup = null
        super.onDestroyView()
        _binding = null
    }
}
