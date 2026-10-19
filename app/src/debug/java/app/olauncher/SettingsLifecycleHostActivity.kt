package app.olauncher

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.olauncher.ui.SettingsFragment

/** Debug-only host for lifecycle regressions that must not launch the launcher UI. */
class SettingsLifecycleHostActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            wasDestroyedWithoutView = false
            val fragment = SettingsFragment()
            fragment.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    wasDestroyedWithoutView = fragment.view == null
                }
            })
            supportFragmentManager.beginTransaction()
                .add(fragment, SETTINGS_TAG)
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED)
                .commitNow()
            supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commitNow()
        }
    }

    companion object {
        private const val SETTINGS_TAG = "settings-lifecycle-regression"

        @Volatile
        var wasDestroyedWithoutView: Boolean = false
    }
}
