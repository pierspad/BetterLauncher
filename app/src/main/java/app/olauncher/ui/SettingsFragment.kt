package app.olauncher.ui

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
import app.olauncher.helper.ContactsHelper
import app.olauncher.helper.FontHelper
import app.olauncher.helper.animateAlpha
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.isCountryIn
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.rateApp
import app.olauncher.helper.scrimColor
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

    private var fontDialog: AlertDialog? = null
    private var languageDialog: AlertDialog? = null

    private data class LanguageOption(
        val code: String,
        val flag: String,
        val nativeName: String,
        val englishName: String,
        val nameRes: Int = 0
    )

    private val languageOptions = listOf(
        LanguageOption("", "🌐", "Automatic", "Automatic", R.string.language_automatic),
        LanguageOption("ar", "🇸🇦", "العربية", "Arabic"),
        LanguageOption("bn", "🇧🇩", "বাংলা", "Bengali"),
        LanguageOption("zh", "🇨🇳", "中文", "Chinese"),
        LanguageOption("hr", "🇭🇷", "Hrvatski", "Croatian"),
        LanguageOption("nl", "🇳🇱", "Nederlands", "Dutch"),
        LanguageOption("en", "🇬🇧", "English", "English"),
        LanguageOption("fr", "🇫🇷", "Français", "French"),
        LanguageOption("de", "🇩🇪", "Deutsch", "German"),
        LanguageOption("he", "🇮🇱", "עברית", "Hebrew"),
        LanguageOption("hi", "🇮🇳", "हिन्दी", "Hindi"),
        LanguageOption("hu", "🇭🇺", "Magyar", "Hungarian"),
        LanguageOption("in", "🇮🇩", "Bahasa Indonesia", "Indonesian"),
        LanguageOption("it", "🇮🇹", "Italiano", "Italian"),
        LanguageOption("ja", "🇯🇵", "日本語", "Japanese"),
        LanguageOption("ko", "🇰🇷", "한국어", "Korean"),
        LanguageOption("pl", "🇵🇱", "Polski", "Polish"),
        LanguageOption("pt-BR", "🇧🇷", "Português", "Portuguese"),
        LanguageOption("ru-RU", "🇷🇺", "Русский", "Russian"),
        LanguageOption("es-ES", "🇪🇸", "Español", "Spanish"),
        LanguageOption("sv", "🇸🇪", "Svenska", "Swedish"),
        LanguageOption("tr", "🇹🇷", "Türkçe", "Turkish"),
        LanguageOption("uk", "🇺🇦", "Українська", "Ukrainian")
    )

    private val enableAdminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                prefs.lockModeOn = true
                if (_binding != null) populateLockSettings()
            }
        }


    // Custom font: let the user pick a .ttf/.otf, copy it into private storage and apply.
    private val pickFontLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val path = FontHelper.importCustomFont(requireContext(), uri)
            if (path != null) {
                prefs.customFontPath = path
                prefs.fontFamily = FontHelper.CUSTOM_PREFIX + path.substringAfterLast('/')
                requireContext().showToast(getString(R.string.font_imported))
                applyFont()
            } else {
                requireContext().showToast(getString(R.string.font_import_failed))
            }
        }

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private fun resultWidgetId(result: androidx.activity.result.ActivityResult): Int {
        val fromData = result.data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        return if (fromData != AppWidgetManager.INVALID_APPWIDGET_ID) fromData else pendingWidgetId
    }

    private val pickWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = resultWidgetId(result)
            Log.d(WIDGET_TAG, "pick result: code=${result.resultCode} id=$id")
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
            Log.d(WIDGET_TAG, "configure result: code=${result.resultCode} id=$id")
            if (result.resultCode == Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID)
                setWidget(id)
            else {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID)
                    appWidgetHost.deleteAppWidgetId(id)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

    private val bindWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = resultWidgetId(result)
            Log.d(WIDGET_TAG, "bind-permission result: code=${result.resultCode} id=$id")
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

    override fun onResume() {
        super.onResume()
        val wasScreenTimeOn = binding.screenTimeAlignmentRow.isVisible
        populateScreenTimeOnOff()
        val isScreenTimeOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()
        if (!wasScreenTimeOn && isScreenTimeOn) {
            val clockOn = prefs.dateTimeVisibility != Constants.DateTime.OFF
            if (clockOn && prefs.screenTimeAlignment == prefs.clockAlignment) {
                prefs.screenTimeAlignment = firstFreeAlignment(prefs.clockAlignment)
            }
        }
        populateAlignment()
        (activity as? app.olauncher.MainActivity)?.updateGlobalOpacityScrim(animate = true)
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
        populateKeyboardText()
        populateScreenTimeOnOff()
        populateLockSettings()
        // Home button for recents feature disabled
        // populateHomeButtonRecents()
        populateTheme()
        populateFont()
        populateTextSize()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populateSwipeDownAction()
        populateWidget()
        initClickListeners()
        initObservers()

        val savedScrollY = prefs.settingsScrollY
        if (savedScrollY > 0) {
            prefs.settingsScrollY = 0
            binding.scrollView.post {
                binding.scrollView.scrollTo(0, savedScrollY)
            }
        }
    }

    override fun onClick(view: View) {
        binding.swipeDownSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.lockApps -> showLockApps()
            R.id.limitApps -> showLimitApps()
            R.id.backupRestoreSettings -> findNavController().navigate(R.id.action_settingsFragment_to_backupRestoreFragment)
            R.id.resetSettings -> resetSettingsToDefault()
            R.id.screenTimeSwitch -> {
                viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
                populateScreenTimeOnOff()
            }
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            // Home button for recents feature disabled
            // R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.alignHomeLeft -> updateHomeHorizontalAlignment(Gravity.START)
            R.id.alignHomeCenter -> updateHomeHorizontalAlignment(Gravity.CENTER)
            R.id.alignHomeRight -> updateHomeHorizontalAlignment(Gravity.END)
            R.id.alignVertUp -> updateVerticalAlignment(Gravity.TOP)
            R.id.alignVertDown -> updateVerticalAlignment(Gravity.BOTTOM)
            R.id.alignClockLeft -> updateClockAlignment(Gravity.START)
            R.id.alignClockCenter -> updateClockAlignment(Gravity.CENTER)
            R.id.alignClockRight -> updateClockAlignment(Gravity.END)
            R.id.alignScreenTimeLeft -> updateScreenTimeAlignment(Gravity.START)
            R.id.alignScreenTimeCenter -> updateScreenTimeAlignment(Gravity.CENTER)
            R.id.alignScreenTimeRight -> updateScreenTimeAlignment(Gravity.END)
            R.id.alignIconsLeft -> updateIconsAlignment(Gravity.START)
            R.id.alignIconsCenter -> updateIconsAlignment(Gravity.CENTER)
            R.id.alignIconsRight -> updateIconsAlignment(Gravity.END)
            R.id.reorderItems -> startReorderMode()
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTimeSwitch -> toggleDateTimeEnabled()
            R.id.dateOnlySwitch -> toggleDateOnly()
            R.id.fontText -> showFontDialog()
            R.id.themeChooseRow -> cycleTheme()
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> requireContext().openUrl(Constants.URL_DOUBLE_TAP)
            R.id.widgetEnabled -> toggleWidget()
            R.id.widgetChooseRow -> startWidgetPick()

            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)

            R.id.changeLanguage -> showLanguageDialog()

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
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            R.id.widgetChooseRow -> removeWidget()
        }
        return true
    }

    private fun initClickListeners() {
        binding.olauncherHiddenApps.setOnClickListener(this)
        binding.lockApps.setOnClickListener(this)
        binding.limitApps.setOnClickListener(this)
        binding.changeLanguage.setOnClickListener(this)
        binding.backupRestoreSettings.setOnClickListener(this)
        binding.resetSettings.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.aboutOlauncher.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.homeButtonRecents.setOnClickListener(this)
        binding.screenTimeSwitch.setOnClickListener(this)
        binding.alignHomeLeft.setOnClickListener(this)
        binding.alignHomeCenter.setOnClickListener(this)
        binding.alignHomeRight.setOnClickListener(this)
        binding.alignVertUp.setOnClickListener(this)
        binding.alignVertDown.setOnClickListener(this)
        binding.alignClockLeft.setOnClickListener(this)
        binding.alignClockCenter.setOnClickListener(this)
        binding.alignClockRight.setOnClickListener(this)
        binding.alignScreenTimeLeft.setOnClickListener(this)
        binding.alignScreenTimeCenter.setOnClickListener(this)
        binding.alignScreenTimeRight.setOnClickListener(this)
        binding.alignIconsLeft.setOnClickListener(this)
        binding.alignIconsCenter.setOnClickListener(this)
        binding.alignIconsRight.setOnClickListener(this)
        binding.reorderItems.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTimeSwitch.setOnClickListener(this)
        binding.dateOnlySwitch.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.fontText.setOnClickListener(this)
        binding.themeChooseRow.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)
        binding.widgetEnabled.setOnClickListener(this)
        binding.widgetChooseRow.setOnClickListener(this)
        binding.widgetChooseRow.setOnLongClickListener(this)

        binding.share.setOnClickListener(this)
        binding.rate.setOnClickListener(this)
        binding.website.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)

        setupSliders()

        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
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
        val screenTimeOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()
        if (selected != Constants.DateTime.OFF && screenTimeOn && prefs.clockAlignment == prefs.screenTimeAlignment) {
            prefs.clockAlignment = firstFreeAlignment(prefs.screenTimeAlignment)
        }
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

    private fun showMessageDialog(
        titleRes: Int,
        messageRes: Int,
        positiveBtnRes: Int,
        negativeBtnRes: Int? = null,
        onPositiveClick: (() -> Unit)? = null
    ) {
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_message, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        val positiveButton = view.findViewById<TextView>(R.id.dialogPositiveButton)
        val negativeButton = view.findViewById<TextView>(R.id.dialogNegativeButton)

        titleView.setText(titleRes)
        messageView.setText(messageRes)
        positiveButton.setText(positiveBtnRes)

        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        positiveButton.setOnClickListener {
            onPositiveClick?.invoke()
            dialog.dismiss()
        }

        if (negativeBtnRes != null) {
            negativeButton.visibility = View.VISIBLE
            negativeButton.setText(negativeBtnRes)
            negativeButton.setOnClickListener {
                dialog.dismiss()
            }
        } else {
            negativeButton.visibility = View.GONE
        }

        dialog.show()
    }

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            showMessageDialog(
                titleRes = R.string.hidden_apps,
                messageRes = R.string.hidden_apps_instructions,
                positiveBtnRes = android.R.string.ok
            )
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun resetSettingsToDefault() {
        showMessageDialog(
            titleRes = R.string.reset_settings_title,
            messageRes = R.string.reset_settings_confirm,
            positiveBtnRes = R.string.reset,
            negativeBtnRes = android.R.string.cancel,
            onPositiveClick = {
                prefs.resetToDefaults()
                requireContext().showToast(getString(R.string.reset_done))
                restartLauncher()
            }
        )
    }

    private fun showLockApps() {
        viewModel.getAppList()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_LOCKED_APPS)
        )
    }

    private fun showLimitApps() {
        viewModel.getAppList()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_LIMITED_APPS)
        )
    }

    // Re-apply theme and rebuild the UI after a settings restore.
    private fun restartLauncher() {
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        requireActivity().recreate()
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

    private fun updateHomeAppsNum(num: Int) {
        val wasEnabled = prefs.homeAppsNum > 0
        val oldNum = prefs.homeAppsNum
        if (num < oldNum) {
            prefs.reduceHomeApps(oldNum, num)
        }
        prefs.homeAppsNum = num
        binding.homeAppsNum.text = num.toString()

        // Re-enabling the apps column while it would collide with the icons column:
        // move the apps to the first free horizontal slot so the two never overlap.
        if (!wasEnabled && num > 0 && prefs.shortcutIconsEnabled &&
            prefs.homeAlignment == prefs.shortcutIconsAlignment
        ) {
            prefs.homeAlignment = firstFreeAlignment(prefs.shortcutIconsAlignment)
        }

        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
        viewModel.refreshHome(true)
    }

    private fun updateHomeIconsNum(num: Int) {
        val wasEnabled = prefs.shortcutIconsEnabled
        prefs.homeShortcutIconsNum = num
        binding.homeIconsNum.text = num.toString()

        // Re-enabling the icons column while it would collide with the apps column:
        // park the icons in the first free horizontal slot.
        if (!wasEnabled && num > 0 && prefs.homeAppsNum > 0 &&
            prefs.shortcutIconsAlignment == prefs.homeAlignment
        ) {
            prefs.shortcutIconsAlignment = firstFreeAlignment(prefs.homeAlignment)
        }

        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
        viewModel.refreshHome(false)

        if (!wasEnabled && num > 0)
            requireContext().showToast(getString(R.string.long_press_shortcut_icon_hint), Toast.LENGTH_LONG)
    }

    // First horizontal slot (start → center → end) that is not the one already taken.
    private fun firstFreeAlignment(taken: Int): Int =
        listOf(Gravity.START, Gravity.CENTER, Gravity.END).first { it != taken }

    // ---- Sliders: apps-on-home (1..6) and text size (0.8..1.5) ----

    private fun setupSliders() {
        // Mutate drawables to prevent shared state highlights
        val seekBars = listOf(
            binding.appsNumSeekBar,
            binding.iconsNumSeekBar,
            binding.textSizeSeekBar,
            binding.homeOpacitySeekBar,
            binding.drawerOpacitySeekBar
        )
        seekBars.forEach { seekBar ->
            seekBar.thumb?.mutate()
            seekBar.progressDrawable?.mutate()
        }

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

        // Favorite icons count: progress 0..(MAX-MIN) maps to MIN..MAX shortcut icons.
        val iconsNum = prefs.homeShortcutIconsNum.coerceIn(ICONS_NUM_MIN, ICONS_NUM_MAX)
        if (iconsNum != prefs.homeShortcutIconsNum) prefs.homeShortcutIconsNum = iconsNum
        binding.iconsNumSeekBar.max = ICONS_NUM_MAX - ICONS_NUM_MIN
        binding.iconsNumSeekBar.progress = iconsNum - ICONS_NUM_MIN
        binding.homeIconsNum.text = iconsNum.toString()
        binding.iconsNumSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.homeIconsNum.text = (progress + ICONS_NUM_MIN).toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                updateHomeIconsNum(seekBar.progress + ICONS_NUM_MIN)
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

        setupOpacitySliders()
    }

    // Opacity sliders: progress runs 0..OPACITY_STEPS (20) in steps of 5%, so each
    // tick is 5% (progress*5 = percentage, progress/steps = 0f..1f scrim alpha).
    private fun setupOpacitySliders() {
        // The preview is a real hole onto the live wallpaper, tracking the placeholder box.
        binding.wallpaperHole.attachTo(binding.opacityPreview, 16.dpToPx().toFloat())

        binding.homeOpacitySeekBar.max = OPACITY_STEPS
        binding.homeOpacitySeekBar.progress = opacityToProgress(prefs.opacityHome)
        binding.homeOpacityValue.text = "${binding.homeOpacitySeekBar.progress * OPACITY_STEP_PERCENT}%"
        binding.homeOpacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.homeOpacityValue.text = "${progress * OPACITY_STEP_PERCENT}%"
                updateOpacityPreview(progress, getString(R.string.opacity_home))
                if (fromUser) {
                    prefs.opacityHome = progress.toFloat() / OPACITY_STEPS
                    (activity as? app.olauncher.MainActivity)?.updateGlobalOpacityScrim(animate = false)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                updateOpacityPreview(seekBar.progress, getString(R.string.opacity_home))
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                prefs.opacityHome = seekBar.progress.toFloat() / OPACITY_STEPS
                (activity as? app.olauncher.MainActivity)?.updateGlobalOpacityScrim(animate = false)
            }
        })

        binding.drawerOpacitySeekBar.max = OPACITY_STEPS
        binding.drawerOpacitySeekBar.progress = opacityToProgress(prefs.opacityDrawer)
        binding.drawerOpacityValue.text = "${binding.drawerOpacitySeekBar.progress * OPACITY_STEP_PERCENT}%"
        binding.drawerOpacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.drawerOpacityValue.text = "${progress * OPACITY_STEP_PERCENT}%"
                updateOpacityPreview(progress, getString(R.string.opacity_drawer))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                updateOpacityPreview(seekBar.progress, getString(R.string.opacity_drawer))
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                prefs.opacityDrawer = seekBar.progress.toFloat() / OPACITY_STEPS
            }
        })

        // Initial preview reflects the home slider.
        updateOpacityPreview(binding.homeOpacitySeekBar.progress, getString(R.string.opacity_home))
    }

    // Mirrors the real home/drawer effect over the live wallpaper revealed by the hole:
    // the same theme-aware scrim (darken in dark mode, lighten in light mode) at the chosen
    // intensity, plus a centered label. At 0% the hole shows the bare wallpaper, no tint.
    private fun updateOpacityPreview(progress: Int, label: String) {
        val alpha = (progress.toFloat() / OPACITY_STEPS * 255).toInt()
        binding.wallpaperHole.setScrimColor(requireContext().scrimColor(alpha))
        binding.wallpaperHole.setLabel("$label · ${progress * OPACITY_STEP_PERCENT}%")
    }

    private fun opacityToProgress(value: Float): Int =
        Math.round(value.coerceIn(0f, 1f) * OPACITY_STEPS)

    private fun progressToScale(progress: Int): Float =
        TEXT_SIZE_MIN + progress * TEXT_SIZE_STEP

    private fun scaleToProgress(scale: Float): Int =
        Math.round((scale - TEXT_SIZE_MIN) / TEXT_SIZE_STEP).coerceIn(0, TEXT_SIZE_STEPS)

    private fun recreateSettingsWithScroll() {
        prefs.settingsScrollY = binding.scrollView.scrollY
        prefs.reopenSettingsAfterRestart = true
        requireActivity().recreate()
    }

    private fun applyTextSizeScale(scale: Float) {
        if (prefs.textSizeScale == scale) return
        prefs.textSizeScale = scale
        recreateSettingsWithScroll()
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
        if (prefs.appTheme == appTheme) return
        prefs.appTheme = appTheme
        // setDefaultNightMode applies the change and recreates the activity once,
        // re-inflating all views (and re-tinting icons) with the new theme.
        AppCompatDelegate.setDefaultNightMode(appTheme)
        recreateSettingsWithScroll()
    }

    private fun getThemeName(appTheme: Int): String {
        return when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.dark)
            else -> getString(R.string.system_default)
        }
    }

    private fun populateTheme() {
        val currentTheme = prefs.appTheme
        binding.themeValue.text = getThemeName(currentTheme)

        val iconRes = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.drawable.ic_sun
            AppCompatDelegate.MODE_NIGHT_YES -> R.drawable.ic_moon
            else -> R.drawable.ic_contrast
        }

        val ctx = requireContext()
        val drawable = androidx.core.content.ContextCompat.getDrawable(ctx, iconRes)?.mutate()
        if (drawable != null) {
            val color = ctx.getColorFromAttr(R.attr.primaryColor)
            androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, color)
            binding.themeLabel.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
        }
    }

    private fun cycleTheme() {
        val nextTheme = when (prefs.appTheme) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        updateTheme(nextTheme)
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = String.format("%.1f", prefs.textSizeScale)
    }

    // ---- Font ----
    // Predefined options map to built-in Android font families (no bundled files, no
    // licensing concerns). The last item lets the user import a custom .ttf/.otf.
    private data class FontOption(val labelRes: Int, val family: String)

    private val fontOptions = listOf(
        FontOption(R.string.font_system_default, ""),
        FontOption(R.string.font_sans_serif, "sans-serif"),
        FontOption(R.string.font_sans_serif_light, "sans-serif-light"),
        FontOption(R.string.font_sans_serif_medium, "sans-serif-medium"),
        FontOption(R.string.font_sans_serif_condensed, "sans-serif-condensed"),
        FontOption(R.string.font_serif, "serif"),
        FontOption(R.string.font_monospace, "monospace"),
    )

    private fun populateFont() {
        val current = prefs.fontFamily
        binding.fontText.text = when {
            current.startsWith(FontHelper.CUSTOM_PREFIX) ->
                current.removePrefix(FontHelper.CUSTOM_PREFIX)

            else -> getString(
                fontOptions.firstOrNull { it.family == current }?.labelRes
                    ?: R.string.font_system_default
            )
        }
    }

    // Modern picker: a glass card listing each font rendered in its own typeface, with
    // the current selection highlighted. The last row imports a custom .ttf/.otf.
    private fun showFontDialog() {
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_font_picker, null)
        val list = view.findViewById<LinearLayout>(R.id.fontPickerList)
        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        fontDialog = dialog

        val current = prefs.fontFamily
        val padV = 14.dpToPx()
        val padH = 10.dpToPx()
        val rippleBg = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun addRow(label: String, preview: Typeface, selected: Boolean, onClick: () -> Unit) {
            val row = TextView(ctx).apply {
                text = label
                textSize = 18f
                setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                typeface = if (selected) Typeface.create(preview, Typeface.BOLD) else preview
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(if (selected) R.drawable.rounded_rect_shade_color_glass else rippleBg)
                setOnClickListener { onClick() }
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dpToPx() }
            )
        }

        for (opt in fontOptions) {
            val preview = if (opt.family.isBlank()) Typeface.DEFAULT
            else Typeface.create(opt.family, Typeface.NORMAL)
            addRow(getString(opt.labelRes), preview, current == opt.family) {
                prefs.fontFamily = opt.family
                applyFont()
                dialog.dismiss()
            }
        }

        val isCustom = current.startsWith(FontHelper.CUSTOM_PREFIX)
        val customPreview = if (isCustom)
            runCatching { Typeface.createFromFile(prefs.customFontPath) }.getOrNull() ?: Typeface.DEFAULT
        else Typeface.DEFAULT
        val customLabel = if (isCustom) current.removePrefix(FontHelper.CUSTOM_PREFIX)
        else getString(R.string.font_custom)
        addRow(customLabel, customPreview, isCustom) {
            // Restrict the system picker to font files. Different providers report
            // fonts under different MIME types, so we pass the common ones; the file
            // is still validated after copying (see FontHelper.importCustomFont).
            dialog.dismiss()
            pickFontLauncher.launch(FONT_MIME_TYPES)
        }

        dialog.show()
        val displayMetrics = ctx.resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()
        view.post {
            if (view.height > maxHeight) {
                val scrollView = view.findViewById<View>(R.id.fontScrollView)
                val lp = scrollView.layoutParams
                lp.height = maxHeight - 48.dpToPx()
                scrollView.layoutParams = lp
            }
        }
    }

    private fun showLanguageDialog() {
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_language_picker, null)
        val list = view.findViewById<LinearLayout>(R.id.languagePickerList)
        val dialog = AlertDialog.Builder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        languageDialog = dialog

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentCode = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.toLanguageTag() ?: ""

        val padV = 14.dpToPx()
        val padH = 10.dpToPx()
        val rippleBg = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun addRow(opt: LanguageOption, selected: Boolean, onClick: () -> Unit) {
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(if (selected) R.drawable.rounded_rect_selected_language else rippleBg)
                setOnClickListener { onClick() }
            }

            // Flag TextView
            val flagView = TextView(ctx).apply {
                text = opt.flag
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    32.dpToPx(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12.dpToPx()
                }
            }
            rowLayout.addView(flagView)

            if (opt.code.isEmpty()) {
                val nameView = TextView(ctx).apply {
                    text = getString(opt.nameRes)
                    textSize = 18f
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                rowLayout.addView(nameView)
            } else {
                val nativeView = TextView(ctx).apply {
                    text = opt.nativeName
                    textSize = 18f
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val separatorView = TextView(ctx).apply {
                    text = "-"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8.dpToPx()
                        marginEnd = 8.dpToPx()
                    }
                }

                val englishView = TextView(ctx).apply {
                    text = opt.englishName
                    textSize = 18f
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                rowLayout.addView(nativeView)
                rowLayout.addView(separatorView)
                rowLayout.addView(englishView)
            }

            list.addView(
                rowLayout,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dpToPx() }
            )
        }

        for (opt in languageOptions) {
            val isSelectedExact = if (opt.code.isEmpty()) {
                currentCode.isEmpty()
            } else {
                currentCode.equals(opt.code, ignoreCase = true) ||
                (opt.code.length == 2 && currentCode.startsWith(opt.code + "-", ignoreCase = true))
            }
            addRow(opt, isSelectedExact) {
                // Save current settings scroll position
                prefs.settingsScrollY = binding.scrollView.scrollY
                prefs.reopenSettingsAfterRestart = true

                val localeList = if (opt.code.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(opt.code)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
                dialog.dismiss()
            }
        }

        dialog.show()
        val displayMetrics = ctx.resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()
        view.post {
            if (view.height > maxHeight) {
                val scrollView = view.findViewById<View>(R.id.languageScrollView)
                val lp = scrollView.layoutParams
                lp.height = maxHeight - 48.dpToPx()
                scrollView.layoutParams = lp
            }
        }
    }

    private fun applyFont() {
        populateFont()
        FontHelper.reload(requireContext())
        recreateSettingsWithScroll()
    }


    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.screenTimeSwitch.isChecked = requireContext().appUsagePermissionGranted()
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun populateKeyboardText() {
        binding.autoShowKeyboard.isChecked = prefs.autoShowKeyboard
    }

    private fun populateAlignment() {
        // Each horizontal-alignment control only makes sense when its block is on screen,
        // so we hide a whole row (and its divider) when the matching slider is at 0.
        val appsOn = prefs.homeAppsNum > 0
        val iconsOn = prefs.shortcutIconsEnabled // == homeShortcutIconsNum > 0
        val screenTimeOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()
        binding.appAlignmentRow.isVisible = appsOn
        binding.appAlignmentDivider.isVisible = appsOn
        binding.iconsAlignmentRow.isVisible = iconsOn
        binding.iconsAlignmentDivider.isVisible = iconsOn
        binding.screenTimeAlignmentRow.isVisible = screenTimeOn
        binding.screenTimeAlignmentDivider.isVisible = screenTimeOn

        highlightHorizontal(prefs.homeAlignment, binding.alignHomeLeft, binding.alignHomeCenter, binding.alignHomeRight)
        highlightHorizontal(prefs.clockAlignment, binding.alignClockLeft, binding.alignClockCenter, binding.alignClockRight)
        highlightHorizontal(prefs.shortcutIconsAlignment, binding.alignIconsLeft, binding.alignIconsCenter, binding.alignIconsRight)
        highlightHorizontal(prefs.screenTimeAlignment, binding.alignScreenTimeLeft, binding.alignScreenTimeCenter, binding.alignScreenTimeRight)
        val verticalSelected = if (prefs.homeVerticalAlignment == Gravity.TOP) binding.alignVertUp else binding.alignVertDown
        highlightSegment(verticalSelected, binding.alignVertUp, binding.alignVertDown)
        applyAlignmentExclusion()
    }

    // Apps and shortcut icons must not share the same horizontal position: in each
    // segmented control we disable the button that matches the other control's
    // current position (but never the control's own current selection). The mutual
    // exclusion only applies while BOTH blocks are visible — when only one is on
    // screen it is free to use any of the three slots.
    // Pairwise mutual exclusion is also applied between Clock and Screen Time.
    private fun applyAlignmentExclusion() {
        val bothAppsIcons = prefs.homeAppsNum > 0 && prefs.shortcutIconsEnabled
        if (bothAppsIcons) {
            applyExclusion(prefs.homeAlignment, prefs.shortcutIconsAlignment, binding.alignHomeLeft, binding.alignHomeCenter, binding.alignHomeRight)
            applyExclusion(prefs.shortcutIconsAlignment, prefs.homeAlignment, binding.alignIconsLeft, binding.alignIconsCenter, binding.alignIconsRight)
        } else {
            setAllSegmentsEnabled(binding.alignHomeLeft, binding.alignHomeCenter, binding.alignHomeRight)
            setAllSegmentsEnabled(binding.alignIconsLeft, binding.alignIconsCenter, binding.alignIconsRight)
        }

        val screenTimeOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()
        val clockOn = prefs.dateTimeVisibility != Constants.DateTime.OFF
        val bothClockScreenTime = screenTimeOn && clockOn
        if (bothClockScreenTime) {
            applyExclusion(prefs.clockAlignment, prefs.screenTimeAlignment, binding.alignClockLeft, binding.alignClockCenter, binding.alignClockRight)
            applyExclusion(prefs.screenTimeAlignment, prefs.clockAlignment, binding.alignScreenTimeLeft, binding.alignScreenTimeCenter, binding.alignScreenTimeRight)
        } else {
            setAllSegmentsEnabled(binding.alignClockLeft, binding.alignClockCenter, binding.alignClockRight)
            setAllSegmentsEnabled(binding.alignScreenTimeLeft, binding.alignScreenTimeCenter, binding.alignScreenTimeRight)
        }
    }

    private fun setAllSegmentsEnabled(vararg views: ImageView) {
        for (v in views) setSegmentEnabled(v, true)
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
        val screenTimeOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()
        if (screenTimeOn && gravity == prefs.screenTimeAlignment) return
        prefs.clockAlignment = gravity
        viewModel.updateHomeAlignment(prefs.homeAlignment)
        populateAlignment()
    }

    private fun updateScreenTimeAlignment(gravity: Int) {
        val clockOn = prefs.dateTimeVisibility != Constants.DateTime.OFF
        if (clockOn && gravity == prefs.clockAlignment) return
        prefs.screenTimeAlignment = gravity
        viewModel.updateHomeAlignment(prefs.homeAlignment)
        populateAlignment()
    }

    private fun updateHomeHorizontalAlignment(gravity: Int) {
        // Apps can't occupy the same horizontal position as the shortcut icons,
        // but only while the icons column is actually on screen.
        if (prefs.shortcutIconsEnabled && gravity == prefs.shortcutIconsAlignment) return
        viewModel.updateHomeAlignment(gravity)
    }

    // Asks the Home screen to enter reorder mode, then returns there. The event is
    // buffered by SingleLiveEvent and delivered once HomeFragment is active again.
    private fun startReorderMode() {
        viewModel.enterReorderMode.call()
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    private fun updateIconsAlignment(gravity: Int) {
        // Shortcut icons can't occupy the same horizontal position as the apps,
        // but only while the apps column is actually on screen.
        if (prefs.homeAppsNum > 0 && gravity == prefs.homeAlignment) return
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
        if (!isOlauncherDefault(requireContext())) {
            requireContext().showToast(getString(R.string.widget_requires_default_launcher), Toast.LENGTH_LONG)
            viewModel.resetLauncherLiveData.call()
            return
        }
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = appWidgetId
        prefs.pendingWidgetId = appWidgetId
        Log.d(WIDGET_TAG, "startWidgetPick: allocated id=$appWidgetId")

        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<Parcelable>())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Parcelable>())
        }
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
                prefs.pendingWidgetId = -1
            }
            .show()
    }

    private fun bindWidget(appWidgetId: Int, provider: ComponentName) {
        val allowed = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        } catch (e: Exception) {
            false
        }
        Log.d(WIDGET_TAG, "bindWidget: provider=$provider allowed=$allowed")
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
        Log.d(WIDGET_TAG, "afterWidgetBound: id=$appWidgetId bound=${info != null} configure=${info?.configure}")
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
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            Log.w(WIDGET_TAG, "setWidget: id=$appWidgetId is NOT bound -> aborting, not persisting")
            try { appWidgetHost.deleteAppWidgetId(appWidgetId) } catch (_: Exception) {}
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            prefs.pendingWidgetId = -1
            requireContext().showToast(getString(R.string.widget_bind_failed))
            return
        }
        Log.d(WIDGET_TAG, "setWidget: persisting bound id=$appWidgetId")
        val old = prefs.widgetId
        if (old != -1 && old != appWidgetId) {
            try {
                appWidgetHost.deleteAppWidgetId(old)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        prefs.widgetId = appWidgetId
        prefs.widgetProviderPackage = info.provider.packageName
        prefs.widgetProviderClass = info.provider.className
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        prefs.pendingWidgetId = -1
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
        prefs.widgetProviderPackage = ""
        prefs.widgetProviderClass = ""
        populateWidget()
        viewModel.refreshHome(false)
        requireContext().showToast(getString(R.string.widget_removed))
    }

    override fun onStop() {
        super.onStop()
        // Don't leave the pickers floating over the home screen when we leave Settings.
        fontDialog?.dismiss()
        languageDialog?.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fontDialog?.dismiss()
        fontDialog = null
        languageDialog?.dismiss()
        languageDialog = null
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
        private const val WIDGET_TAG = "BLWidget"
        private const val KEY_PENDING_WIDGET_ID = "pending_widget_id"

        // MIME types accepted by the custom-font picker (TrueType / OpenType, plus the
        // generic font category some providers expose). Validated again after import.
        private val FONT_MIME_TYPES = arrayOf(
            "font/ttf",
            "font/otf",
            "font/sfnt",
            "application/x-font-ttf",
            "application/x-font-otf",
            "application/font-sfnt",
            "application/vnd.ms-opentype",
        )

        // Apps-on-home slider bounds. 0 lets the user hide the apps column entirely.
        private const val APPS_NUM_MIN = 0
        private const val APPS_NUM_MAX = 8

        // Icons slider bounds. 0 hides the favorite-icons column entirely.
        private const val ICONS_NUM_MIN = 0
        private const val ICONS_NUM_MAX = Constants.SHORTCUT_COUNT

        // Text-size slider: 0.5..1.5 in steps of 0.1 (10 stops => max progress 10).
        private const val TEXT_SIZE_MIN = 0.5f
        private const val TEXT_SIZE_MAX = 1.5f
        private const val TEXT_SIZE_STEP = 0.1f
        private const val TEXT_SIZE_STEPS = 10

        // Opacity sliders: 20 steps of 5% each (0%..100%).
        private const val OPACITY_STEPS = 20
        private const val OPACITY_STEP_PERCENT = 5
    }
}