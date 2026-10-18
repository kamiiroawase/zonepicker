package io.github.kamiiroawase.zonepicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The time zone picker as a Material bottom sheet in the caller's own window: the same
 * list, search and follow-system option as the activity entry, without launching an
 * Activity or handling results. A pick first plays the sheet's hide animation and only then
 * fires [onResult], so a caller that simply hides the sheet there gets a clean exit; drag
 * and scrim dismissals animate on their own and arrive as [onDismissRequest].
 *
 * The caller owns visibility: hide the sheet in both callbacks.
 *
 * ```kotlin
 * var showSheet by rememberSaveable { mutableStateOf(false) }
 *
 * if (showSheet) {
 *     ZonePickerBottomSheet(
 *         onResult = { result -> /* Selected or FollowSystem */ showSheet = false },
 *         onDismissRequest = { showSheet = false },
 *         selectedZoneId = zoneId,
 *     )
 * }
 * ```
 *
 * @param onResult fires [ZonePickerResult.Selected] or [ZonePickerResult.FollowSystem] once
 *   a tapped pick's hide animation has settled. Dismissals arrive as [onDismissRequest]
 *   only, so [ZonePickerResult.Canceled] is never delivered here.
 * @param onDismissRequest back press, drag-down or tap on the scrim
 * @param selectedZoneId currently selected zone ID (e.g. Asia/Shanghai), checkmarked in the
 *   list; null means "follow system". Legacy aliases (US/Pacific, Etc/UTC) resolve to the
 *   row the list actually shows.
 * @param accentColor accent color for the selection mark and search cursor; the library
 *   default ([Color.Unspecified], resolving the overridable `zpPrimaryColor` resource) when
 *   omitted — any alpha in the value is forced opaque
 * @param title panel title; the default `zp_title` string ("时区") when null
 */
// ModalBottomSheet remains experimental in Material 3; the opt-in stays on this internal
// use site — the public signature exposes nothing experimental
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonePickerBottomSheet(
    onResult: (ZonePickerResult) -> Unit,
    onDismissRequest: () -> Unit,
    selectedZoneId: String? = null,
    accentColor: Color = Color.Unspecified,
    title: String? = null,
) {
    val state = rememberZonePickerState(selectedZoneId, accentColor)
    val snapshot by state.snapshot.collectAsState()
    val scrollSelectionInitially = remember { selectedZoneId != null }

    // Resolved against the host window, outside the sheet's own: the list cap tracks the
    // screen the caller lives in
    val hostHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

    // The pick's own exit animation: the result fires once it settles, so a caller hiding
    // the sheet in onResult never cuts the animation mid-flight
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun pick(zoneId: String?) {
        scope.launch {
            sheetState.hide()

            onResult(
                if (zoneId == null) {
                    ZonePickerResult.FollowSystem
                } else {
                    ZonePickerResult.Selected(zoneId)
                },
            )
        }
    }

    // Mirrors on its own like the activity entry: hosts without RTL support still get a
    // correctly mirrored picker
    CompositionLocalProvider(
        LocalLayoutDirection provides PickerLocale.layoutDirection(LocalConfiguration.current.locales[0]),
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            containerColor = colorResource(R.color.zpBackground),
            // Insets owned below: the sheet's default content insets plus the content's own
            // ime and navigation-bar padding would double-pad the bottom
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Column(
                modifier =
                Modifier
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
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
                    onSelect = ::pick,
                    listShape = RoundedCornerShape(16.dp),
                    // A weighted card would force the sheet fully expanded on every open;
                    // the cap keeps the half-expanded state meaningful
                    listMaxHeight = hostHeight * 0.6f,
                )
            }
        }
    }
}
