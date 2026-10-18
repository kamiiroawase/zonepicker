package io.github.kamiiroawase.zonepicker.demo

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamiiroawase.zonepicker.ZonePickerBottomSheet
import io.github.kamiiroawase.zonepicker.ZonePickerContract
import io.github.kamiiroawase.zonepicker.ZonePickerDialog
import io.github.kamiiroawase.zonepicker.ZonePickerRequest
import io.github.kamiiroawase.zonepicker.ZonePickerResult
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {
    /** Compose state, so a pick landing recomposes the label without a manual refresh. */
    private var zoneId by mutableStateOf<String?>(null)

    private val pickerLauncher =
        registerForActivityResult(ZonePickerContract()) { result ->
            when (result) {
                is ZonePickerResult.Selected -> zoneId = result.zoneId
                ZonePickerResult.FollowSystem -> zoneId = null
                ZonePickerResult.Canceled -> return@registerForActivityResult
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        zoneId = savedInstanceState?.getString(STATE_ZONE_ID)

        // Android 15 enforces edge-to-edge; the explicit opt-in makes older systems draw
        // fullscreen too, so the screen's insets padding owns the bar areas on every API
        // level — the same shape the picker activity itself uses. The navigation bar takes
        // a transparent style with the scrim-free side of each mode: enableEdgeToEdge
        // re-applies its setup on configuration changes reaching the decor without
        // recreation (rotation in some window modes, folds, freeform resizes), and auto
        // styles re-enable the platform's contrast scrim each time — night-scoped styles
        // never do. A uiMode change recreates the activity, so the pick stays fresh, and
        // on API 26-28 the enforced scrim is a platform limitation that no style removes
        val darkTheme =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        enableEdgeToEdge(
            navigationBarStyle =
            if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )

        setContent {
            DemoTheme {
                // Plain Texts take LocalContentColor, whose bare default is black — the
                // three label lines read near-invisible on the dark window. Surface
                // provides the scheme's onBackground for everything inside
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoScreen(
                        zoneId = zoneId,
                        // The activity launch carries both modes' values: the picker itself
                        // re-resolves the pair when a mid-open day/night switch recreates it
                        onPick = { title, colors, locale ->
                            pickerLauncher.launch(
                                ZonePickerRequest(
                                    selectedZoneId = zoneId,
                                    accentColor = colors.lightAccent,
                                    accentColorDark = colors.darkAccent,
                                    markColor = colors.lightMark,
                                    markColorDark = colors.darkMark,
                                    title = title,
                                    locale = locale,
                                ),
                            )
                        },
                        onZonePicked = { zoneId = it },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        zoneId?.let { outState.putString(STATE_ZONE_ID, it) }
    }

    private companion object {
        private const val STATE_ZONE_ID = "zoneId"
    }
}

/** One pickable swatch in a demo color group. A null [color] is the 「默认」 pick: the
 *  request omits the extra and the library applies its own default (the resource accent) or
 *  its derived behavior (the mark from the accent). */
private data class ColorOption(@StringRes val label: Int, val color: Int?)

/** The swatch palette every color group picks from: the two extremes, an amber, and the
 *  默认 pick that omits the extra. Explicit marks render exactly as picked — no contrast
 *  gate — so amber comes out amber (light against the white surface) and a white mark on
 *  the light surface all but vanishes: the deliberate-choice tradeoff the demo shows. White
 *  flips the chrome black; black flips it white — and melts into the dark page, which the
 *  dark-mode groups exist to hand-pick around. */
private val COLOR_OPTIONS =
    listOf(
        ColorOption(R.string.demo_color_default, null),
        ColorOption(R.string.demo_color_white, Color.WHITE),
        ColorOption(R.string.demo_color_black, Color.BLACK),
        ColorOption(R.string.demo_color_amber, 0xFFFFD54F.toInt()),
    )

/** The four color channels the request takes, one swatch group each on the demo screen. */
private data class DemoColors(val lightAccent: Int?, val darkAccent: Int?, val lightMark: Int?, val darkMark: Int?)

/** Picker-language options the demo passes to the activity entry, exercising the explicit
 *  locale parameter — the in-app language-switcher channel on API 26-32, where no platform
 *  per-app locale exists for the picker to pick up. */
private enum class DemoLocale(@StringRes val label: Int, val tag: String?) {
    /** The picker follows the system language (and the platform per-app locale on API 33+). */
    System(R.string.demo_locale_system, null),

    Chinese(R.string.demo_locale_chinese, "zh"),

    English(R.string.demo_locale_english, "en"),
}

@Composable
private fun DemoTheme(content: @Composable () -> Unit) {
    val accent = colorResource(R.color.demoAccent)

    // Button ink rides onPrimary, whose baseline defaults disagree across modes (white in
    // light, near-black purple in dark): on the orange accent the dark-mode button text
    // turns dark. Derive black/white from the accent's own luminance instead — the same
    // rule the picker applies to its header chrome — so both modes read the same
    val onAccent = if (accent.luminance() > 0.5f) ComposeColor.Black else ComposeColor.White

    val colorScheme =
        if (isSystemInDarkTheme()) {
            darkColorScheme(primary = accent, onPrimary = onAccent)
        } else {
            lightColorScheme(primary = accent, onPrimary = onAccent)
        }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun DemoScreen(zoneId: String?, onPick: (title: String?, colors: DemoColors, locale: Locale?) -> Unit, onZonePicked: (String?) -> Unit) {
    val displayName =
        remember(zoneId) {
            val timeZone = zoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()

            timeZone.getDisplayName(false, TimeZone.LONG, Locale.SIMPLIFIED_CHINESE)
        }

    val zoneLabel = zoneId ?: stringResource(R.string.demo_follow_system)

    var title by rememberSaveable { mutableStateOf("") }
    var locale by rememberSaveable { mutableStateOf(DemoLocale.System) }

    // The four color groups, one per request parameter; every group starts on 默认 — the
    // library's own defaults until the user picks a swatch
    var lightAccent by rememberSaveable { mutableStateOf<Int?>(null) }
    var lightMark by rememberSaveable { mutableStateOf<Int?>(null) }
    var darkAccent by rememberSaveable { mutableStateOf<Int?>(null) }
    var darkMark by rememberSaveable { mutableStateOf<Int?>(null) }

    // The Compose entries: the same title and color picks as the activity launch above,
    // hosted in this window instead of a picker activity
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // The Compose entries take a single color per composition, so the mode on screen hands
    // them its own; the activity launch above carries both modes' values instead. Their
    // accentColor paints only the mark, so an unmarked mode falls back to its bar color —
    // the same value the activity entry's derived mark starts from
    val darkTheme = isSystemInDarkTheme()
    val composeAccent = if (darkTheme) darkAccent else lightAccent
    val composeMark = (if (darkTheme) darkMark else lightMark) ?: composeAccent

    // The 打勾色 groups' 默认 swatch shows what the picker really draws under 默认: the mark
    // derived from the currently picked accent — fallback included. Amber under a light
    // surface fails the 3:1 gate, so its 默认 mark swatch is the fallback text color, the
    // exact checkmark the picker will show
    val lightDerivedMark = derivedMarkColor(lightAccent, LIGHT_SURFACE, LIGHT_TEXT)
    val darkDerivedMark = derivedMarkColor(darkAccent, DARK_SURFACE, DARK_TEXT)

    val pickerTitle = title.trim().takeIf { it.isNotEmpty() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                // A scroll's unbounded height kills plain Arrangement.Center, so the column
                // pins its minimum height to the viewport instead: tall content scrolls,
                // short content centers — the behavior the unscrollable layout had
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                // Inside the scroll, not around it: the resting layout stays clear of the
                // status bar, navigation bar, cutouts and keyboard, while scrolled content
                // slides under the transparent bars — what edge-to-edge immersion is for.
                // After heightIn, so the viewport-minimum covers the full screen and the
                // padding carves the safe area out of it
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.demo_current, "$displayName ($zoneLabel)"),
                fontSize = 16.sp,
            )

            // Demonstrates the title parameter: a non-empty input becomes the picker's title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                label = { Text(stringResource(R.string.demo_custom_title_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(),
            )

            // The four color parameters, one swatch group per request channel — the bar and
            // the mark, each in both modes. 默认 omits the extra (the library's default accent
            // or derived mark); every explicit pick rides the contrast gate
            ColorGroup(label = stringResource(R.string.demo_color_accent_light), defaultColor = LIBRARY_ACCENT, selected = lightAccent, onSelect = {
                lightAccent =
                    it
            })
            ColorGroup(label = stringResource(R.string.demo_color_mark_light), defaultColor = lightDerivedMark, selected = lightMark, onSelect = {
                lightMark =
                    it
            })
            ColorGroup(label = stringResource(R.string.demo_color_accent_dark), defaultColor = LIBRARY_ACCENT, selected = darkAccent, onSelect = {
                darkAccent =
                    it
            })
            ColorGroup(label = stringResource(R.string.demo_color_mark_dark), defaultColor = darkDerivedMark, selected = darkMark, onSelect = { darkMark = it })

            // Demonstrates the locale parameter: the picker renders in the chosen language
            // — chrome strings, zone names and RTL all follow. The Compose entries below
            // render in this window instead and inherit its language, so they take none
            Text(
                text = stringResource(R.string.demo_locale_label),
                modifier = Modifier.padding(top = 16.dp),
                fontSize = 14.sp,
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DemoLocale.entries.forEach { option ->
                    FilterChip(
                        selected = locale == option,
                        onClick = { locale = option },
                        label = { Text(stringResource(option.label)) },
                    )
                }
            }

            Button(
                onClick = {
                    onPick(
                        title
                            .trim()
                            .takeIf { it.isNotEmpty() },
                        DemoColors(lightAccent, darkAccent, lightMark, darkMark),
                        locale.tag?.let(Locale::forLanguageTag),
                    )
                },
                modifier = Modifier.padding(top = 32.dp),
            ) {
                Text(stringResource(R.string.demo_pick), fontSize = 16.sp)
            }

            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.demo_pick_dialog))
                }

                OutlinedButton(
                    onClick = { showSheet = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.demo_pick_sheet))
                }
            }
        }
    }

    if (showDialog) {
        ZonePickerDialog(
            onResult = { result ->
                onZonePicked((result as? ZonePickerResult.Selected)?.zoneId)
                showDialog = false
            },
            onDismissRequest = { showDialog = false },
            selectedZoneId = zoneId,
            accentColor = composeMark.composeColor(),
            title = pickerTitle,
        )
    }

    if (showSheet) {
        ZonePickerBottomSheet(
            onResult = { result ->
                onZonePicked((result as? ZonePickerResult.Selected)?.zoneId)
                showSheet = false
            },
            onDismissRequest = { showSheet = false },
            selectedZoneId = zoneId,
            accentColor = composeMark.composeColor(),
            title = pickerTitle,
        )
    }
}

