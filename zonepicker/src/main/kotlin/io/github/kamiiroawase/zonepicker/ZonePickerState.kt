package io.github.kamiiroawase.zonepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow

/** State behind the Compose entries ([ZonePickerDialog], [ZonePickerBottomSheet]). */
internal class ZonePickerState(
    /** The zone list build; null while the first background build is still running. */
    val snapshot: StateFlow<ZonePickerViewModel.Snapshot?>,

    /** The incoming selection resolved to the row the list actually shows (legacy aliases
     *  mapped), or null for "follow system". */
    val selectedZoneId: String?,

    /** Checkmark and cursor color: the accent while it contrasts with the surface, the row
     *  text color otherwise. */
    val markColor: Color,
)

/** Wires the Compose entries to the host-scoped [ZonePickerViewModel]: one instance per host
 *  ViewModelStore, so successive opens reuse the cached snapshot and an activity-entry pick
 *  and a composable-entry pick agree. Mirrors the activity's lifecycle contract — names push
 *  on (re)composition, a DST-staleness check on every host resume. */
@Composable
internal fun rememberZonePickerState(selectedZoneId: String?, accentColor: Color, defaultZoneIds: List<String>? = null): ZonePickerState {
    val viewModel = viewModel<ZonePickerViewModel>()
    val context = LocalContext.current

    // A locale change recreates the host composition with a fresh configuration, so keying
    // on the context covers it without re-resolving resources every recomposition
    val zoneNames = remember(context) { PickerLocale.zoneNames(context) }

    // One push per open carries both inputs, so the initial build runs once under both
    // instead of being cancelled and restarted by a second push
    LaunchedEffect(zoneNames, defaultZoneIds) { viewModel.setInputs(zoneNames, defaultZoneIds) }

    LifecycleResumeEffect(Unit) {
        viewModel.requestFreshSnapshot()
        onPauseOrDispose { }
    }

    // colorResource, not context.getColor: the composable re-resolves on configuration
    // changes (day/night flips the resource values), which the raw context read would miss
    val accent =
        if (accentColor == Color.Unspecified) {
            colorResource(R.color.zpPrimaryColor)
        } else {
            accentColor.copy(alpha = 1f)
        }

    // These entries paint no bands — accentColor IS the mark color. An explicit one is
    // honored as handed in (same contract as the activity's explicit markColor: alpha forced
    // opaque, contrast gate skipped); only the resource-default accent derives its mark
    // through the gate, falling back to the row text color below 3:1
    val markColor =
        if (accentColor != Color.Unspecified) {
            accent
        } else {
            Color(
                AccentColors.markColor(
                    accent.toArgb(),
                    colorResource(R.color.zpSurface).toArgb(),
                    colorResource(R.color.zpTextPrimary).toArgb(),
                ),
            )
        }

    return ZonePickerState(
        snapshot = viewModel.snapshot,
        selectedZoneId = remember(selectedZoneId) { selectedZoneId?.let(ZoneData::resolveDisplayZoneId) },
        markColor = markColor,
    )
}
