package io.github.kamiiroawase.zonepicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.Dp
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

/** The picker's interactive column — search field, follow-system row and the grouped zone
 *  list — shared verbatim by every entry point: the full-screen activity page, the dialog
 *  and the bottom sheet. Callers own the chrome around it (title, accent bands, window
 *  insets, outer padding) and the list card's geometry: [listShape] (the activity's card
 *  sits flush on the bottom accent band, so only its top corners round) and the height mode
 *  ([listMaxHeight] null fills the remaining column; a cap keeps the bottom sheet's
 *  partially expanded state meaningful — a weighted card would force the sheet fully
 *  expanded on every open).
 *
 *  ColumnScope: the list card takes the remaining height via weight when [listMaxHeight] is
 *  null, so the caller's column must have bounded height. */
@Composable
internal fun ColumnScope.ZonePickerBody(
    snapshot: ZonePickerViewModel.Snapshot?,
    selectedZoneId: String?,
    markColor: Color,
    scrollSelectionInitially: Boolean,
    onSelect: (String?) -> Unit,
    listShape: Shape,
    listMaxHeight: Dp?,
) {
    val surfaceColor = colorResource(R.color.zpSurface)
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

    // Default shows popular zones covering all offsets; search matches against all zones,
    // with country-name and Chinese city-name queries widening the match by zone ID
    val rows =
        remember(snapshot, trimmedQuery) {
            when {
                snapshot == null -> emptyList()

                trimmedQuery.isEmpty() -> buildRows(snapshot.defaultZones, selectedZoneId)

                else -> buildRows(
                    ZoneData.filter(snapshot.zones, trimmedQuery, ZoneData.countryZoneIds(trimmedQuery) + ZoneData.cityZoneIds(trimmedQuery)),
                    selectedZoneId,
                )
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
        shape = listShape,
        color = surfaceColor,
        modifier =
        Modifier
            .fillMaxWidth()
            .then(
                if (listMaxHeight == null) {
                    Modifier.weight(1f)
                } else {
                    Modifier.heightIn(max = listMaxHeight)
                },
            ).padding(top = 12.dp),
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

/** Left-aligned panel title for the dialog and bottom-sheet entries: the same 18sp bold
 *  type the activity's accent header band carries, on the plain surface instead of the
 *  accent. */
@Composable
internal fun ZonePickerTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        color = colorResource(R.color.zpTextPrimary),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
