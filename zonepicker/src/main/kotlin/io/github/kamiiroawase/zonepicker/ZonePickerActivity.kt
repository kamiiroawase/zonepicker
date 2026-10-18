package io.github.kamiiroawase.zonepicker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

    /** An explicit locale from the intent — an in-app language switcher's choice, API 26-32
     *  included, where no platform per-app locale exists for the picker to pick up; null
     *  (the default) keeps the system locale (and the platform per-app locale from API 33). */
    private var pickerLocale: Locale? = null

    /** Scrolls the checked zone to the top once, on its first appearance in the rendered
     *  list; initial launch only. */
    private var scrollSelectionInitially = false

    private val viewModel by lazy { ViewModelProvider(this)[ZonePickerViewModel::class.java] }

    /** The context the picker's own resources resolve against: this activity when no
     *  explicit locale was passed, else a configuration-context wrap following it. The wrap
     *  cannot happen in attachBaseContext — getIntent() still returns null there (the
     *  framework assigns the activity's intent only after attachBaseContext runs) — so the
     *  intent is read here in onCreate and the wrap enters through the composition. */
    // AppBundleLocaleChanges wants Play Core language downloads beside any dynamic locale
    // change: this wrap is the picker's explicit EXTRA_LOCALE channel (an in-app language
    // switcher, API 26-32 included), not a resource-locale lookup that could miss split
    // assets — which language splits to ship is the host app's bundle decision, and there is
    // nothing for a library to download
    @SuppressLint("AppBundleLocaleChanges")
    private fun pickerContext(): Context {
        val locale = pickerLocale ?: return this

        val configuration = Configuration(resources.configuration)

        configuration.setLocale(locale)

        return createConfigurationContext(configuration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pickerLocale =
            intent
                ?.getStringExtra(ZonePicker.EXTRA_LOCALE)
                ?.takeIf { it.isNotEmpty() }
                ?.let(Locale::forLanguageTag)

        // Zone names must follow the picker's own UI language, resolved against the
        // locale-wrapped picker context and re-pushed on every recreation, so an in-app
        // language switch surviving recreation rebuilds the snapshot. One push carries the
        // host-supplied default-list zone set too, so the initial build starts once under
        // both inputs
        viewModel.setInputs(
            PickerLocale.zoneNames(pickerContext()),
            intent.getStringArrayListExtra(ZonePicker.EXTRA_DEFAULT_ZONE_IDS)?.takeIf { it.isNotEmpty() },
        )

        // Stored legacy aliases (US/Pacific, Etc/UTC) and canonical IDs this old tzdata never
        // heard of resolve to the ID the list actually shows, so the checkmark lands on a
        // visible row instead of silently disappearing.
        selectedZoneId =
            intent
                .getStringExtra(ZonePicker.EXTRA_ZONE_ID)
                ?.let { ZoneData.resolveDisplayZoneId(it) }

        val passedAccent =
            colorExtra(ZonePicker.EXTRA_ACCENT_COLOR, ZonePicker.EXTRA_ACCENT_COLOR_DARK)
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
        // color, which reads in both day and night modes. An explicit EXTRA_MARK_COLOR rides
        // the same gate — a hand-picked mark cannot turn invisible either — and replaces only
        // the mark, never the bands: a dark-mode bar subtle enough for the dark page keeps a
        // bright checkmark
        val passedMarkColor =
            colorExtra(ZonePicker.EXTRA_MARK_COLOR, ZonePicker.EXTRA_MARK_COLOR_DARK)
                ?.let(AccentColors::opaqueColor)

        markColor =
            AccentColors.markColor(
                passedMarkColor ?: accentColor,
                getColor(R.color.zpSurface),
                getColor(R.color.zpTextPrimary),
            )

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
            // The locale wrap enters here, not attachBaseContext (see pickerContext):
            // every resource the picker resolves — stringResource, colorResource — follows
            // LocalContext, while the window-level theme and chrome stay untouched. Keyed on
            // the configuration (it implements equals) rather than the activity, whose
            // resources mutate in place, so a live configuration change rebuilds the wrap
            // with fresh day/night and density values
            val pickerContext = remember(resources.configuration) { pickerContext() }

            // The picker mirrors in RTL locales on its own: resolving the layout direction from
            // the locale here works even when the host app has not enabled RTL support.
            CompositionLocalProvider(
                LocalContext provides pickerContext,
                LocalLayoutDirection provides PickerLocale.layoutDirection(pickerLocale ?: resources.configuration.locales[0]),
            ) {
                // A scheme that tracks dark mode: the picker paints from its own color
                // resources, but the clickable ripple derives from the theme's colors, and
                // the light default scheme would leave that ripple all but invisible on the
                // dark night surfaces
                MaterialTheme(
                    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
                ) {
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

    /** The mode's own color extra: the dark twin in night mode when present, the base value
     *  otherwise. Read on every onCreate, so a system day/night switch while the picker is
     *  open — the activity recreates, having declared no configChanges — re-resolves both
     *  colors against the new mode instead of stranding the launch mode's picks on it. */
    private fun colorExtra(base: String, dark: String): Int? {
        val isNight =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val baseColor =
            intent.getIntExtra(base, COLOR_UNSET).takeIf { it != COLOR_UNSET }
        val darkColor =
            intent.getIntExtra(dark, COLOR_UNSET).takeIf { it != COLOR_UNSET }

        return if (isNight) darkColor ?: baseColor else baseColor
    }

    private companion object {
        private const val COLOR_UNSET = Int.MIN_VALUE
    }
}
