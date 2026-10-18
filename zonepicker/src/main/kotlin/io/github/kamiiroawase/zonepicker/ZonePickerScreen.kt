package io.github.kamiiroawase.zonepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The full-screen activity page: the shared [ZonePickerBody] under an accent header band,
 *  between the edge-to-edge insets the activity owns. Pure presentation: the snapshot, the
 *  launch-resolved selection and colors, and callbacks are the whole contract — window chrome
 *  (edge-to-edge, status-bar icon appearance) and the result intent stay with
 *  [ZonePickerActivity]. */
@Composable
internal fun ZonePickerScreen(
    snapshot: ZonePickerViewModel.Snapshot?,
    selectedZoneId: String?,
    accent: Color,
    onAccent: Color,
    markColor: Color,
    title: String,
    scrollSelectionInitially: Boolean,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val pageBackground = colorResource(R.color.zpBackground)

    Column(modifier = Modifier.fillMaxSize()) {
        PickerHeader(
            title = title,
            accent = accent,
            onAccent = onAccent,
            onBack = onBack,
        )

        // The accent fills behind the side system-bar and cutout areas as well — landscape
        // places a 3-button bar on a side, and the strip it covers must read as part of
        // the accent bands, not as a raw window-background gap between them. The content
        // column repaints the page background inside its own insets
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(accent),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
                    ).background(pageBackground)
                    // No bottom margin: it would read as a translucent strip between the
                    // list card and the accent band; the band sits flush under the card
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                // The body's children cap at MAX_CONTENT_WIDTH on wide screens and center
                // here; phones fill the width and never notice
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ZonePickerBody(
                    snapshot = snapshot,
                    selectedZoneId = selectedZoneId,
                    markColor = markColor,
                    scrollSelectionInitially = scrollSelectionInitially,
                    onSelect = onSelect,
                    // Top corners only: the card sits flush on the bottom accent band, and
                    // rounded bottom corners would leave page-background notches there
                    listShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    listMaxHeight = null,
                )
            }
        }

        // The accent band mirrors the header over the bottom system-bar area: as tall as the
        // taller of the navigation bar and the keyboard (which covers it anyway — the
        // weight(1f) content above reflows exactly as bottom-insets padding made it). No
        // minimum height: a zero bottom inset — no navigation bar at all, e.g. a hidden
        // gesture hint — collapses the band entirely and the content column reaches the
        // window's bottom edge, instead of a stray accent strip painting over nothing
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(accent)
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom),
                ),
        )
    }
}

/** Accent header band: the back affordance's fixed 54dp square sizes the band, the title is
 *  centered between two 54dp gutters, and the accent background fills behind the status bar
 *  and side cutouts (the insets padding shrinks only the content). */
@Composable
private fun PickerHeader(title: String, accent: Color, onAccent: Color, onBack: () -> Unit) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(accent)
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.zp_back),
            modifier =
            Modifier
                .align(Alignment.CenterStart)
                .size(54.dp)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            tint = onAccent,
        )

        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 54.dp),
            color = onAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
