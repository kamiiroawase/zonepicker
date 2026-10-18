package io.github.kamiiroawase.zonepicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The time zone picker as a centered dialog in the caller's own window: the same list,
 * search and follow-system option as the activity entry, without launching an Activity or
 * handling results — compose it from anywhere in a Compose hierarchy.
 *
 * The caller owns visibility: hide the dialog in both callbacks.
 *
 * ```kotlin
 * var showDialog by rememberSaveable { mutableStateOf(false) }
 *
 * if (showDialog) {
 *     ZonePickerDialog(
 *         onResult = { result -> /* Selected or FollowSystem */ showDialog = false },
 *         onDismissRequest = { showDialog = false },
 *         selectedZoneId = zoneId,
 *     )
 * }
 * ```
 *
 * @param onResult fires [ZonePickerResult.Selected] or [ZonePickerResult.FollowSystem] as
 *   soon as a row is tapped. Dismissals arrive as [onDismissRequest] only, so
 *   [ZonePickerResult.Canceled] is never delivered here.
 * @param onDismissRequest back press or tap outside the dialog
 * @param selectedZoneId currently selected zone ID (e.g. Asia/Shanghai), checkmarked in the
 *   list; null means "follow system". Legacy aliases (US/Pacific, Etc/UTC) resolve to the
 *   row the list actually shows.
 * @param accentColor accent color for the selection mark and search cursor; the library
 *   default ([Color.Unspecified], resolving the overridable `zpPrimaryColor` resource) when
 *   omitted — any alpha in the value is forced opaque
 * @param title panel title; the default `zp_title` string ("时区") when null
 */
@Composable
fun ZonePickerDialog(
    onResult: (ZonePickerResult) -> Unit,
    onDismissRequest: () -> Unit,
    selectedZoneId: String? = null,
    accentColor: Color = Color.Unspecified,
    title: String? = null,
) {
    val state = rememberZonePickerState(selectedZoneId, accentColor)
    val snapshot by state.snapshot.collectAsState()
    val scrollSelectionInitially = remember { selectedZoneId != null }

    // Resolved against the host window, outside the dialog's own: the height cap tracks the
    // screen the caller lives in, whatever the dialog window reports for itself
    val hostHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

    // Mirrors on its own like the activity entry: hosts without RTL support still get a
    // correctly mirrored picker
    CompositionLocalProvider(
        LocalLayoutDirection provides PickerLocale.layoutDirection(LocalConfiguration.current.locales[0]),
    ) {
        Dialog(
            onDismissRequest = onDismissRequest,
            // A decorFitsSystemWindows=false window hands the insets to the content, so the
            // imePadding below lifts the card above the keyboard instead of the platform
            // resizing the window and clipping the card; the platform-default width cap is
            // dropped in favor of the explicit one on the card
            properties = DialogProperties(decorFitsSystemWindows = false, usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colorResource(R.color.zpBackground),
                modifier =
                Modifier
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    // Most of the screen but never all of it: the centered card keeps clear
                    // of the status and navigation bars on every size class
                    .heightIn(max = hostHeight * 0.8f),
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(all = 24.dp),
                ) {
                    ZonePickerTitle(
                        text = title ?: stringResource(R.string.zp_title),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    ZonePickerBody(
                        snapshot = snapshot,
                        selectedZoneId = state.selectedZoneId,
                        markColor = state.markColor,
                        scrollSelectionInitially = scrollSelectionInitially,
                        onSelect = { zoneId ->
                            onResult(
                                if (zoneId == null) {
                                    ZonePickerResult.FollowSystem
                                } else {
                                    ZonePickerResult.Selected(zoneId)
                                },
                            )
                        },
                        listShape = RoundedCornerShape(16.dp),
                        listMaxHeight = null,
                    )
                }
            }
        }
    }
}
