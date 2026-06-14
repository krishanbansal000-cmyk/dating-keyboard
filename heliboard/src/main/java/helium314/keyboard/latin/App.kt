// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.os.Build
import androidx.core.content.edit
import com.mikepenz.iconics.Iconics
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.upgradeToolbarPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugFlags.init(this)
        FoldableUtils.init(this)
        Settings.init(this)
        SubtypeSettings.init(this)

        // Register Google Material typeface for iconics
        Iconics.registerFont(GoogleMaterial)

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch { // do some uncritical work in background for faster startup
            SupportedEmojis.load(this@App)
            LayoutUtilsCustom.removeMissingLayouts(this@App)
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            Log.i(
                "startup", "Starting ${applicationInfo.processName} version ${packageInfo.versionName} (${
                    packageInfo.versionCode
                }) on Android ${Build.VERSION.SDK_INT}"
            )
        }

        RichInputMethodManager.init(this)
        checkVersionUpgrade(this)
        // Keep toolbar prefs aligned with the current key set so new pinned actions show up.
        val prefs = prefs()
        upgradeToolbarPrefs(prefs)
        prefs.edit {
            putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_VIOLETTE)
            putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_VIOLETTE)
            putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
        }
        transferOldPinnedClips(this) // todo: remove in a few months, maybe end 2026
        app = this
        Defaults.initDynamicDefaults(this)
    }

    companion object {
        // used so JniUtils can access application once
        private var app: App? = null
        fun getApp(): App? {
            val application = app
            app = null
            return application
        }
    }
}
