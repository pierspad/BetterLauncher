package app.olauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
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
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hasBeenDays
import app.olauncher.helper.hasBeenHours
import app.olauncher.helper.hasBeenMinutes
import app.olauncher.helper.isDaySince
import app.olauncher.helper.isDefaultLauncher
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
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
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
                    binding.messageLayout.visibility = View.GONE
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
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

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
    }

    override fun onStart() {
        super.onStart()
        restartLauncherOrCheckTheme()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        viewModel.isPrivateSpaceToggling = false
    }

    override fun onStop() {
        isResumed = false
        backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        // Home button for recents feature disabled
        // val alreadyHome = navController.currentDestination?.id == R.id.mainFragment
        backToHomeScreen()
        // if (alreadyHome && isResumed && prefs.homeButtonShowRecents)
        //     viewModel.showRecentApps.call()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
    }

    private fun initClickListeners() {
        binding.ivClose.setOnClickListener {
            binding.messageLayout.visibility = View.GONE
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
                        binding.messageLayout.visibility = View.GONE
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
        val messageView = android.widget.TextView(this).apply {
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad / 2)
            textSize = 16f
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_limit_cooldown_title, label))
            .setView(messageView)
            .setPositiveButton(R.string.okay, null)
            .create()
        cooldownDialog = dialog
        dialog.setOnDismissListener { cooldownJob?.cancel() }
        dialog.show()

        cooldownJob = lifecycleScope.launch {
            while (true) {
                val remaining = block.untilMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    messageView.text = getString(R.string.app_limit_cooldown_over)
                    break
                }
                val totalSec = (remaining + 999) / 1000
                val mm = totalSec / 60
                val ss = totalSec % 60
                messageView.text = getString(
                    R.string.app_limit_cooldown_message,
                    String.format("%02d:%02d", mm, ss)
                )
                delay(500)
            }
        }
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

    private fun authenticateAndLaunch(appModel: AppModel.App) {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.launchAppDirectly(appModel)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // If the device has no biometric and no screen lock, don't lock the user out.
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                        showToast(getString(R.string.set_up_screen_lock_for_app_lock))
                        viewModel.launchAppDirectly(appModel)
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
            viewModel.launchAppDirectly(appModel)
        }
    }

    private fun showMessageDialog(title: Int, message: Int, action: Int, clickListener: () -> Unit) {
        binding.tvTitle.text = getString(title)
        binding.tvMessage.text = getString(message)
        binding.tvAction.text = getString(action)
        binding.tvAction.setOnClickListener {
            clickListener()
            binding.messageLayout.visibility = View.GONE
        }
        binding.messageLayout.visibility = View.VISIBLE
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        if (viewModel.isPrivateSpaceToggling) return
        binding.messageLayout.visibility = View.GONE
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
            if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            )
                restartLauncherOrCheckTheme(true)
        }
    }

    override fun onDestroy() {
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
}