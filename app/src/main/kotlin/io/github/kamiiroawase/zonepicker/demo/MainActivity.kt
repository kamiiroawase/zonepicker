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
                DemoScreen(
                    zoneId = zoneId,
                    onPick = { title, accentColor ->
                        pickerLauncher.launch(
                            ZonePickerRequest(
                                selectedZoneId = zoneId,
                                accentColor = accentColor,
                                title = title,
                            ),
                        )
                    },
                    onZonePicked = { zoneId = it },
                )
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

/** Accent options the demo passes to the picker; each exercises a different runtime path. */
private enum class DemoAccent(@StringRes val label: Int, val color: Int?) {
    /** The library default: no intent extra, the resource accent. */
    Default(R.string.demo_accent_default, null),

    /** Bright enough for the black header chrome, yet below the 3:1 mark contrast on the
     *  light surface, so the day-mode checkmark shows the text-color fallback too (night
     *  mode keeps the amber mark). */
    Amber(R.string.demo_accent_amber, 0xFFFFD54F.toInt()),

    /** Flips the header chrome to white; the checkmark keeps the accent on the light
     *  surface and falls back to the text color on the dark one. */
    Black(R.string.demo_accent_black, Color.BLACK),

    /** Keeps the black chrome and forces the mark fallback on the light surface. */
    White(R.string.demo_accent_white, Color.WHITE),
}

@Composable
private fun DemoTheme(content: @Composable () -> Unit) {
    val accent = colorResource(R.color.demoAccent)
    val colorScheme =
        if (isSystemInDarkTheme()) darkColorScheme(primary = accent) else lightColorScheme(primary = accent)

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun DemoScreen(zoneId: String?, onPick: (title: String?, accentColor: Int?) -> Unit, onZonePicked: (String?) -> Unit) {
    val displayName =
        remember(zoneId) {
            val timeZone = zoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()

            timeZone.getDisplayName(false, TimeZone.LONG, Locale.SIMPLIFIED_CHINESE)
        }

    val zoneLabel = zoneId ?: stringResource(R.string.demo_follow_system)

    var title by rememberSaveable { mutableStateOf("") }
    var accent by rememberSaveable { mutableStateOf(DemoAccent.Default) }

    // The Compose entries: the same title and accent options as the activity launch above,
    // hosted in this window instead of a picker activity
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }

    val pickerTitle = title.trim().takeIf { it.isNotEmpty() }
    val pickerAccent = accent.composeColor()

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

            Button(
                onClick = {
                    onPick(
                        title
                            .trim()
                            .takeIf { it.isNotEmpty() },
                        accent.color,
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
            accentColor = pickerAccent,
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
            accentColor = pickerAccent,
            title = pickerTitle,
        )
    }
}

/** The accent as the Compose entries take it: [ComposeColor.Unspecified] keeps the library default. */
private fun DemoAccent.composeColor(): ComposeColor = color?.let(::ComposeColor) ?: ComposeColor.Unspecified