/** One labeled swatch group: a wrapped row of [COLOR_OPTIONS] chips with one selected. The
 *  label carries the channel and the mode it feeds; [defaultColor] paints the 默认 swatch
 *  with the color that channel actually renders when left default. */
@Composable
private fun ColorGroup(label: String, defaultColor: Int, selected: Int?, onSelect: (Int?) -> Unit) {
    Text(text = label, modifier = Modifier.padding(top = 16.dp), fontSize = 13.sp)

    // FlowRow: the palette outruns a phone's width; the extras wrap instead of clipping
    FlowRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        COLOR_OPTIONS.forEach { option ->
            ColorChip(
                label = stringResource(option.label),
                color = option.color ?: defaultColor,
                selected = selected == option.color,
                onClick = { onSelect(option.color) },
            )
        }
    }
}

/** One palette swatch as a chip with its color dot leading the label — 默认 included, whose
 *  dot shows the real color that channel renders under the default. */
@Composable
private fun ColorChip(label: String, color: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Box(
                modifier =
                Modifier
                    .size(14.dp)
                    // The hairline ring keeps the white swatch visible on any background
                    .border(1.dp, ComposeColor.Gray, CircleShape)
                    .background(ComposeColor(color), CircleShape),
            )
        },
    )
}

/** The color as the Compose entries take it: [ComposeColor.Unspecified] keeps the library default. */
private fun Int?.composeColor(): ComposeColor = this?.let(::ComposeColor) ?: ComposeColor.Unspecified

