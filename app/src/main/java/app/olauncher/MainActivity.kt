package app.olauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import app.olauncher.helper.LauncherRefreshCallback
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.helper.FontHelper
import app.olauncher.helper.IconManager
import app.olauncher.helper.AppLimiter
import app.olauncher.helper.scrimColor
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hasBeenDays
import app.olauncher.helper.hasBeenHours
import app.olauncher.helper.hasBeenMinutes
import app.olauncher.helper.isDaySince
import app.olauncher.helper.isDefaultLauncher
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isTablet
import app.olauncher.helper.openUrl
import app.olauncher.helper.resetLauncherViaFakeActivity
import app.olauncher.helper.shareApp
import app.olauncher.helper.showLauncherSelector
import app.olauncher.helper.showToast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var cooldownJob: Job? = null
    private var cooldownDialog: androidx.appcompat.app.AlertDialog? = null
    private var isResumed = false
    private var profileReceiver: BroadcastReceiver? = null
    private var launcherRefreshCallback: LauncherRefreshCallback? = null
    private var packageRefreshJob: Job? = null

    private fun effectiveNightMode() = if (isEinkDisplay()) AppCompatDelegate.MODE_NIGHT_NO else prefs.appTheme

    private fun registerLauncherRefresh() {
        val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val callback = LauncherRefreshCallback { packageName, user ->
            IconManager.invalidatePackage(packageName, user)
            packageRefreshJob?.cancel()
            packageRefreshJob = lifecycleScope.launch {
                delay(100) // Package replacement can emit several consecutive callbacks.
                viewModel.getAppList()
                if (IconManager.ready.value == true) IconManager.ready.value = true
            }
        }
        launcher.registerCallback(callback)
        launcherRefreshCallback = callback
    }

