package io.github.kamiiroawase.zonepicker

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal sealed interface ZoneRow {
    data class Header(val title: String) : ZoneRow

    data class Item(val zoneId: String, val title: String, val subtitle: String, val selected: Boolean) : ZoneRow
}

/** Stable LazyColumn key for a row: headers own their offset label, items their zone ID. */
private val ZoneRow.key: Any
    get() =
        when (this) {
            is ZoneRow.Header -> "header:$title"
            is ZoneRow.Item -> zoneId
        }

/** The picker's Compose UI. Pure presentation: the snapshot, the launch-resolved selection
 *  and colors, and callbacks are the whole contract — window chrome (edge-to-edge, status-bar
 *  icon appearance) and the result intent stay with [ZonePickerActivity]. */
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
    val surfaceColor = colorResource(R.color.zpSurface)
    val pageBackground = colorResource(R.color.zpBackground)
    val textPrimary = colorResource(R.color.zpTextPrimary)
    val textSecondary = colorResource(R.color.zpTextSecondary)

    var query by rememberSaveable { mutableStateOf("") }

    // The query the list renders with, one typing-pause beat behind the keystrokes
    var renderedQuery by remember { mutableStateOf("") }

    // The first composition (initial launch or view-state restoration) renders synchronously;
    // only later keystrokes wait out the debounce, so a restored search never flashes the
    // default list through the delay the way a debounced-first-render would
    var immediateRender by remember { mutableStateOf(true) }

    LaunchedEffect(query) {
        if (immediateRender) {
            immediateRender = false
            renderedQuery = query
        } else {
            delay(SEARCH_RENDER_DEBOUNCE_MILLIS)
            renderedQuery = query
        }
    }

    // The search action is the query's final word — flush the pending beat now
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitSearch() {
        renderedQuery = query
        keyboardController?.hide()
    }

    val trimmedQuery = renderedQuery.trim()

    // Default shows popular zones covering all offsets; search matches against all zones
    val rows =
        remember(snapshot, trimmedQuery) {
            when {
                snapshot == null -> emptyList()
                trimmedQuery.isEmpty() -> buildRows(snapshot.defaultZones, selectedZoneId)
                else -> buildRows(ZoneData.filter(snapshot.zones, trimmedQuery, ZoneData.countryZoneIds(trimmedQuery)), selectedZoneId)
            }
        }

    // Brings the checked zone to the top the first time the rendered list contains it. A
    // selection filtered out by the current query waits for the query to clear; an unknown
    // zone ID gives up — it never shows a checkmark anyway.
    val listState = rememberLazyListState()
    var pendingSelectionScroll by remember { mutableStateOf(scrollSelectionInitially) }

    LaunchedEffect(snapshot, rows, pendingSelectionScroll) {
        if (!pendingSelectionScroll || snapshot == null) return@LaunchedEffect

        if (snapshot.zones.none { it.zoneId == selectedZoneId }) {
            pendingSelectionScroll = false

            return@LaunchedEffect
        }

        val index = rows.indexOfFirst { it is ZoneRow.Item && it.selected }

        if (index >= 0) {
            pendingSelectionScroll = false
            listState.scrollToItem(index)
        }
    }

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
            ) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onSubmit = ::submitSearch,
                    onClear = { query = "" },
                    surfaceColor = surfaceColor,
                    textSecondary = textSecondary,
                    markColor = markColor,
                )

                FollowSystemRow(
                    selected = selectedZoneId == null,
                    onSelect = onSelect,
                    surfaceColor = surfaceColor,
                    textPrimary = textPrimary,
                    markColor = markColor,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Surface(
                    // Top corners only: the card sits flush on the bottom accent band, and
                    // rounded bottom corners would leave page-background notches there
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = surfaceColor,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                                when (row) {
                                    is ZoneRow.Header -> ZoneHeaderRow(row.title)

                                    is ZoneRow.Item ->
                                        ZoneItemRow(
                                            row = row,
                                            // No divider under the list's last row, nor above a
                                            // group header: the header's own top padding separates
                                            // the groups instead of hanging off the previous one
                                            showDivider = index < rows.lastIndex && rows[index + 1] is ZoneRow.Item,
                                            onSelect = onSelect,
                                            textPrimary = textPrimary,
                                            textSecondary = textSecondary,
                                            markColor = markColor,
                                        )
                                }
                            }
                        }

                        // The spinner stands in for the list until the first snapshot lands; a
                        // stale-triggered rebuild never resurrects it, as the current snapshot
                        // stays rendered meanwhile
                        if (snapshot == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = textSecondary,
                            )
                        } else if (rows.isEmpty()) {
                            // An empty list while the snapshot is still loading is not a "no result" state
                            Text(
                                text = stringResource(R.string.zp_no_result),
                                modifier = Modifier.align(Alignment.Center),
                                color = textSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        // The accent band mirrors the header at the bottom: as tall as the taller of the
        // navigation bar and the keyboard (which covers it anyway — the weight(1f) content
        // above reflows exactly as bottom-insets padding made it). The 16dp floor is
        // portrait-only: devices with a hidden gesture hint report a zero bottom inset,
        // and without a floor the footer would vanish and leave the card surface as the
        // bottom color — but landscape drops a side-placed navigation bar's bottom inset
        // to zero too, where a floor would draw a stray accent strip below the content
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .background(accent)
                .heightIn(min = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 16.dp else 0.dp)
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

/** Border-less search field on a rounded surface: leading search icon, trailing clear
 *  affordance, hint while empty, and the cursor tinted to the selection mark color. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    surfaceColor: Color,
    textSecondary: Color,
    markColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = textSecondary,
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                textStyle = TextStyle(color = colorResource(R.color.zpTextStrong), fontSize = 15.sp),
                cursorBrush = SolidColor(markColor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.zp_search_hint),
                                color = colorResource(R.color.zpTextHint),
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }

                        innerTextField()
                    }
                },
            )

            // The clear affordance tracks the text synchronously; the list filter stays one
            // debounce beat behind
            if (query.trim().isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.zp_clear_search),
                    modifier =
                    Modifier
                        .size(48.dp)
                        .clickable(onClick = onClear)
                        .padding(12.dp),
                    tint = textSecondary,
                )
            }
        }
    }
}

/** The pinned "follow system" row above the list; checkmarked when nothing else is. */
@Composable
private fun FollowSystemRow(
    selected: Boolean,
    onSelect: (String?) -> Unit,
    surfaceColor: Color,
    textPrimary: Color,
    markColor: Color,
    modifier: Modifier = Modifier,
) {
    val selectedDescription = stringResource(R.string.zp_selected)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .semantics { if (selected) stateDescription = selectedDescription }
                .clickable { onSelect(null) }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.zp_follow_system),
                modifier = Modifier.weight(1f),
                color = textPrimary,
                fontSize = 15.sp,
            )

            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier =
                    Modifier
                        .padding(start = 6.dp)
                        .size(20.dp),
                    tint = markColor,
                )
            }
        }
    }
}

