package app.olauncher.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.helper.animateAlpha
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isCountryIn
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.rateApp
import app.olauncher.helper.setPlainWallpaper
import app.olauncher.helper.shareApp
import app.olauncher.helper.showToast
import app.olauncher.listener.DeviceAdmin

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val enableAdminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                prefs.lockModeOn = true
                if (_binding != null) populateLockSettings()
            }
        }

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // The id of the widget is read back from the result Intent (EXTRA_APPWIDGET_ID),
    // not just the in-memory pendingWidgetId: launching the system picker can stop and
    // recreate this fragment, wiping the field, which is exactly why picking a widget
    // previously "did nothing". We fall back to pendingWidgetId only if the Intent has none.
    private fun resultWidgetId(result: androidx.activity.result.ActivityResult): Int {
        val fromData = result.data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        return if (fromData != AppWidgetManager.INVALID_APPWIDGET_ID) fromData else pendingWidgetId
    }

    // Standard system widget picker (ACTION_APPWIDGET_PICK). The OS handles
    // selection + binding and returns the bound id; we just run configure if needed.
    private val pickWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = resultWidgetId(result)
            if (result.resultCode == Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID)
                afterWidgetBound(id)
            else {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID)
                    appWidgetHost.deleteAppWidgetId(id)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

    private val configureWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = resultWidgetId(result)
            if (result.resultCode == Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID)
                setWidget(id)
            else {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID)
                    appWidgetHost.deleteAppWidgetId(id)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

    // Official binding-permission dialog (ACTION_APPWIDGET_BIND). Used as a fallback
    // when the OEM system widget picker is missing/broken and we bind a provider
    // ourselves. The dialog is the sanctioned way to grant a third-party launcher the
    // bind permission, so it does not crash OEM Settings the way a raw bind does.
    private val bindWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = resultWidgetId(result)
            if (result.resultCode == Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID)
                afterWidgetBound(id)
            else {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID)
                    appWidgetHost.deleteAppWidgetId(id)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            pendingWidgetId = it.getInt(KEY_PENDING_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
        checkAdminPermission()

        appWidgetManager = AppWidgetManager.getInstance(requireContext())
        appWidgetHost = AppWidgetHost(requireContext().applicationContext, Constants.WIDGET_HOST_ID)

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateProMessage()
        populateKeyboardText()
        populateScreenTimeOnOff()
        populateLockSettings()
        // Home button for recents feature disabled
        // populateHomeButtonRecents()
        populateWallpaperText()
        populateAppThemeText()
        populateTextSize()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populateSwipeDownAction()
        populateShortcutIconsSetting()
        populateWidget()
        populateActionHints()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        binding.appThemeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.screenTimeSwitch -> {
                viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
                populateScreenTimeOnOff()
            }
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            // Home button for recents feature disabled
            // R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.dailyWallpaperUrl -> requireContext().openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.alignHomeLeft -> updateHomeHorizontalAlignment(Gravity.START)
            R.id.alignHomeCenter -> updateHomeHorizontalAlignment(Gravity.CENTER)
            R.id.alignHomeRight -> updateHomeHorizontalAlignment(Gravity.END)
            R.id.alignVertUp -> updateVerticalAlignment(Gravity.TOP)
            R.id.alignVertDown -> updateVerticalAlignment(Gravity.BOTTOM)
            R.id.alignClockLeft -> updateClockAlignment(Gravity.START)
            R.id.alignClockCenter -> updateClockAlignment(Gravity.CENTER)
            R.id.alignClockRight -> updateClockAlignment(Gravity.END)
            R.id.alignIconsLeft -> updateIconsAlignment(Gravity.START)
            R.id.alignIconsCenter -> updateIconsAlignment(Gravity.CENTER)
            R.id.alignIconsRight -> updateIconsAlignment(Gravity.END)
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTimeSwitch -> toggleDateTimeEnabled()
            R.id.dateOnlySwitch -> toggleDateOnly()
            R.id.appThemeText -> binding.appThemeSelectLayout.visibility = View.VISIBLE
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> requireContext().openUrl(Constants.URL_DOUBLE_TAP)
            R.id.shortcutIcons -> toggleShortcutIcons()
            R.id.widgetEnabled -> toggleWidget()
            R.id.widgetChooseRow -> startWidgetPick()

            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)

            R.id.aboutOlauncher -> {
                prefs.aboutClicked = true
                requireContext().openUrl(Constants.URL_ABOUT_OLAUNCHER)
            }

            R.id.share -> requireActivity().shareApp()
            R.id.rate -> {
                prefs.rateClicked = true
                requireActivity().rateApp()
            }

            R.id.website -> requireContext().openUrl(Constants.URL_PIERSPAD)
            R.id.github -> requireContext().openUrl(Constants.URL_OLAUNCHER_GITHUB)
            R.id.privacy -> requireContext().openUrl(Constants.URL_OLAUNCHER_PRIVACY)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.dailyWallpaper -> removeWallpaper()
            R.id.appThemeText -> {
                binding.appThemeSelectLayout.visibility = View.VISIBLE
                binding.themeSystem.visibility = View.VISIBLE
            }

            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            R.id.widgetChooseRow -> removeWidget()
        }
        return true
    }

    private fun initClickListeners() {
        binding.olauncherHiddenApps.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.aboutOlauncher.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.homeButtonRecents.setOnClickListener(this)
        binding.screenTimeSwitch.setOnClickListener(this)
        binding.dailyWallpaperUrl.setOnClickListener(this)
        binding.dailyWallpaper.setOnClickListener(this)
        binding.alignHomeLeft.setOnClickListener(this)
        binding.alignHomeCenter.setOnClickListener(this)
        binding.alignHomeRight.setOnClickListener(this)
        binding.alignVertUp.setOnClickListener(this)
        binding.alignVertDown.setOnClickListener(this)
        binding.alignClockLeft.setOnClickListener(this)
        binding.alignClockCenter.setOnClickListener(this)
        binding.alignClockRight.setOnClickListener(this)
        binding.alignIconsLeft.setOnClickListener(this)
        binding.alignIconsCenter.setOnClickListener(this)
        binding.alignIconsRight.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTimeSwitch.setOnClickListener(this)
        binding.dateOnlySwitch.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.themeLight.setOnClickListener(this)
        binding.themeDark.setOnClickListener(this)
        binding.themeSystem.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)
        binding.shortcutIcons.setOnClickListener(this)
        binding.widgetEnabled.setOnClickListener(this)
        binding.widgetChooseRow.setOnClickListener(this)
        binding.widgetChooseRow.setOnLongClickListener(this)

        binding.share.setOnClickListener(this)
        binding.rate.setOnClickListener(this)
        binding.website.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)

        setupSliders()

        binding.dailyWallpaper.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            viewModel.showDialog.postValue(Constants.Dialog.ABOUT)
            prefs.firstSettingsOpen = false
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter += 1
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) showStatusBar() else hideStatusBar()
        binding.statusBar.isChecked = prefs.showStatusBar
    }

    private fun setDateTimeVisibility(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    // Main switch: off -> hidden; on -> show date & time (or date only if that sub-toggle is set).
    private fun toggleDateTimeEnabled() {
        val turningOn = prefs.dateTimeVisibility == Constants.DateTime.OFF
        setDateTimeVisibility(if (turningOn) Constants.DateTime.ON else Constants.DateTime.OFF)
    }

    // Sub-toggle, only meaningful while date & time is shown.
    private fun toggleDateOnly() {
        if (prefs.dateTimeVisibility == Constants.DateTime.OFF) return
        val dateOnly = prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY
        setDateTimeVisibility(if (dateOnly) Constants.DateTime.ON else Constants.DateTime.DATE_ONLY)
    }

    private fun populateDateTime() {
        val enabled = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.dateTimeSwitch.isChecked = enabled
        binding.dateOnlyRow.isVisible = enabled
        binding.dateOnlySwitch.isChecked = prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY
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

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun checkAdminPermission() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            prefs.lockModeOn = isAdmin
    }

    private fun toggleAccessibilityVisibility(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            binding.notWorking.visibility = View.VISIBLE
        if (isAccessServiceEnabled(requireContext()))
            binding.actionAccessibility.text = getString(R.string.disable)
        binding.accessibilityLayout.isVisible = show
        binding.scrollView.animateAlpha(if (show) 0.5f else 1f)
    }

    private fun openAccessibilityService() {
        toggleAccessibilityVisibility(false)
        // prefs.lockModeOn = true
        populateLockSettings()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
                toggleAccessibilityVisibility(true)
                populateLockSettings()
                return
            }
            prefs.lockModeOn = !prefs.lockModeOn
        } else {
            val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
            if (isAdmin) {
                removeActiveAdmin("Admin permission removed.")
                prefs.lockModeOn = false
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                enableAdminLauncher.launch(intent)
            }
        }
        populateLockSettings()
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeWallpaper() {
        if (requireContext().isEinkDisplay()) {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
            setPlainWallpaper(requireContext(), android.R.color.white)
        } else {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_YES
            setPlainWallpaper(requireContext(), android.R.color.black)
        }
        if (!prefs.dailyWallpaper) return
        prefs.dailyWallpaper = false
        populateWallpaperText()
        viewModel.cancelWallpaperWorker()
    }

    private fun toggleDailyWallpaperUpdate() {
        if (prefs.dailyWallpaper.not() && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && viewModel.isOlauncherDefault.value == false) {
            requireContext().showToast(R.string.set_as_default_launcher_first)
            populateWallpaperText()
            return
        }
        prefs.dailyWallpaper = !prefs.dailyWallpaper
        populateWallpaperText()
        if (prefs.dailyWallpaper) {
            viewModel.setWallpaperWorker()
            showWallpaperToasts()
        } else viewModel.cancelWallpaperWorker()
    }

    private fun showWallpaperToasts() {
        if (isOlauncherDefault(requireContext()))
            requireContext().showToast(getString(R.string.your_wallpaper_will_update_shortly))
        else
            requireContext().showToast(getString(R.string.olauncher_is_not_default_launcher), Toast.LENGTH_LONG)
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    // ---- Sliders: apps-on-home (1..6) and text size (0.8..1.5) ----

    private fun setupSliders() {
        // Apps on home: progress 0..(MAX-MIN) maps to MIN..MAX apps.
        val appsNum = prefs.homeAppsNum.coerceIn(APPS_NUM_MIN, APPS_NUM_MAX)
        if (appsNum != prefs.homeAppsNum) prefs.homeAppsNum = appsNum
        binding.appsNumSeekBar.max = APPS_NUM_MAX - APPS_NUM_MIN
        binding.appsNumSeekBar.progress = appsNum - APPS_NUM_MIN
        binding.homeAppsNum.text = appsNum.toString()
        binding.appsNumSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.homeAppsNum.text = (progress + APPS_NUM_MIN).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                updateHomeAppsNum(seekBar.progress + APPS_NUM_MIN)
            }
        })

        // Text size: progress 0..TEXT_SIZE_STEPS maps to TEXT_SIZE_MIN..TEXT_SIZE_MAX (step 0.1).
        binding.textSizeSeekBar.max = TEXT_SIZE_STEPS
        binding.textSizeSeekBar.progress = scaleToProgress(prefs.textSizeScale)
        binding.textSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.textSizeValue.text = String.format("%.1f", progressToScale(progress))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                applyTextSizeScale(progressToScale(seekBar.progress))
            }
        })
    }

    private fun progressToScale(progress: Int): Float =
        TEXT_SIZE_MIN + progress * TEXT_SIZE_STEP

    private fun scaleToProgress(scale: Float): Int =
        Math.round((scale - TEXT_SIZE_MIN) / TEXT_SIZE_STEP).coerceIn(0, TEXT_SIZE_STEPS)

    private fun applyTextSizeScale(scale: Float) {
        if (prefs.textSizeScale == scale) return
        prefs.textSizeScale = scale
        requireActivity().recreate()
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
            populateKeyboardText()
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.dailyWallpaper) {
            setPlainWallpaper(theme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.white)
            else -> {
                if (requireContext().isDarkThemeOn())
                    setPlainWallpaper(requireContext(), android.R.color.black)
                else setPlainWallpaper(requireContext(), android.R.color.white)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = String.format("%.1f", prefs.textSizeScale)
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.screenTimeSwitch.isChecked = requireContext().appUsagePermissionGranted()
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun populateKeyboardText() {
        binding.autoShowKeyboard.isChecked = prefs.autoShowKeyboard
    }

    private fun populateWallpaperText() {
        binding.dailyWallpaper.isChecked = prefs.dailyWallpaper
    }

    private fun populateAlignment() {
        highlightHorizontal(prefs.homeAlignment, binding.alignHomeLeft, binding.alignHomeCenter, binding.alignHomeRight)
        highlightHorizontal(prefs.clockAlignment, binding.alignClockLeft, binding.alignClockCenter, binding.alignClockRight)
        highlightHorizontal(prefs.shortcutIconsAlignment, binding.alignIconsLeft, binding.alignIconsCenter, binding.alignIconsRight)
        val verticalSelected = if (prefs.homeVerticalAlignment == Gravity.TOP) binding.alignVertUp else binding.alignVertDown
        highlightSegment(verticalSelected, binding.alignVertUp, binding.alignVertDown)
        applyAlignmentExclusion()
    }

    // Apps and shortcut icons must not share the same horizontal position: in each
    // segmented control we disable the button that matches the other control's
    // current position (but never the control's own current selection).
    private fun applyAlignmentExclusion() {
        applyExclusion(prefs.homeAlignment, prefs.shortcutIconsAlignment, binding.alignHomeLeft, binding.alignHomeCenter, binding.alignHomeRight)
        applyExclusion(prefs.shortcutIconsAlignment, prefs.homeAlignment, binding.alignIconsLeft, binding.alignIconsCenter, binding.alignIconsRight)
    }

    private fun applyExclusion(own: Int, other: Int, left: ImageView, center: ImageView, right: ImageView) {
        setSegmentEnabled(left, !(other == Gravity.START && own != Gravity.START))
        setSegmentEnabled(center, !(other == Gravity.CENTER && own != Gravity.CENTER))
        setSegmentEnabled(right, !(other == Gravity.END && own != Gravity.END))
    }

    private fun setSegmentEnabled(view: ImageView, enabled: Boolean) {
        view.isEnabled = enabled
        view.isClickable = enabled
        view.alpha = if (enabled) 1f else 0.25f
    }

    private fun highlightHorizontal(gravity: Int, left: ImageView, center: ImageView, right: ImageView) {
        val selected = when (gravity) {
            Gravity.CENTER -> center
            Gravity.END -> right
            else -> left
        }
        highlightSegment(selected, left, center, right)
    }

    private fun highlightSegment(selected: ImageView, vararg all: ImageView) {
        val active = requireContext().getColorFromAttr(R.attr.primaryColor)
        val inactive = requireContext().getColorFromAttr(R.attr.primaryColorTrans50)
        for (segment in all) {
            val isSelected = segment === selected
            segment.setColorFilter(if (isSelected) active else inactive)
            segment.setBackgroundResource(if (isSelected) R.drawable.segmented_thumb else 0)
        }
    }

    private fun updateVerticalAlignment(gravity: Int) {
        prefs.homeVerticalAlignment = gravity
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun updateClockAlignment(gravity: Int) {
        prefs.clockAlignment = gravity
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun updateHomeHorizontalAlignment(gravity: Int) {
        // Apps can't occupy the same horizontal position as the shortcut icons.
        if (gravity == prefs.shortcutIconsAlignment) return
        viewModel.updateHomeAlignment(gravity)
    }

    private fun updateIconsAlignment(gravity: Int) {
        // Shortcut icons can't occupy the same horizontal position as the apps.
        if (gravity == prefs.homeAlignment) return
        prefs.shortcutIconsAlignment = gravity
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    // Home button for recents feature disabled
    // private fun toggleHomeButtonRecents() {
    //     if (!prefs.homeButtonShowRecents && !isAccessServiceEnabled(requireContext())) {
    //         toggleAccessibilityVisibility(true)
    //         return
    //     }
    //     prefs.homeButtonShowRecents = !prefs.homeButtonShowRecents
    //     populateHomeButtonRecents()
    // }

    // private fun populateHomeButtonRecents() {
    //     binding.homeButtonRecents.text = getString(
    //         if (prefs.homeButtonShowRecents && isAccessServiceEnabled(requireContext())) R.string.on
    //         else R.string.off
    //     )
    // }

    private fun populateLockSettings() {
        binding.toggleLock.isChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            prefs.lockModeOn && isAccessServiceEnabled(requireContext())
        else
            prefs.lockModeOn
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
    }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        binding.swipeLeftApp.text = prefs.appNameSwipeLeft
        binding.swipeRightApp.text = prefs.appNameSwipeRight
        if (!prefs.swipeLeftEnabled)
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    }

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun populateActionHints() {
        if (prefs.aboutClicked.not())
            binding.aboutOlauncher.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_info, 0)
    }

    private fun populateProMessage() {
        if (prefs.proMessageShown.not() && prefs.userState == Constants.UserState.SHARE) {
            prefs.proMessageShown = true
            viewModel.showDialog.postValue(Constants.Dialog.PRO_MESSAGE)
        }
    }

    private fun toggleShortcutIcons() {
        prefs.shortcutIconsEnabled = !prefs.shortcutIconsEnabled
        populateShortcutIconsSetting()
        viewModel.refreshHome(false)
        if (prefs.shortcutIconsEnabled)
            requireContext().showToast(getString(R.string.long_press_shortcut_icon_hint), Toast.LENGTH_LONG)
    }

    private fun populateShortcutIconsSetting() {
        binding.shortcutIcons.isChecked = prefs.shortcutIconsEnabled
    }

    private fun populateWidget() {
        val enabled = prefs.widgetEnabled
        binding.widgetEnabled.isChecked = enabled
        binding.widgetChooseRow.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.widget.text = getString(if (prefs.widgetId != -1) R.string.active else R.string.none)
    }

    private fun toggleWidget() {
        prefs.widgetEnabled = !prefs.widgetEnabled
        // Turning the feature off clears any widget currently on the home screen.
        if (!prefs.widgetEnabled && prefs.widgetId != -1) removeWidget()
        populateWidget()
    }

    private fun startWidgetPick() {
        // Hosting a home-screen widget requires being the active default launcher.
        // On aggressive OEM skins (notably Xiaomi MIUI/HyperOS) firing the system
        // widget picker while we are NOT the default home app makes the OS try to
        // reassign the home role mid-flow, which is what triggers the "choose default
        // app" prompt and can crash the OEM Settings app. On stock/AOSP Android the
        // picker just works, which is why the bug only shows up on some devices.
        // So: make sure we're the default launcher before going any further.
        if (!isOlauncherDefault(requireContext())) {
            requireContext().showToast(getString(R.string.widget_requires_default_launcher), Toast.LENGTH_LONG)
            viewModel.resetLauncherLiveData.call()
            return
        }

        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = appWidgetId

        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Some OEM pickers crash on a missing custom-widget/shortcut list; pass empty ones.
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<Parcelable>())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Parcelable>())
        }

        // Prefer the native system picker when the device actually provides one;
        // otherwise fall back to our own provider list + official bind flow.
        if (pickIntent.resolveActivity(requireContext().packageManager) != null) {
            try {
                pickWidgetLauncher.launch(pickIntent)
            } catch (e: Exception) {
                showWidgetProviderPicker(appWidgetId)
            }
        } else {
            showWidgetProviderPicker(appWidgetId)
        }
    }

    // Self-hosted widget chooser used when the OEM system picker is unavailable.
    // Lists every installed widget provider and binds the chosen one via the
    // official permission flow (bindAppWidgetIdIfAllowed → ACTION_APPWIDGET_BIND).
    private fun showWidgetProviderPicker(appWidgetId: Int) {
        val providers = appWidgetManager.installedProviders
        if (providers.isEmpty()) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            requireContext().showToast(getString(R.string.no_widgets_available))
            return
        }
        val pm = requireContext().packageManager
        val labels = providers.map { it.loadLabel(pm) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.widget_choose)
            .setItems(labels) { _, which -> bindWidget(appWidgetId, providers[which].provider) }
            .setOnCancelListener {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
            .show()
    }

    private fun bindWidget(appWidgetId: Int, provider: ComponentName) {
        val allowed = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        } catch (e: Exception) {
            false
        }
        if (allowed) {
            afterWidgetBound(appWidgetId)
        } else {
            // Not allowed yet: ask the system for the bind permission through the
            // official dialog. This is the flow OEMs expect and it won't crash Settings.
            try {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                }
                bindWidgetLauncher.launch(intent)
            } catch (e: Exception) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                requireContext().showToast(getString(R.string.widget_bind_failed))
            }
        }
    }

    private fun afterWidgetBound(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info?.configure != null) {
            try {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                configureWidgetLauncher.launch(intent)
            } catch (e: Exception) {
                setWidget(appWidgetId)
            }
        } else {
            setWidget(appWidgetId)
        }
    }

    private fun setWidget(appWidgetId: Int) {
        val old = prefs.widgetId
        if (old != -1 && old != appWidgetId) {
            try {
                appWidgetHost.deleteAppWidgetId(old)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        prefs.widgetId = appWidgetId
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        populateWidget()
        viewModel.refreshHome(false)
        requireContext().showToast(getString(R.string.widget_added))
    }

    private fun removeWidget() {
        val id = prefs.widgetId
        if (id == -1) {
            requireContext().showToast(getString(R.string.long_press_to_remove_widget))
            return
        }
        try {
            appWidgetHost.deleteAppWidgetId(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs.widgetId = -1
        populateWidget()
        viewModel.refreshHome(false)
        requireContext().showToast(getString(R.string.widget_removed))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_WIDGET_ID, pendingWidgetId)
    }

    override fun onDestroy() {
        viewModel.checkForMessages.call()
        super.onDestroy()
    }

    companion object {
        private const val KEY_PENDING_WIDGET_ID = "pending_widget_id"

        // Apps-on-home slider bounds.
        private const val APPS_NUM_MIN = 1
        private const val APPS_NUM_MAX = 6

        // Text-size slider: 0.8..1.5 in steps of 0.1 (8 stops => max progress 7).
        private const val TEXT_SIZE_MIN = 0.8f
        private const val TEXT_SIZE_MAX = 1.5f
        private const val TEXT_SIZE_STEP = 0.1f
        private const val TEXT_SIZE_STEPS = 7
    }
}