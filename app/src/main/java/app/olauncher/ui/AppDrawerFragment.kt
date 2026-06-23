package app.olauncher.ui

import android.os.Build
import android.os.Bundle
import android.os.Process
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
import androidx.appcompat.widget.SearchView
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
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.dpToPx
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
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
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false

    private val viewModel: MainViewModel by activityViewModels()
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
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag == Constants.FLAG_LOCKED_APPS)
            binding.search.queryHint = getString(R.string.lock_apps)
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

    private fun initSearch() {
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
            appClickListener = { appModel ->
                if (flag == Constants.FLAG_LOCKED_APPS) {
                    if (appModel is AppModel.App) {
                        val nowLocked = viewModel.toggleAppLock(appModel)
                        requireContext().showToast(
                            getString(if (nowLocked) R.string.app_locked_toast else R.string.app_unlocked_toast)
                        )
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    viewModel.selectedApp(appModel, flag)
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
                            requireContext().showToast(getString(R.string.system_app_cannot_delete))
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
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
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
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
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
            folderManageListener = { folderId -> showFolderManageDialog(folderId) },
            privateSpaceToggleListener = {
                viewModel.togglePrivateSpaceLock()
            },
            privateSpaceSettingsListener = {
                viewModel.openPrivateSpaceSettings()
                findNavController().popBackStack(R.id.mainFragment, false)
            }
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
        if (requireContext().isEinkDisplay().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
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
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            when (flag) {
                Constants.FLAG_SET_HOME_APP_1 -> prefs.appName1 = name
                Constants.FLAG_SET_HOME_APP_2 -> prefs.appName2 = name
                Constants.FLAG_SET_HOME_APP_3 -> prefs.appName3 = name
                Constants.FLAG_SET_HOME_APP_4 -> prefs.appName4 = name
                Constants.FLAG_SET_HOME_APP_5 -> prefs.appName5 = name
                Constants.FLAG_SET_HOME_APP_6 -> prefs.appName6 = name
                Constants.FLAG_SET_HOME_APP_7 -> prefs.appName7 = name
                Constants.FLAG_SET_HOME_APP_8 -> prefs.appName8 = name
            }
            findNavController().popBackStack()
        }
    }

    // ---- Folder management (manual, via long-press) ----

    // Long-pressing an app row → "Group": toggle its membership across groups (or create one).
    private fun showFolderAssignDialog(appModel: AppModel) {
        if (appModel !is AppModel.App || appModel.appPackage.isEmpty()) return
        val key = appModel.appPackage + "|" + appModel.user.toString()
        val folders = prefs.folders
        if (folders.isEmpty()) {
            showCreateFolderDialog(key)
            return
        }
        val names = folders.map { it.name }.toTypedArray()
        val checked = BooleanArray(folders.size) { folders[it].apps.contains(key) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_group)
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                folders.forEachIndexed { i, folder ->
                    val has = folder.apps.contains(key)
                    if (checked[i] && !has) folder.apps.add(key)
                    else if (!checked[i] && has) folder.apps.remove(key)
                }
                prefs.folders = folders
                updateCombinedAppList()
            }
            .setNeutralButton(R.string.new_folder) { _, _ -> showCreateFolderDialog(key) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showCreateFolderDialog(appKey: String?) {
        val (input, container) = folderNameInput(null)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.new_folder)
            .setView(container)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val folder = Folder(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    apps = if (appKey != null) mutableListOf(appKey) else mutableListOf(),
                )
                prefs.upsertFolder(folder)
                updateCombinedAppList()
            }
            .setNegativeButton(R.string.close, null)
            .show()
        input.showKeyboard()
    }

    // Tapping a folder section header → rename / add-to-home / delete.
    private fun showFolderManageDialog(folderId: String) {
        val folder = prefs.getFolder(folderId) ?: return
        val items = arrayOf(
            getString(R.string.rename_folder),
            getString(R.string.add_to_home),
            getString(R.string.delete_folder),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(folder.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameFolderDialog(folder)
                    1 -> showAddFolderToHomeDialog(folder)
                    2 -> {
                        prefs.deleteFolder(folderId)
                        requireContext().showToast(getString(R.string.folder_deleted))
                        viewModel.refreshHome(false)
                        updateCombinedAppList()
                    }
                }
            }
            .show()
    }

    private fun showRenameFolderDialog(folder: Folder) {
        val (input, container) = folderNameInput(folder.name)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_folder)
            .setView(container)
            .setPositiveButton(R.string.rename) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                folder.name = name
                prefs.upsertFolder(folder)
                // Keep any home slot that shows this folder in sync with the new name.
                for (i in 1..8)
                    if (prefs.getIsFolder(i) && prefs.getFolderIdAt(i) == folder.id) prefs.setAppName(i, name)
                viewModel.refreshHome(false)
                updateCombinedAppList()
            }
            .setNegativeButton(R.string.close, null)
            .show()
        input.showKeyboard()
    }

    private fun showAddFolderToHomeDialog(folder: Folder) {
        val count = prefs.homeAppsNum.coerceIn(1, 8)
        val positions = (1..count).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.choose_home_position)
            .setItems(positions) { _, which ->
                prefs.assignFolderToHome(which + 1, folder)
                viewModel.refreshHome(false)
            }
            .show()
    }

    // Builds an EditText (optionally pre-filled) wrapped in a padded container for dialogs.
    private fun folderNameInput(prefill: String?): Pair<EditText, FrameLayout> {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setSingleLine()
            setHint(R.string.folder_name)
            if (prefill != null) {
                setText(prefill)
                setSelectAllOnFocus(true)
            }
        }
        val pad = 24.dpToPx()
        val container = FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        return input to container
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

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
