package app.olauncher.data

import app.olauncher.R

object Constants {

    object Key {
        const val FLAG = "flag"
        const val RENAME = "rename"
    }

    object Dialog {
        const val ABOUT = "ABOUT"
        const val SHARE = "SHARE"
        const val HIDDEN = "HIDDEN"
        const val KEYBOARD = "KEYBOARD"
        const val DIGITAL_WELLBEING = "DIGITAL_WELLBEING"
        const val PRO_MESSAGE = "PRO_MESSAGE"
    }

    object UserState {
        const val START = "START"
        const val SHARE = "SHARE"
    }

    object DateTime {
        const val OFF = 0
        const val ON = 1
        const val DATE_ONLY = 2

        fun isTimeVisible(dateTimeVisibility: Int): Boolean {
            return dateTimeVisibility == ON
        }

        fun isDateVisible(dateTimeVisibility: Int): Boolean {
            return dateTimeVisibility == ON || dateTimeVisibility == DATE_ONLY
        }
    }

    object SwipeDownAction {
        const val SEARCH = 1
        const val NOTIFICATIONS = 2
    }

    object CharacterIndicator {
        const val SHOW = 102
        const val HIDE = 101
    }

    val CLOCK_APP_PACKAGES = arrayOf(
        "com.google.android.deskclock", //Google Clock
        "com.sec.android.app.clockpackage", //Samsung Clock
        "com.oneplus.deskclock", //OnePlus Clock
        "com.miui.clock", //Xiaomi Clock
    )


//    const val THEME_MODE_DARK = 0
//    const val THEME_MODE_LIGHT = 1
//    const val THEME_MODE_SYSTEM = 2

    const val FLAG_LAUNCH_APP = 100
    const val FLAG_HIDDEN_APPS = 101
    const val FLAG_LOCKED_APPS = 102

    const val FLAG_SET_HOME_APP_1 = 1
    const val FLAG_SET_HOME_APP_2 = 2
    const val FLAG_SET_HOME_APP_3 = 3
    const val FLAG_SET_HOME_APP_4 = 4
    const val FLAG_SET_HOME_APP_5 = 5
    const val FLAG_SET_HOME_APP_6 = 6
    const val FLAG_SET_HOME_APP_7 = 7
    const val FLAG_SET_HOME_APP_8 = 8

    const val FLAG_SET_SWIPE_LEFT_APP = 11
    const val FLAG_SET_SWIPE_RIGHT_APP = 12
    const val FLAG_SET_CLOCK_APP = 13
    const val FLAG_SET_CALENDAR_APP = 14
    const val FLAG_SET_SCREEN_TIME_APP = 15

    // Custom target for the home-screen shortcut icons (right side column)
    const val FLAG_SET_SHORTCUT_ICON_1 = 31
    const val FLAG_SET_SHORTCUT_ICON_2 = 32
    const val FLAG_SET_SHORTCUT_ICON_3 = 33
    const val FLAG_SET_SHORTCUT_ICON_4 = 34
    const val FLAG_SET_SHORTCUT_ICON_5 = 35
    const val FLAG_SET_SHORTCUT_ICON_6 = 36
    const val FLAG_SET_SHORTCUT_ICON_7 = 37
    const val FLAG_SET_SHORTCUT_ICON_8 = 38

    const val REQUEST_CODE_ENABLE_ADMIN = 666
    const val REQUEST_CODE_LAUNCHER_SELECTOR = 678

    // Home-screen shortcut icons (right side column)
    const val SHORTCUT_COUNT = 8

    // Default action of each shortcut slot when no custom app is set
    const val SHORTCUT_ACTION_BROWSER = 0
    const val SHORTCUT_ACTION_GALLERY = 1
    const val SHORTCUT_ACTION_CAMERA = 2
    const val SHORTCUT_ACTION_MESSAGING = 3
    const val SHORTCUT_ACTION_DIALER = 4
    const val SHORTCUT_ACTION_SETTINGS = 5