// Mirrors of the library's zpPrimaryColor and the zpSurface/zpTextPrimary day-night pair —
// all internal to the library module, so the demo carries its own copy for the 默认 swatches
private const val LIBRARY_ACCENT = 0xFFF05E1C.toInt()
private const val LIGHT_SURFACE = 0xFFFFFFFF.toInt()
private const val LIGHT_TEXT = 0xFF333333.toInt()
private const val DARK_SURFACE = 0xFF1E1E1E.toInt()
private const val DARK_TEXT = 0xFFE0E0E0.toInt()

/** The demo's copy of the library's derived-mark rule — what 打勾色 under 默认 really
 *  renders: the accent — picked, or the library default — while it clears 3:1 against that
 *  mode's surface, the row text color otherwise. A fallback-triggering accent flips the
 *  默认 swatch to the fallback color before the picker even opens. */
private fun derivedMarkColor(accent: Int?, surface: Int, fallback: Int): Int {
    val resolved = accent ?: LIBRARY_ACCENT

    return if (contrastRatio(resolved, surface) >= 3.0) resolved else fallback
}

private fun contrastRatio(first: Int, second: Int): Double {
    val firstLuminance = luminance(first)
    val secondLuminance = luminance(second)

    return (max(firstLuminance, secondLuminance) + 0.05) / (min(firstLuminance, secondLuminance) + 0.05)
}

private fun luminance(color: Int): Double {
    fun channel(value: Int): Double {
        val linear = value / 255.0

        return if (linear <= 0.03928) linear / 12.92 else ((linear + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * channel(color shr 16 and 0xFF) +
        0.7152 * channel(color shr 8 and 0xFF) +
        0.0722 * channel(color and 0xFF)
}
