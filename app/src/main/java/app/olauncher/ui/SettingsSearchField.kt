package app.olauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatEditText

class SettingsSearchField(context: Context, attrs: AttributeSet) : AppCompatEditText(context, attrs) {
    override fun focusSearch(direction: Int): View? {
        return try {
            super.focusSearch(direction)
        } catch (exception: IllegalArgumentException) {
            // MIUI can fail to sort focus candidates while the IME looks for another editor.
            if ((direction != FOCUS_UP && direction != FOCUS_DOWN) ||
                exception.message != "parameter must be a descendant of this view"
            ) {
                throw exception
            }
            null
        }
    }
}