    // Default glyph (index into SHORTCUT_ICONS) for each of the 8 slots
    val SHORTCUT_DEFAULT_ICONS = intArrayOf(0, 2, 3, 4, 5, 6, 7, 8)

    // The selectable icon set (~20). Stored as index in prefs (resource ids can change between builds).
    val SHORTCUT_ICONS = intArrayOf(
        R.drawable.ic_sc_search,    // 0
        R.drawable.ic_sc_globe,     // 1
        R.drawable.ic_sc_image,     // 2
        R.drawable.ic_sc_camera,    // 3
        R.drawable.ic_sc_message,   // 4
        R.drawable.ic_sc_phone,     // 5
        R.drawable.ic_sc_settings,  // 6
        R.drawable.ic_sc_mail,      // 7
        R.drawable.ic_sc_music,     // 8
        R.drawable.ic_sc_calendar,  // 9
        R.drawable.ic_sc_clock,     // 10
        R.drawable.ic_sc_map,       // 11
        R.drawable.ic_sc_calculator,// 12
        R.drawable.ic_sc_note,      // 13
        R.drawable.ic_sc_cloud,     // 14
        R.drawable.ic_sc_person,    // 15
        R.drawable.ic_sc_play,      // 16
        R.drawable.ic_sc_heart,     // 17
        R.drawable.ic_sc_star,      // 18
        R.drawable.ic_sc_grid,      // 19
        R.drawable.ic_sc_folder,    // 20
        R.drawable.ic_sc_download,  // 21
        R.drawable.ic_sc_wifi,      // 22
        R.drawable.ic_sc_bluetooth, // 23
        R.drawable.ic_sc_alarm,     // 24
        R.drawable.ic_sc_video,     // 25
        R.drawable.ic_sc_cart,      // 26
        R.drawable.ic_sc_wallet,    // 27
        R.drawable.ic_sc_book,      // 28
        R.drawable.ic_sc_game,      // 29
    )

    // AppWidget host id for the single home-screen widget
    const val WIDGET_HOST_ID = 4242

    const val LONG_PRESS_DELAY_MS = 500L
    const val ONE_DAY_IN_MILLIS = 86400000L
    const val ONE_HOUR_IN_MILLIS = 3600000L
    const val ONE_MINUTE_IN_MILLIS = 60000L

    const val MIN_ANIM_REFRESH_RATE = 30f

    const val URL_ABOUT_OLAUNCHER = "https://github.com/pierspad/BetterLauncher"
    const val URL_OLAUNCHER_PRIVACY = "https://github.com/pierspad/BetterLauncher#privacy-policy"
    const val URL_DOUBLE_TAP = "https://github.com/pierspad/BetterLauncher#double-tap-to-lock"
    const val URL_OLAUNCHER_GITHUB = "https://github.com/pierspad/BetterLauncher"
    const val URL_PIERSPAD = "https://pierspad.com"
    const val URL_OLAUNCHER_PLAY_STORE = "https://play.google.com/store/apps/details?id=app.olauncher"
    const val URL_OLAUNCHER_PRO = "https://play.google.com/store/apps/details?id=app.prolauncher"
    const val URL_PLAY_STORE_DEV = "https://pierspad.com"
    const val URL_TWITTER_TANUJ = ""
    const val URL_INSTA_TANUJ = ""
    const val URL_NTS = ""
    const val URL_PENTASTIC = ""
    const val URL_DUCK_SEARCH = "https://duck.co/?q="
    const val URL_DIGITAL_WELLBEING_LEARN_MORE = "https://github.com/pierspad/BetterLauncher#digital-wellbeing"

    const val DIGITAL_WELLBEING_PACKAGE_NAME = "com.google.android.apps.wellbeing"
    const val DIGITAL_WELLBEING_ACTIVITY = "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
    const val DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME = "com.samsung.android.forest"
    const val DIGITAL_WELLBEING_SAMSUNG_ACTIVITY = "com.samsung.android.forest.launcher.LauncherActivity"
}