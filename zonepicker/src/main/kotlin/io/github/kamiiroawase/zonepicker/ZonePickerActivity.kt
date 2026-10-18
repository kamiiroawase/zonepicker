package io.github.kamiiroawase.zonepicker

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import java.util.Locale
import androidx.compose.ui.graphics.Color as ComposeColor

class ZonePickerActivity : ComponentActivity() {
    private var selectedZoneId: String? = null

    private var accentColor = 0

    private var onAccentColor = 0

    /** Checkmark and cursor color: the accent itself while it contrasts with the surface. */
    private var markColor = 0

    private var titleText: CharSequence? = null

    /** Scrolls the checked zone to the top once, on its first appearance in the rendered
     *  list; initial launch only. */
    private var scrollSelectionInitially = false

    private val viewModel by lazy { ViewModelProvider(this)[ZonePickerViewModel::class.java] }

    /** An explicit locale — an in-app language switcher's choice, API 26-32 included, where
     *  no platform per-app locale exists for the picker to pick up — wraps the base context,
     *  so every resource the picker resolves follows it: strings, zone display names, layout
     *  direction. Without one the context keeps the system locale (and the platform per-app
     *  locale from API 33). The intent is attached before this runs, so reading it here is
     *  safe. */
    override fun attachBaseContext(base: Context) {
        val locale =
            intent
                ?.getStringExtra(ZonePicker.EXTRA_LOCALE)
                ?.takeIf { it.isNotEmpty() }
                ?.let(Locale::forLanguageTag)

        val wrapped =
            if (locale == null) {
                base
            } else {
                val configuration = Configuration(base.resources.configuration)

                configuration.setLocale(locale)

                base.createConfigurationContext(configuration)
            }

        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Zone names must follow the picker's own UI language, resolved against this
        // activity's context and re-pushed on every recreation, so an in-app language
        // switch surviving recreation rebuilds the snapshot
        viewModel.setZoneNames(PickerLocale.zoneNames(this))

        // A host-supplied default-list zone set, when it passes one; the first push starts
        // the initial build under both inputs
        viewModel.setDefaultZoneIds(intent.getStringArrayListExtra(ZonePicker.EXTRA_DEFAULT_ZONE_IDS)?.takeIf { it.isNotEmpty() })

        // Stored legacy aliases (US/Pacific, Etc/UTC) and canonical IDs this old tzdata never
        // heard of resolve to the ID the list actually shows, so the checkmark lands on a
        // visible row instead of silently disappearing.
        selectedZoneId =
            intent
                .getStringExtra(ZonePicker.EXTRA_ZONE_ID)
                ?.let { ZoneData.resolveDisplayZoneId(it) }

        val passedAccent =
            intent
                .getIntExtra(ZonePicker.EXTRA_ACCENT_COLOR, ACCENT_UNSET)
                .takeIf { it != ACCENT_UNSET }
                ?.let(AccentColors::opaqueColor)

        accentColor = passedAccent ?: getColor(R.color.zpPrimaryColor)

        // A runtime accent repaints the whole header, so its title and back arrow take the
        // black/white side that reads over it; the resource path keeps the host's zpOnAccent
        // pairing untouched.
        onAccentColor =
            if (passedAccent != null) {
                AccentColors.onAccentColor(accentColor)
            } else {
                getColor(R.color.zpOnAccent)
            }

        // Selection marks drawn on zpSurface (checkmark, cursor) keep the accent while it
        // contrasts with that surface; a too-light accent falls back to the row's own text
        // color, which reads in both day and night modes.
        markColor =
            AccentColors.markColor(accentColor, getColor(R.color.zpSurface), getColor(R.color.zpTextPrimary))

        titleText = intent.getCharSequenceExtra(ZonePicker.EXTRA_TITLE)

        scrollSelectionInitially = savedInstanceState == null && selectedZoneId != null

        // Android 15 enforces edge-to-edge; older systems need the explicit opt-in so the
        // Compose insets padding owns the system bar areas on every host targetSdk, instead
        // of the system also reserving them and double-padding the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // The pre-35 DecorView still paints the theme's bar colors (framework Material
        // status bar tint, black nav bar) over that fullscreen layout, so clear the
        // status bar the way androidx enableEdgeToEdge does, leaving only the padded
        // content. The navigation bar instead takes the opaque accent outright: the
        // footer band paints that area on every system, and the legacy color API — a
        // documented no-op on stock Android 15+, but still honored by some OEM layers
        // such as MIUI's three-button bar, which otherwise draws its own white
        // background — paints the same color wherever it works. An opaque color also
        // sidesteps the enforced-scrim path entirely below 35.
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
        }
        @Suppress("DEPRECATION")
        window.navigationBarColor = accentColor

        // Contrast enforcement would layer the platform's translucent scrim over the accent
        // band on a 3-button navigation bar — on by default everywhere the flag exists,
        // 35 included, even though the bars themselves are transparent there by default too
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // Bar icons flip on the same light/dark edge as the header's chrome: the accent now
        // paints both bands — the header above and the footer under the navigation bar
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightNavigationBars = onAccentColor == Color.BLACK
            isAppearanceLightStatusBars = onAccentColor == Color.BLACK
        }

        setContent {
            // The picker mirrors in RTL locales on its own: resolving the layout direction from
            // the locale here works even when the host app has not enabled RTL support.
            CompositionLocalProvider(
                LocalLayoutDirection provides PickerLocale.layoutDirection(resources.configuration.locales[0]),
            ) {
                MaterialTheme {
                    // Renders whenever a snapshot lands: the first build runs in the
                    // background, and a stale one crossing the DST window is rebuilt in onResume
                    val snapshot by viewModel.snapshot.collectAsState()

                    ZonePickerScreen(
                        snapshot = snapshot,
                        selectedZoneId = selectedZoneId,
                        accent = ComposeColor(accentColor),
                        onAccent = ComposeColor(onAccentColor),
                        markColor = ComposeColor(markColor),
                        title = titleText?.toString() ?: stringResource(R.string.zp_title),
                        scrollSelectionInitially = scrollSelectionInitially,
                        onSelect = ::select,
                        onBack = ::finish,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Triggers a background rebuild when the cached snapshot has crossed the DST window
        viewModel.requestFreshSnapshot()
    }

    private fun select(zoneId: String?) {
        val data = Intent()

        zoneId?.let { data.putExtra(ZonePicker.EXTRA_ZONE_ID, it) }

        setResult(RESULT_OK, data)

        finish()
    }

    private companion object {
        private const val ACCENT_UNSET = Int.MIN_VALUE
    }
}
