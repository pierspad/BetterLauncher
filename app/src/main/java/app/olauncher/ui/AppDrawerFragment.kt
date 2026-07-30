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
import android.widget.EditText
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Folder
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var settingTiles: List<AppModel> = emptyList()
    private var contactItems: List<AppModel> = emptyList()

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.loadDrawerContacts()
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
                SearchMode.SMART -> getString(R.string.search_mode_smart)
                SearchMode.STRICT_PREFIX -> getString(R.string.search_mode_strict)
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
        val checkStrict = view.findViewById<View>(R.id.checkStrict)
        val checkLoose = view.findViewById<View>(R.id.checkLoose)
        val btnCancel = view.findViewById<View>(R.id.btnCancel)

        val currentMode = SearchMode.fromValue(prefs.searchMode)
        checkSmart.visibility = if (currentMode == SearchMode.SMART) View.VISIBLE else View.INVISIBLE
        checkStrict.visibility = if (currentMode == SearchMode.STRICT_PREFIX) View.VISIBLE else View.INVISIBLE
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

        view.findViewById<View>(R.id.modeSmart).setOnClickListener { selectMode(SearchMode.SMART) }
        view.findViewById<View>(R.id.modeStrict).setOnClickListener { selectMode(SearchMode.STRICT_PREFIX) }
        view.findViewById<View>(R.id.modeLoose).setOnClickListener { selectMode(SearchMode.LOOSE_FUZZY) }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun updateSearchSources() {
        adapter.setSearchSources(settingTiles + contactItems)
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
                else if (adapter.itemCount == 0)
                    requireContext().openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    binding.btnClearSearch?.isVisible = newText.isNotEmpty()
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
                    is AppModel.FolderHeader -> {}
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
            appHideListener = { appModel, position ->
                if (appModel is AppModel.PinnedShortcut) {
                    showCustomAlert(getString(R.string.hiding_pinned_shortcuts_not_supported))
                    return@AppDrawerAdapter
                }
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.remove(appModel)

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
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.shortcutId
                    is AppModel.App -> appModel.appPackage
                    else -> return@AppDrawerAdapter
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                viewModel.getAppList()
            },
            appFolderListener = { appModel -> showFolderAssignDialog(appModel) },
            appAddToHomeListener = { _ -> viewModel.refreshHome(false) },
            folderManageListener = { folderId -> showFolderManageDialog(folderId) },
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
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
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
            (activity as? app.olauncher.MainActivity)?.updateGlobalOpacityScrim(animate = true)
        }
    }

    private fun initObservers() {
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
                updateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.drawerContacts.observe(viewLifecycleOwner) {
                    contactItems = it ?: emptyList()
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
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_folder_input, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val inputView = view.findViewById<EditText>(R.id.dialogInput)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)
        val btnConfirm = view.findViewById<TextView>(R.id.btnConfirm)

        titleView.setText(titleRes)
        btnConfirm.setText(buttonTextRes)

        if (prefill != null) {
            inputView.setText(prefill)
            inputView.setSelection(prefill.length)
        }

        val cancelIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_close)?.apply {
            val size = (16 * resources.displayMetrics.density).toInt()
            setBounds(0, 0, size, size)
            setTint(ctx.getColorFromAttr(R.attr.primaryColorTrans80))
        }
        btnCancel.setCompoundDrawablesRelative(cancelIcon, null, null, null)
        btnCancel.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()

        val confirmIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_check)?.apply {
            val size = (16 * resources.displayMetrics.density).toInt()
            setBounds(0, 0, size, size)
            setTint(ctx.getColorFromAttr(R.attr.primaryColor))
        }
        btnConfirm.setCompoundDrawablesRelative(confirmIcon, null, null, null)
        btnConfirm.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        fun submit() {
            val name = inputView.text.toString().trim()
            if (name.isNotEmpty()) {
                onConfirm(name)
                dialog.dismiss()
            }
        }

        btnConfirm.setOnClickListener { submit() }

        inputView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else false
        }

        dialog.show()
        inputView.showKeyboard()
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
                prefs.upsertFolder(folder)
                updateCombinedAppList()
            }
        )
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

        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_list_picker, null)
        val titleView = view.findViewById<TextView>(R.id.pickerTitle)
        val list = view.findViewById<LinearLayout>(R.id.pickerList)

        titleView.setText(R.string.add_to_group)
        titleView.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val padV = 14.dpToPx()
        val padH = 10.dpToPx()
        val rippleBg = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        val checkedState = currentFolders.map { it.apps.contains(key) }.toMutableList()

        currentFolders.forEachIndexed { idx, folder ->
            val row = TextView(ctx).apply {
                text = (if (checkedState[idx]) "✓  " else "    ") + folder.name
                textSize = 18f
                setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(rippleBg)
                val icon = ContextCompat.getDrawable(ctx, R.drawable.ic_sc_folder)?.apply {
                    val size = (20 * resources.displayMetrics.density).toInt()
                    setBounds(0, 0, size, size)
                    setTint(ctx.getColorFromAttr(R.attr.primaryColor))
                }
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = 14.dpToPx()
                setOnClickListener {
                    checkedState[idx] = !checkedState[idx]
                    text = (if (checkedState[idx]) "✓  " else "    ") + folder.name
                    if (checkedState[idx] && !folder.apps.contains(key)) folder.apps.add(key)
                    else if (!checkedState[idx] && folder.apps.contains(key)) folder.apps.remove(key)
                    prefs.folders = currentFolders
                    updateCombinedAppList()
                }
            }
            list.addView(row)
        }

        val newFolderRow = TextView(ctx).apply {
            text = getString(R.string.new_folder)
            textSize = 18f
            setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            setBackgroundResource(rippleBg)
            val icon = ContextCompat.getDrawable(ctx, R.drawable.ic_check)?.apply {
                val size = (20 * resources.displayMetrics.density).toInt()
                setBounds(0, 0, size, size)
                setTint(ctx.getColorFromAttr(R.attr.primaryColor))
            }
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = 14.dpToPx()
            setOnClickListener {
                dialog.dismiss()
                showCreateFolderDialog(key)
            }
        }
        list.addView(newFolderRow)

        dialog.show()
    }

    private fun showFolderManageDialog(folderId: String) {
        val folder = prefs.getFolder(folderId) ?: return
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_list_picker, null)
        val titleView = view.findViewById<TextView>(R.id.pickerTitle)
        val list = view.findViewById<LinearLayout>(R.id.pickerList)

        titleView.text = folder.name
        titleView.visibility = View.VISIBLE

        val items = listOf(
            Triple(getString(R.string.rename_folder), R.drawable.ic_rename) { showRenameFolderDialog(folder) },
            Triple(getString(R.string.add_to_home), R.drawable.ic_sc_grid) { showAddFolderToHomeDialog(folder) },
            Triple(getString(R.string.delete_folder), R.drawable.ic_delete) {
                prefs.deleteFolder(folderId)
                ctx.showToast(getString(R.string.folder_deleted))
                viewModel.refreshHome(false)
                updateCombinedAppList()
            }
        )

        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val padV = 14.dpToPx()
        val padH = 10.dpToPx()
        val rippleBg = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        for ((label, iconRes, action) in items) {
            val row = TextView(ctx).apply {
                text = label
                textSize = 18f
                setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(rippleBg)
                val icon = ContextCompat.getDrawable(ctx, iconRes)?.apply {
                    val size = (20 * resources.displayMetrics.density).toInt()
                    setBounds(0, 0, size, size)
                    setTint(ctx.getColorFromAttr(R.attr.primaryColor))
                }
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = 14.dpToPx()
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            list.addView(row)
        }

        dialog.show()
    }

    private fun showAddFolderToHomeDialog(folder: Folder) {
        val ctx = requireContext()
        val count = prefs.homeAppsNum.coerceIn(1, 8)
        val view = layoutInflater.inflate(R.layout.dialog_list_picker, null)
        val titleView = view.findViewById<TextView>(R.id.pickerTitle)
        val list = view.findViewById<LinearLayout>(R.id.pickerList)

        titleView.setText(R.string.choose_home_position)
        titleView.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val padV = 14.dpToPx()
        val padH = 10.dpToPx()
        val rippleBg = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        for (pos in 1..count) {
            val currentTarget = prefs.getAppName(pos).ifEmpty { getString(R.string.app) }
            val row = TextView(ctx).apply {
                text = "$pos.  $currentTarget"
                textSize = 18f
                setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(rippleBg)
                setOnClickListener {
                    prefs.assignFolderToHome(pos, folder)
                    viewModel.refreshHome(false)
                    dialog.dismiss()
                }
            }
            list.addView(row)
        }

        dialog.show()
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
