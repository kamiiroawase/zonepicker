package io.github.kamiiroawase.zonepicker.demo

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
                        onPick = { title, accent, locale ->
                            pickerLauncher.launch(
                                ZonePickerRequest(
                                    selectedZoneId = zoneId,
                                    accentColor = accent.light.bar,
                                    accentColorDark = accent.dark.bar,
                                    markColor = accent.light.mark,
                                    markColorDark = accent.dark.mark,
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

/** One mode's accent inputs the demo hands to the picker: [bar] feeds the activity entry's
 *  header/status-bar band extra, [mark] its explicit checkmark extra; null keeps the
 *  library's default or derived behavior for that input. */
private data class AccentChoice(val bar: Int?, val mark: Int?)

/** Accent options the demo passes to the picker: each carries a light/dark pair of
 *  [AccentChoice]s, and the scheme on screen when the picker opens decides which one reaches
 *  it — a color that reads in one mode can melt into the page of the other (black on the
 *  dark background). */
private enum class DemoAccent(@StringRes val label: Int, val light: AccentChoice, val dark: AccentChoice) {
    /** The library default: no extras — the resource accent with the mark derived from it. */
    Default(R.string.demo_accent_default, AccentChoice(null, null), AccentChoice(null, null)),

    /** Bright enough for the black header chrome, yet below the 3:1 mark contrast on the
     *  light surface, so the day-mode checkmark shows the text-color fallback too (night
     *  mode keeps the amber mark). The same inputs in both modes — the pair need not differ
     *  just because it can. */
    Amber(
        R.string.demo_accent_amber,
        AccentChoice(0xFFFFD54F.toInt(), null),
        AccentChoice(0xFFFFD54F.toInt(), null),
    ),

    /** Flips the header chrome to white; the derived checkmark keeps the accent on the light
     *  surface and falls back to the text color on the dark one. Night swaps the black —
     *  which would sink into the dark page — for a deep indigo bar too dark to check with,
     *  so an explicit brighter indigo mark rides along: the bar stays subtle, the mark stays
     *  itself. */
    Black(
        R.string.demo_accent_black,
        AccentChoice(Color.BLACK, null),
        AccentChoice(0xFF3949AB.toInt(), 0xFF9FA8DA.toInt()),
    ),

    /** Keeps the black chrome and forces the mark fallback on the light surface; white reads
     *  against both page colors as-is. */
    White(
        R.string.demo_accent_white,
        AccentChoice(Color.WHITE, null),
        AccentChoice(Color.WHITE, null),
    ),
}

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
private fun DemoScreen(zoneId: String?, onPick: (title: String?, accent: DemoAccent, locale: Locale?) -> Unit, onZonePicked: (String?) -> Unit) {
    val displayName =
        remember(zoneId) {
            val timeZone = zoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()

            timeZone.getDisplayName(false, TimeZone.LONG, Locale.SIMPLIFIED_CHINESE)
        }

    val zoneLabel = zoneId ?: stringResource(R.string.demo_follow_system)

    var title by rememberSaveable { mutableStateOf("") }
    var accent by rememberSaveable { mutableStateOf(DemoAccent.Default) }
    var locale by rememberSaveable { mutableStateOf(DemoLocale.System) }

    // The Compose entries: the same title and accent options as the activity launch above,
    // hosted in this window instead of a picker activity
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // The Compose entries take a single color per composition, so the mode on screen hands
    // them its own; the activity launch above carries both modes' values instead
    val darkTheme = isSystemInDarkTheme()
    val choice = accent.choice(darkTheme)

    val pickerTitle = title.trim().takeIf { it.isNotEmpty() }

    // The Compose entries paint no bar — their accentColor is the mark color itself — so they
    // take the explicit mark when this option defines one, the bar color otherwise: the same
    // value the activity entry's derived mark starts from
    val composeMark = choice.mark ?: choice.bar

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

            // Demonstrates the accentColor parameter: each chip passes a different accent —
            // see DemoAccent for the runtime path each one exercises
            Text(
                text = stringResource(R.string.demo_accent_label),
                modifier = Modifier.padding(top = 16.dp),
                fontSize = 14.sp,
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DemoAccent.entries.forEach { option ->
                    FilterChip(
                        selected = accent == option,
                        onClick = { accent = option },
                        label = { Text(stringResource(option.label)) },
                    )
                }
            }

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
                        accent,
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

/** One mode's accent inputs for this option. */
private fun DemoAccent.choice(darkTheme: Boolean): AccentChoice = if (darkTheme) dark else light

/** The color as the Compose entries take it: [ComposeColor.Unspecified] keeps the library default. */
private fun Int?.composeColor(): ComposeColor = this?.let(::ComposeColor) ?: ComposeColor.Unspecified