//    override fun onBackPressed() {
//        if (navController.currentDestination?.id != R.id.mainFragment)
//            super.onBackPressed()
//    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent bars on every API level: the default auto() scrim would tint the
        // navigation bar below API 29 and hide the wallpaper.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        prefs = Prefs(this)
        IconManager.init(this)
        AppCompatDelegate.setDefaultNightMode(effectiveNightMode())
        if (prefs.appLanguageTag.isNotEmpty()) {
            val current = AppCompatDelegate.getApplicationLocales()
            val tag = if (current.isEmpty) "" else current.get(0)?.toLanguageTag() ?: ""
            if (tag != prefs.appLanguageTag) {
                AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(prefs.appLanguageTag))
            }
        }
        // Resolve the selected font and install the typeface factory *before*
        // super.onCreate(), so AppCompat chains through it instead of claiming
        // the LayoutInflater's factory for itself.
        FontHelper.reload(this)
        FontHelper.install(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // A settings-initiated recreate (theme/font/text size) drops to the home via
        // onStop→backToHomeScreen; reopen Settings so the user stays where they were.
        if (prefs.reopenSettingsAfterRestart) {
            prefs.reopenSettingsAfterRestart = false
            if (navController.currentDestination?.id == R.id.mainFragment)
                navController.navigate(R.id.action_mainFragment_to_settingsFragment)
        }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id != R.id.mainFragment) {
                    // then we might want to finish the activity or disable this callback.
                    if (navController.popBackStack()) {
                        // Successfully popped back
                    } else {
                        // if you want other system/activity level handling
                    }
                } else {
                    hideMessageLayout()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
            viewModel.resetLauncherLiveData.call()
        }

        initClickListeners()
        initObservers(viewModel)
        registerLauncherRefresh()
        viewModel.getAppList()
        setupOrientation()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            profileReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    viewModel.isPrivateSpaceToggling = false
                    viewModel.getPrivateSpaceAppList()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
            registerReceiver(profileReceiver, filter)
        }
        app.olauncher.helper.AccessibilityConsent.showOnLaunch(this)
        intent?.let { handleIntent(it) }
    }

    override fun onStart() {
        super.onStart()
        restartLauncherOrCheckTheme()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        viewModel.isPrivateSpaceToggling = false

        AppLimiter.decayDaily(prefs, System.currentTimeMillis())

        // The accessibility service starts cooldown on an observed app exit.
        // Activity resume can precede the final window event of the departing app.

    }

    override fun onStop() {
        isResumed = false
        // External permission dialogs and document pickers must keep their fragment.
        super.onStop()
    }


    override fun onNewIntent(intent: Intent?) {
        // Home button for recents feature disabled
        // val alreadyHome = navController.currentDestination?.id == R.id.mainFragment
        backToHomeScreen()
        // if (alreadyHome && isResumed && prefs.homeButtonShowRecents)
        //     viewModel.showRecentApps.call()
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val key = intent.getStringExtra("SHOW_COOLDOWN_FOR_KEY")
        if (key != null) {
            viewModel.triggerCooldownBlock(key)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(effectiveNightMode())
        checkTheme()
    }

    private fun initClickListeners() {
        binding.ivClose.setOnClickListener {
            hideMessageLayout()
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
        }
        viewModel.checkForMessages.observe(this) {
            checkForMessages()
        }
        viewModel.launchAppWithAuth.observe(this) { appModel ->
            appModel?.let { authenticateAndLaunch(it) }
        }
        viewModel.cooldownBlocked.observe(this) { block ->
            block?.let { showCooldownDialog(it) }
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.ABOUT -> {
                    showMessageDialog(R.string.app_name, R.string.welcome_to_olauncher_settings, R.string.okay) {
                        hideMessageLayout()
                    }
                }

                Constants.Dialog.KEYBOARD -> {
                    showMessageDialog(R.string.app_name, R.string.keyboard_message, R.string.okay) {
                    }
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessageDialog(R.string.screen_time, R.string.app_usage_message, R.string.permission) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
            }
        }
    }

    // Soft-limit cooldown: a non-dismissable-by-tap dialog with a live mm:ss countdown.
    // Trying again before it expires already escalated the timer in the ViewModel, so we
    // just keep showing the latest target; the coroutine refreshes the text each second.
    private fun showCooldownDialog(block: MainViewModel.CooldownBlock) {
        cooldownJob?.cancel()
        cooldownDialog?.dismiss()

        val label = resolveAppLabel(block.packageName, block.user)
        val binding = app.olauncher.databinding.DialogAppCooldownBinding.inflate(layoutInflater)

        binding.cooldownTitle.text = getString(R.string.app_limit_cooldown_title_new, label)
        binding.cooldownMessage.text = buildString {
            append(getString(R.string.app_limit_cooldown_message_new))
            append("\n\n")
            if (block.isSevere) {
                append(getString(R.string.app_limit_cooldown_reason_severe, block.penaltyMinutes))
            } else {
                append(getString(R.string.app_limit_cooldown_reason_compulsiveness, block.compulsivenessLevel, block.totalBanMinutes))
            }
        }

        // Severe block (ban violated): red "no entry" icon and red accents instead of the
        // plain white hourglass, so the two situations are unmistakable at a glance.
        if (block.isSevere) {
            val severe = ContextCompat.getColor(this, R.color.cooldownSevere)
            binding.cooldownIcon.setImageResource(R.drawable.ic_block)
            binding.cooldownIcon.imageTintList = android.content.res.ColorStateList.valueOf(severe)
            binding.cooldownTitle.setTextColor(severe)
            binding.cooldownTimer.setTextColor(severe)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(binding.root)
            .create()
        // Transparent window so the rounded surface (not a square frame) is what shows.
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        cooldownDialog = dialog
        dialog.setOnDismissListener { cooldownJob?.cancel() }
        binding.cooldownButton.setOnClickListener { dialog.dismiss() }
        dialog.show()

        cooldownJob = lifecycleScope.launch {
            while (true) {
                val remaining = block.untilMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    binding.cooldownTimer.text = getString(R.string.cooldown_timer_zero)
                    binding.cooldownMessage.text = getString(R.string.app_limit_cooldown_over)
                    break
                }
                binding.cooldownTimer.text = formatCountdown(remaining)
                delay(500)
            }
        }
    }

    // mm:ss below one hour, hh:mm:ss above. Locale-invariant digits on purpose:
    // the countdown pairs with the monospace-looking 48sp timer style.
    private fun formatCountdown(remainingMillis: Long): String {
        val totalSec = (remainingMillis + 999) / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) "%02d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
        else "%02d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }

    private fun resolveAppLabel(packageName: String, user: android.os.UserHandle): String {
        return try {
            val launcherApps =
                getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
            launcherApps.getActivityList(packageName, user).firstOrNull()?.label?.toString()
                ?: packageName
        } catch (e: Exception) {
            packageName
        }
    }

    private fun authenticateAndLaunch(appModel: AppModel) {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.launchAppDirectly(appModel)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Authentication errors never authorize a launch.
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                        showToast(getString(R.string.set_up_screen_lock_for_app_lock))
                    }
                    // ERROR_USER_CANCELED / ERROR_NEGATIVE_BUTTON / lockout: keep the app locked.
                    else -> {}
                }
            }
        }

        val label = appModel.appLabel.ifEmpty { getString(R.string.app_name) }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app))
            .setSubtitle(label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        try {
            BiometricPrompt(this, executor, callback).authenticate(builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(getString(R.string.unable_to_open_app))
        }
    }

    private fun showMessageDialog(title: Int, message: Int, action: Int, clickListener: () -> Unit) {
        binding.tvTitle.text = getString(title)
        binding.tvMessage.text = getString(message)
        binding.tvAction.text = getString(action)
        binding.tvAction.setOnClickListener {
            clickListener()
            hideMessageLayout()
        }
        showMessageLayout()
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()
    }

    private fun setupOrientation() {
        // Allow free screen orientation to support large screens, foldables, and Android 16+ resizability
    }

    private fun backToHomeScreen() {
        if (viewModel.isPrivateSpaceToggling) return
        hideMessageLayout()
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            cacheDir.deleteRecursively()
            recreate()
        } else
            checkTheme()
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            val isDark = when (effectiveNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> isDarkThemeOn()
            }
            val expectedColor = if (isDark) getColor(R.color.white) else getColor(R.color.black)
            if (getColorFromAttr(R.attr.primaryColor) != expectedColor) {
                restartLauncherOrCheckTheme(true)
            }
        }
    }

    override fun onDestroy() {
        launcherRefreshCallback?.let {
            (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).unregisterCallback(it)
        }
        packageRefreshJob?.cancel()
        profileReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK)
                    prefs.lockModeOn = true
            }

            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK)
                    resetLauncherViaFakeActivity()
            }
        }
    }

    private var currentOpacity: Float = -1f

    fun updateGlobalOpacityScrim(animate: Boolean = false) {
        val destinationId = if (::navController.isInitialized) navController.currentDestination?.id else null
        val opacity = when (destinationId) {
            R.id.mainFragment -> prefs.opacityHome
            R.id.appListFragment -> prefs.opacityDrawer
            R.id.settingsFragment -> prefs.opacityHome
            R.id.backupRestoreFragment -> prefs.opacityDrawer
            R.id.fontPickerFragment -> prefs.opacityDrawer
            R.id.languagePickerFragment -> prefs.opacityDrawer
            else -> 0f
        }
        android.util.Log.d("BetterLauncher", "updateGlobalOpacityScrim: dest=$destinationId, opacity=$opacity, current=$currentOpacity, animate=$animate")

        // Ensure the background is solid (alpha 255) black or white depending on the active theme
        binding.globalOpacityScrim.setBackgroundColor(this.scrimColor(255))

        if (currentOpacity == opacity) return

        if (animate && currentOpacity >= 0f) {
            android.util.Log.d("BetterLauncher", "updateGlobalOpacityScrim: Animating from $currentOpacity to $opacity")
            binding.globalOpacityScrim.animate().cancel()
            binding.globalOpacityScrim.animate()
                .alpha(opacity.coerceIn(0f, 1f))
                .setDuration(220)
                .start()
        } else {
            android.util.Log.d("BetterLauncher", "updateGlobalOpacityScrim: Immediately setting to $opacity")
            binding.globalOpacityScrim.animate().cancel()
            binding.globalOpacityScrim.alpha = opacity.coerceIn(0f, 1f)
        }
        currentOpacity = opacity
    }

    private fun showMessageLayout() {
        binding.messageLayout.animate().cancel()
        binding.messageLayout.alpha = 0f
        binding.messageLayout.visibility = View.VISIBLE
        binding.messageLayout.animate().alpha(1f).setDuration(200).start()
    }

    private fun hideMessageLayout() {
        binding.messageLayout.animate().cancel()
        binding.messageLayout.animate().alpha(0f).setDuration(200).withEndAction {
            binding.messageLayout.visibility = View.GONE
        }.start()
    }
}