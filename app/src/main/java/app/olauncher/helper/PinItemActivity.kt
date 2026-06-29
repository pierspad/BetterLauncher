package app.olauncher.helper

import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R

class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set window to be transparent
        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> showToast(getString(R.string.invalid_pin_request))
        }

        finish()
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                showToast(getString(R.string.widgets_not_supported))

            else -> showToast(getString(R.string.unknown_action_not_supported))
        }
    }

    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = pinItemRequest.accept()
            val message = when (success) {
                true -> getString(R.string.shortcut_pinned_successfully)
                false -> getString(R.string.failed_to_pin_shortcut)
            }
            showToast(message)
        } else {
            showToast(getString(R.string.invalid_shortcut_info))
        }
    }
}