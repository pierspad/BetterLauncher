package app.olauncher.ui

import android.animation.ObjectAnimator
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.scrimColor
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openBrowserApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openGalleryApp
import app.olauncher.helper.openMessagingApp
import app.olauncher.helper.openSearch
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var appWidgetManager: AppWidgetManager? = null
    private var appWidgetHost: AppWidgetHost? = null

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ---- Reorder mode ----
    private var reorderMode = false
    private var appsReorder: ReorderController? = null
    private var iconsReorder: ReorderController? = null
    private val wiggleAnimators = HashMap<View, ObjectAnimator>()
    private var reorderBackCallback: OnBackPressedCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        appWidgetManager = AppWidgetManager.getInstance(requireContext().applicationContext)
        appWidgetHost = AppWidgetHost(requireContext().applicationContext, Constants.WIDGET_HOST_ID)

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        initShortcutIcons()
        initReorder()

        // Sync shortcut icon row heights with corresponding home app row heights
        homeAppViews().forEachIndexed { index, textView ->
            textView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (prefs.shortcutIconsEnabled) {
                    val count = prefs.homeShortcutIconsNum.coerceIn(1, Constants.SHORTCUT_COUNT)
                    val appCount = prefs.homeAppsNum
                    if (index < minOf(count, appCount)) {
                        val iconViews = shortcutIconViews()
                        val imageView = iconViews[index]
                        val lp = imageView.layoutParams
                        if (lp.height != textView.height) {
                            lp.height = textView.height
                            imageView.layoutParams = lp
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            appWidgetHost?.startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        populateHomeScreen(false)
        finalizePendingWidget()
        renderWidget()
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        applyOpacityScrim()
        (activity as? app.olauncher.MainActivity)?.updateGlobalOpacityScrim(animate = true)
    }

    // Darkens the wallpaper behind the UI. The alpha is baked into the background
    // color (not View.alpha) and the scrim stays VISIBLE so the parent's
    // LayoutTransition never animates it to full opacity (which caused a black flash).
    private fun applyOpacityScrim() {
        binding.homeOpacityScrim.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onPause() {
        super.onPause()
        if (reorderMode) exitReorderMode(announce = false)
        try {
            appWidgetHost?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onClick(view: View) {
        if (reorderMode) return
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        if (reorderMode) return true
        when (view.id) {
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1, prefs.appName1.isNotEmpty(), true)
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2, prefs.appName2.isNotEmpty(), true)
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3, prefs.appName3.isNotEmpty(), true)
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4, prefs.appName4.isNotEmpty(), true)
            R.id.homeApp5 -> showAppList(Constants.FLAG_SET_HOME_APP_5, prefs.appName5.isNotEmpty(), true)
            R.id.homeApp6 -> showAppList(Constants.FLAG_SET_HOME_APP_6, prefs.appName6.isNotEmpty(), true)
            R.id.homeApp7 -> showAppList(Constants.FLAG_SET_HOME_APP_7, prefs.appName7.isNotEmpty(), true)
            R.id.homeApp8 -> showAppList(Constants.FLAG_SET_HOME_APP_8, prefs.appName8.isNotEmpty(), true)
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                setHomeAlignment()
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
        viewModel.enterReorderMode.observe(viewLifecycleOwner) {
            enterReorderMode()
        }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val appsH = horizontalGravity
        val vertical = prefs.homeVerticalAlignment

        // Clock/date — independent horizontal alignment
        binding.dateTimeLayout.gravity = prefs.clockAlignment

        // Screen time — independent horizontal alignment
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()) {
            (binding.tvScreenTime.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.gravity = prefs.screenTimeAlignment
                binding.tvScreenTime.layoutParams = lp
            }
        }

        // Home apps — independent horizontal + shared vertical alignment
        binding.homeAppsLayout.gravity = appsH or vertical
        binding.homeApp1.gravity = appsH
        binding.homeApp2.gravity = appsH
        binding.homeApp3.gravity = appsH
        binding.homeApp4.gravity = appsH
        binding.homeApp5.gravity = appsH
        binding.homeApp6.gravity = appsH
        binding.homeApp7.gravity = appsH
        binding.homeApp8.gravity = appsH

        // Shortcut icons column — independent horizontal + shared vertical alignment
        (binding.shortcutIconsLayout.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.gravity = prefs.shortcutIconsAlignment or vertical
            binding.shortcutIconsLayout.layoutParams = lp
        }

        applyAppsPaddingForIcons()
    }

    // Keep the home apps text clear of the icons column when they are on the same side
    private fun applyAppsPaddingForIcons() {
        val base = 24.dpToPx()
        val textSizeScale = prefs.textSizeScale
        val iconScale = 1.0f + (textSizeScale - 1.0f) / 2.0f
        val reserve = (56 * iconScale).toInt().dpToPx()
        var left = base
        var right = base
        if (prefs.shortcutIconsEnabled) {
            when (prefs.shortcutIconsAlignment) {
                Gravity.START -> left = base + reserve
                Gravity.END -> right = base + reserve
            }
        }
        binding.homeAppsLayout.setPadding(
            left,
            binding.homeAppsLayout.paddingTop,
            right,
            binding.homeAppsLayout.paddingBottom
        )
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

//        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
        binding.date.text = dateText.replace(".,", ",")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = prefs.screenTimeAlignment
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private fun homeAppViews(): List<TextView> = listOf(
        binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
        binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8
    )

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()
        populateShortcutIcons()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum
        val views = homeAppViews()
        for (location in 1..views.size) {
            if (location > homeAppsNum) break
            val textView = views[location - 1]
            textView.visibility = View.VISIBLE
            if (!bindHomeSlot(textView, location)) {
                prefs.setAppName(location, "")
                prefs.setAppPackage(location, "")
            }
        }
    }

    // Binds a single home slot, which may be a folder, a pinned shortcut or a plain app.
    // Returns false when the slot's target no longer exists (caller clears the slot).
    private fun bindHomeSlot(textView: TextView, location: Int): Boolean {
        if (prefs.getIsFolder(location)) {
            val folder = prefs.getFolder(prefs.getFolderIdAt(location))
            if (folder != null) {
                textView.text = folder.name
                return true
            }
            // The folder was deleted elsewhere: free the slot.
            prefs.clearHomeSlot(location)
            textView.text = ""
            return false
        }
        return setHomeAppText(
            textView,
            prefs.getAppName(location),
            prefs.getAppPackage(location),
            prefs.getAppUser(location),
            prefs.getIsShortcut(location),
            prefs.getShortcutId(location)
        )
    }

    // Opens a folder placed on a home slot as a simple picker of its (installed) apps.
    private fun openHomeFolder(location: Int) {
        val folder = prefs.getFolder(prefs.getFolderIdAt(location)) ?: return
        val ctx = requireContext()
        val launcherApps = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        data class Member(val label: String, val pkg: String, val userString: String)

        val members = folder.apps.mapNotNull { key ->
            val sep = key.lastIndexOf('|')
            if (sep <= 0) return@mapNotNull null
            val pkg = key.substring(0, sep)
            val userString = key.substring(sep + 1)
            if (!isPackageInstalled(ctx, pkg, userString)) return@mapNotNull null
            val user = getUserHandleFromString(ctx, userString)
            val resolved = try {
                launcherApps.getActivityList(pkg, user).firstOrNull()?.label?.toString()
            } catch (e: Exception) {
                null
            }
            val label = prefs.getAppRenameLabel(pkg).ifEmpty { resolved ?: pkg }
            Member(label, pkg, userString)
        }

        if (members.isEmpty()) {
            ctx.showToast(getString(R.string.folder_is_empty))
            return
        }
        val labels = members.map { it.label }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle(folder.name)
            .setItems(labels) { _, which ->
                val member = members[which]
                launchApp(member.label, member.pkg, null, member.userString)
            }
            .show()
    }

    private fun setHomeAppText(
        textView: TextView,
        appName: String,
        packageName: String,
        userString: String,
        isShortcut: Boolean,
        shortcutId: String?,
    ): Boolean {
        // Get user handle for the app/shortcut
        val userHandle = getUserHandleFromString(requireContext(), userString)

        // If it's a shortcut, verify it still exists
        if (isShortcut) {
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Query for the specific shortcut
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                // Check if our shortcut still exists
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    textView.text = appName
                    return true
                }
                textView.text = ""
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                textView.text = ""
                return false
            }
        }

        // Regular app check
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            return true
        }
        textView.text = ""
        return false
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun homeAppClicked(location: Int) {
        if (prefs.getIsFolder(location)) {
            openHomeFolder(location)
            return
        }
        launchAppOrShortcut(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeRight,
            packageName = prefs.appPackageSwipeRight,
            activityClassName = prefs.appActivityClassNameRight,
            shortcutId = prefs.shortcutIdSwipeRight,
            isShortcut = prefs.isShortcutSwipeRight,
            userString = prefs.appUserSwipeRight,
            fallback = { openDialerApp(requireContext()) }
        )
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeLeft,
            packageName = prefs.appPackageSwipeLeft,
            activityClassName = prefs.appActivityClassNameSwipeLeft,
            shortcutId = prefs.shortcutIdSwipeLeft,
            isShortcut = prefs.isShortcutSwipeLeft,
            userString = prefs.appUserSwipeLeft,
            fallback = { openCameraApp(requireContext()) }
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            e.printStackTrace()
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                if (reorderMode) return
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                if (reorderMode) return
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                if (reorderMode) return
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                if (reorderMode) return
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick() {
                if (reorderMode) return
                super.onLongClick()
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onDoubleClick() {
                if (reorderMode) return
                super.onDoubleClick()
                if (!prefs.lockModeOn) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    binding.lock.performClick()
                else
                    lockPhone()
            }

            override fun onClick() {
                // A tap on empty space is the easy way out of reorder mode.
                if (reorderMode) {
                    exitReorderMode()
                    return
                }
                super.onClick()
                viewModel.checkForMessages.call()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    private fun shortcutIconViews(): List<ImageView> = listOf(
        binding.ivShortcut1, binding.ivShortcut2, binding.ivShortcut3,
        binding.ivShortcut4, binding.ivShortcut5, binding.ivShortcut6,
        binding.ivShortcut7, binding.ivShortcut8
    )

    private fun initShortcutIcons() {
        shortcutIconViews().forEachIndexed { slot, imageView ->
            imageView.setOnClickListener { launchShortcutSlot(slot) }
            imageView.setOnLongClickListener {
                showShortcutOptionsDialog(slot)
                true
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Reorder mode: drag home apps and shortcut icons up/down to reorder them, with a subtle wiggle
    // that signals the home screen is in an editing state (à la classic launcher "jiggle" mode).
    // ---------------------------------------------------------------------------------------------

    private fun initReorder() {
        appsReorder = ReorderController(
            rows = homeAppViews(),
            onCommit = ::commitAppsOrder,
            onLift = ::liftRow,
            onDrop = ::dropRow,
        )
        iconsReorder = ReorderController(
            rows = shortcutIconViews(),
            onCommit = ::commitIconsOrder,
            onLift = ::liftRow,
            onDrop = ::dropRow,
        )
        binding.reorderDone.setOnClickListener { exitReorderMode() }

        reorderBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = exitReorderMode()
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
    }

    private fun enterReorderMode() {
        if (reorderMode || _binding == null) return
        reorderMode = true
        binding.firstRunTips.visibility = View.GONE
        binding.setDefaultLauncher.visibility = View.GONE
        binding.reorderDone.visibility = View.VISIBLE
        reorderBackCallback?.isEnabled = true
        requireContext().showToast(getString(R.string.reorder_mode_hint), Toast.LENGTH_LONG)
        binding.mainLayout.post { startReorderInteractions() }
    }

    private fun exitReorderMode(announce: Boolean = true) {
        if (!reorderMode) return
        reorderMode = false
        reorderBackCallback?.isEnabled = false
        appsReorder?.disable()
        iconsReorder?.disable()
        clearWiggle()
        (homeAppViews() + shortcutIconViews()).forEach(::resetTransforms)
        _binding?.reorderDone?.visibility = View.GONE
        // The controllers replaced the row touch listeners; restore the normal interactions.
        initSwipeTouchListener()
        initShortcutIcons()
        if (announce) requireContext().showToast(getString(R.string.reorder_completed))
    }

    // (Re)arms wiggle + drag handling. Posted after layout so row tops/heights are valid.
    private fun startReorderInteractions() {
        if (!reorderMode || _binding == null) return
        applyWiggle()
        appsReorder?.enable()
        iconsReorder?.enable()
    }

    private fun refreshAndKeepReorder() {
        populateHomeScreen(false)
        if (reorderMode) binding.mainLayout.post { startReorderInteractions() }
    }

    // newOrder[destination] = source visible index. Slot N maps to home location N+1.
    private fun commitAppsOrder(newOrder: List<Int>) {
        val snapshot = newOrder.indices.map { prefs.readHomeSlot(it + 1) }
        newOrder.forEachIndexed { dest, src -> prefs.writeHomeSlot(dest + 1, snapshot[src]) }
        refreshAndKeepReorder()
    }

    // newOrder[destination] = source visible index. Visible index maps 1:1 to icon slot.
    private fun commitIconsOrder(newOrder: List<Int>) {
        val snapshot = newOrder.indices.map { prefs.readIconSlot(it) }
        newOrder.forEachIndexed { dest, src -> prefs.writeIconSlot(dest, snapshot[src]) }
        refreshAndKeepReorder()
    }

    private fun liftRow(view: View) {
        stopWiggle(view)
        view.animate().scaleX(1.08f).scaleY(1.08f).alpha(0.95f).setDuration(120).start()
    }

    private fun dropRow(view: View) {
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
        if (reorderMode) startWiggleFor(view)
    }

    private fun resetTransforms(view: View) {
        view.animate().cancel()
        view.rotation = 0f
        view.translationY = 0f
        view.translationZ = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
    }

    private fun applyWiggle() {
        clearWiggle()
        // Text labels are wide, so a tiny angle already reads as a wiggle. Icons are small,
        // so the same angle would be invisible — they need a much wider, faster rotation to
        // clearly signal "you're editing, a tap won't open the app".
        homeAppViews().filter { it.isVisible }.forEach { startWiggle(it, WIGGLE_DEGREES_TEXT, WIGGLE_DURATION_TEXT) }
        shortcutIconViews().filter { it.isVisible }.forEach { startWiggle(it, WIGGLE_DEGREES_ICON, WIGGLE_DURATION_ICON) }
    }

    // Restarts the wiggle for a single view with the amplitude/speed proper to its type.
    private fun startWiggleFor(view: View) {
        val isIcon = shortcutIconViews().contains(view)
        if (isIcon) startWiggle(view, WIGGLE_DEGREES_ICON, WIGGLE_DURATION_ICON)
        else startWiggle(view, WIGGLE_DEGREES_TEXT, WIGGLE_DURATION_TEXT)
    }

    private fun startWiggle(view: View, degrees: Float, duration: Long) {
        if (wiggleAnimators.containsKey(view)) return
        val anim = ObjectAnimator.ofFloat(view, View.ROTATION, -degrees, degrees).apply {
            this.duration = duration
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            startDelay = (0..120).random().toLong() // desync rows so the wiggle looks organic
            interpolator = AccelerateDecelerateInterpolator()
        }
        wiggleAnimators[view] = anim
        anim.start()
    }

    private fun stopWiggle(view: View) {
        wiggleAnimators.remove(view)?.cancel()
        view.rotation = 0f
    }

    private fun clearWiggle() {
        wiggleAnimators.values.forEach { it.cancel() }
        wiggleAnimators.clear()
        (homeAppViews() + shortcutIconViews()).forEach { it.rotation = 0f }
    }

    private fun populateShortcutIcons() {
        val enabled = prefs.shortcutIconsEnabled
        binding.shortcutIconsLayout.isVisible = enabled
        applyAppsPaddingForIcons()
        if (!enabled) return
        val count = prefs.homeShortcutIconsNum.coerceIn(1, Constants.SHORTCUT_COUNT)
        val appCount = prefs.homeAppsNum
        val minCount = minOf(count, appCount)
        val appViews = homeAppViews()
        val textSizeScale = prefs.textSizeScale
        val iconScale = 1.0f + (textSizeScale - 1.0f) / 2.0f
        val targetWidth = (48 * iconScale).toInt().dpToPx()
        val targetPadding = (12 * iconScale).toInt().dpToPx()
        shortcutIconViews().forEachIndexed { slot, imageView ->
            if (slot < count) {
                imageView.isVisible = true
                val iconIndex = prefs.getShortcutIconIndex(slot)
                    .coerceIn(0, Constants.SHORTCUT_ICONS.size - 1)
                imageView.setImageResource(Constants.SHORTCUT_ICONS[iconIndex])
                imageView.setPadding(targetPadding, targetPadding, targetPadding, targetPadding)

                // Keep icon height in sync with the corresponding app's height if both exist,
                // otherwise reset to default.
                val lp = imageView.layoutParams
                if (lp.width != targetWidth) {
                    lp.width = targetWidth
                    imageView.layoutParams = lp
                }
                if (slot < minCount) {
                    val textView = appViews[slot]
                    if (textView.height > 0) {
                        if (lp.height != textView.height) {
                            lp.height = textView.height
                            imageView.layoutParams = lp
                        }
                    }
                } else {
                    val targetHeight = (48 * iconScale).toInt().dpToPx()
                    if (lp.height != targetHeight) {
                        lp.height = targetHeight
                        imageView.layoutParams = lp
                    }
                }
            } else {
                imageView.isVisible = false
            }
        }
        positionWidget()
    }

    /**
     * Positions the widget container just above the taller of the apps block or the
     * shortcut-icons column, with a small gap for visual breathing room.
     * Must be called after layout has been measured; uses post{} to defer safely.
     */
    private fun positionWidget() {
        val b = _binding ?: return
        if (!b.widgetContainer.isVisible) return
        b.mainLayout.post {
            val binding = _binding ?: return@post
            val parentHeight = binding.mainLayout.height
            if (parentHeight == 0) return@post

            // shortcutIconsLayout is a direct child of mainLayout → .top is already in parent coords
            val iconsTop = if (binding.shortcutIconsLayout.isVisible)
                binding.shortcutIconsLayout.top
            else
                parentHeight

            // homeAppsLayout fills the parent (top == 0), so firstVisibleApp.top is in parent coords
            val appsTop = homeAppViews().firstOrNull { it.isVisible }?.top ?: parentHeight

            // Whichever block starts higher on screen (smaller y) drives the widget bottom
            val blockTop = minOf(iconsTop, appsTop)
            val gapPx = 12.dpToPx()

            // With gravity=bottom: view.bottom = parentHeight - bottomMargin
            // We want: view.bottom = blockTop - gapPx
            // → bottomMargin = parentHeight - blockTop + gapPx
            val lp = binding.widgetContainer.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = (parentHeight - blockTop + gapPx).coerceAtLeast(0)
            binding.widgetContainer.layoutParams = lp
        }
    }

    private fun launchShortcutSlot(slot: Int) {
        if (reorderMode) return
        val packageName = prefs.getShortcutTargetPackage(slot)
        if (packageName.isNotEmpty()) {
            launchApp(
                appName = prefs.getShortcutTargetLabel(slot).ifEmpty { getString(R.string.app) },
                packageName = packageName,
                activityClassName = prefs.getShortcutTargetClassName(slot),
                userString = prefs.getShortcutTargetUser(slot)
            )
            return
        }
        when (slot) {
            Constants.SHORTCUT_ACTION_BROWSER -> openBrowserApp(requireContext())
            Constants.SHORTCUT_ACTION_GALLERY -> openGalleryApp(requireContext())
            Constants.SHORTCUT_ACTION_CAMERA -> openCameraApp(requireContext())
            Constants.SHORTCUT_ACTION_MESSAGING -> openMessagingApp(requireContext())
            Constants.SHORTCUT_ACTION_DIALER -> openDialerApp(requireContext())
            Constants.SHORTCUT_ACTION_SETTINGS -> openOlauncherSettings()
        }
    }

    private fun openOlauncherSettings() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showShortcutOptionsDialog(slot: Int) {
        if (reorderMode) return
        val items = arrayOf(
            getString(R.string.change_app),
            getString(R.string.change_icon),
            getString(R.string.reset_to_default)
        )
        AlertDialog.Builder(requireContext())
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAppList(Constants.FLAG_SET_SHORTCUT_ICON_1 + slot)
                    1 -> showIconPickerDialog(slot)
                    2 -> {
                        prefs.clearShortcutTarget(slot)
                        prefs.setShortcutIconIndex(slot, Constants.SHORTCUT_DEFAULT_ICONS[slot])
                        populateShortcutIcons()
                    }
                }
            }
            .show()
    }

    private fun showIconPickerDialog(slot: Int) {
        val ctx = requireContext()
        val itemSize = 48.dpToPx()
        val gridView = GridView(ctx).apply {
            numColumns = 5
            val pad = 16.dpToPx()
            setPadding(pad, pad, pad, pad)
            verticalSpacing = 8.dpToPx()
            horizontalSpacing = 8.dpToPx()
            isVerticalScrollBarEnabled = false
        }
        gridView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = Constants.SHORTCUT_ICONS.size
            override fun getItem(position: Int): Any = Constants.SHORTCUT_ICONS[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val imageView = (convertView as? ImageView) ?: ImageView(ctx).apply {
                    layoutParams = AbsListView.LayoutParams(itemSize, itemSize)
                    val p = 10.dpToPx()
                    setPadding(p, p, p, p)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                imageView.setImageResource(Constants.SHORTCUT_ICONS[position])
                return imageView
            }
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.choose_icon)
            .setView(gridView)
            .setNegativeButton(R.string.close, null)
            .create()
        gridView.setOnItemClickListener { _, _, position, _ ->
            prefs.setShortcutIconIndex(slot, position)
            populateShortcutIcons()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun finalizePendingWidget() {
        val pending = prefs.pendingWidgetId
        if (pending == -1) return
        val mgr = appWidgetManager ?: return
        if (mgr.getAppWidgetInfo(pending) != null) {
            val old = prefs.widgetId
            if (old != -1 && old != pending) try { appWidgetHost?.deleteAppWidgetId(old) } catch (_: Exception) {}
            prefs.widgetId = pending
            Log.d(WIDGET_TAG, "finalizePendingWidget: adopted bound id=$pending (dropped Settings result)")
        } else {
            try { appWidgetHost?.deleteAppWidgetId(pending) } catch (_: Exception) {}
            Log.d(WIDGET_TAG, "finalizePendingWidget: id=$pending never bound, discarded")
        }
        prefs.pendingWidgetId = -1
    }

    private fun renderWidget() {
        val binding = _binding ?: return
        val container = binding.widgetContainer
        val manager = appWidgetManager
        val host = appWidgetHost
        val id = prefs.widgetId
        if (manager == null || host == null || id == -1 || !prefs.widgetEnabled) {
            container.removeAllViews()
            container.isVisible = false
            return
        }
        val info = manager.getAppWidgetInfo(id)
        if (info == null) {
            Log.w(WIDGET_TAG, "renderWidget: id=$id has no provider info -> clearing")
            container.removeAllViews()
            container.isVisible = false
            prefs.widgetId = -1
            return
        }
        try {
            container.removeAllViews()
            val appContext = requireContext().applicationContext
            val hostView = host.createView(appContext, id, info)

            val density = resources.displayMetrics.density
            val minHeightPx = info.minHeight.coerceAtLeast(MIN_WIDGET_HEIGHT_DP.dpToPx())

            hostView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                minHeightPx
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            container.addView(hostView)
            container.isVisible = true
            positionWidget()

            val widthPx = (resources.displayMetrics.widthPixels - 48.dpToPx()).coerceAtLeast(0)
            val widthDp = (widthPx / density).toInt()
            val heightDp = (minHeightPx / density).toInt()

            hostView.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            Log.d(WIDGET_TAG, "renderWidget: drew id=$id provider=${info.provider} ${widthDp}x${heightDp}dp")
        } catch (e: Exception) {
            Log.e(WIDGET_TAG, "renderWidget: createView failed for id=$id", e)
            container.removeAllViews()
            container.isVisible = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearWiggle()
        reorderMode = false
        appsReorder = null
        iconsReorder = null
        _binding = null
    }

    companion object {
        private const val WIDGET_TAG = "BLWidget"
        private const val MIN_WIDGET_HEIGHT_DP = 72

        // Wiggle amplitude/speed: subtle for wide text labels, pronounced for small icons.
        private const val WIGGLE_DEGREES_TEXT = 1.4f
        private const val WIGGLE_DURATION_TEXT = 130L
        private const val WIGGLE_DEGREES_ICON = 7f
        private const val WIGGLE_DURATION_ICON = 110L
    }
}