/** Offset band header; TalkBack announces it as a heading, not another line of text. */
@Composable
private fun ZoneHeaderRow(title: String) {
    Text(
        text = title,
        modifier =
        Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 6.dp),
        color = colorResource(R.color.zpTextSecondary),
        fontSize = 13.sp,
    )
}

/** One zone row: display name over the zone ID, checkmark when selected, divider unless a
 *  group boundary follows. */
@Composable
private fun ZoneItemRow(row: ZoneRow.Item, showDivider: Boolean, onSelect: (String?) -> Unit, textPrimary: Color, textSecondary: Color, markColor: Color) {
    val selectedDescription = stringResource(R.string.zp_selected)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .semantics { if (row.selected) stateDescription = selectedDescription }
                .clickable { onSelect(row.zoneId) }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = row.title,
                    color = textPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = row.subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (row.selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier =
                    Modifier
                        .padding(start = 6.dp)
                        .size(20.dp),
                    tint = markColor,
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = colorResource(R.color.zpDivider),
            )
        }
    }
}

/** Flattens a zone list into header + item rows, grouped and ordered by GMT offset. */
private fun buildRows(zones: List<ZoneData.Zone>, selectedZoneId: String?): List<ZoneRow> {
    val rows = mutableListOf<ZoneRow>()

    zones.groupBy { it.offsetSeconds }.forEach { (offset, group) ->
        rows += ZoneRow.Header(ZoneData.offsetLabel(offset))

        group.forEach {
            rows +=
                ZoneRow.Item(
                    zoneId = it.zoneId,
                    title = it.displayName,
                    subtitle = it.zoneId,
                    selected = selectedZoneId == it.zoneId,
                )
        }
    }

    return rows
}

/** Typing pause the list filter waits out; the immediate query UI (the clear button) never waits. */
private const val SEARCH_RENDER_DEBOUNCE_MILLIS = 120L
