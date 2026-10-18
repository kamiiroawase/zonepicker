package io.github.kamiiroawase.zonepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

/** Caches the zone snapshot across configuration changes; builds it off the main thread and
 *  rebuilds when the DST window expires. */
internal class ZonePickerViewModel(
    /** Where snapshot builds run; tests inject the test scheduler for determinism. */
    private val buildDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Clock behind the staleness window and the DST snapshot instant; injectable likewise. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val snapshotMutable = MutableStateFlow<Snapshot?>(null)

    /** Null while the first background build is still running. */
    val snapshot: StateFlow<Snapshot?> = snapshotMutable.asStateFlow()

    /** Display-name inputs for the current UI language, resolved against the picker
     *  activity's context — per-app locales never reach the application context, so only
     *  the activity can supply these. Main-thread confined, like every entry point. */
    private var zoneNames: ZoneNames? = null

    /** Host-supplied default-list zones, or null for the library's curated 27. */
    private var defaultZoneIds: List<String>? = null

    /** The in-flight build; a newer rebuild cancels and replaces it, and clearing the
     *  ViewModel cancels it along with the scope. */
    private var buildJob: Job? = null

    /** First call starts the initial build; a later call with a different value — an in-app
     *  language switch surviving recreation — restarts the build with the new names. */
    fun setZoneNames(names: ZoneNames) {
        if (zoneNames == names) return

        zoneNames = names

        rebuild()
    }

    /** Same contract as [setZoneNames]: the first call starts the initial build, a changed
     *  value rebuilds, an equal one does not. */
    fun setDefaultZoneIds(ids: List<String>?) {
        if (defaultZoneIds == ids) return

        defaultZoneIds = ids

        rebuild()
    }

    /** Starts a background rebuild when the cached snapshot has crossed the DST window. */
    fun requestFreshSnapshot() {
        // An in-flight build lands a fresh snapshot anyway; restarting would only duplicate work
        if (buildJob?.isActive == true) return

        val current = snapshotMutable.value

        if (current == null || isStale(current)) rebuild()
    }

    private fun rebuild() {
        // Cancels the in-flight build instead of letting it land: its names are already
        // outdated, and only the latest build should ever reach the picker
        buildJob?.cancel()

        val names = zoneNames ?: DEFAULT_ZONE_NAMES

        buildJob =
            viewModelScope.launch {
                val snapshot = withContext(buildDispatcher) { buildSnapshot(names) }

                snapshotMutable.value = snapshot
            }
    }

    private fun isStale(snapshot: Snapshot): Boolean = nowMillis() - snapshot.builtAtMillis >= STALE_AFTER_MILLIS

    private fun buildSnapshot(names: ZoneNames): Snapshot {
        val now = nowMillis()

        // The default list anchors its circular order at the device's own offset — DST-aware
        // at the same instant the zone offsets snapshot — so the user's zone group leads.
        // A system-timezone change mid-session only catches up at the next rebuild window;
        // picking a zone because you traveled means the anchor was already right at open
        val anchorOffsetSeconds = TimeZone.getDefault().getOffset(now) / 1000

        val zones =
            ZoneData.buildZones(
                now,
                names.overrides,
                nameLocale = names.nameLocale,
                anchorOffsetSeconds = anchorOffsetSeconds,
            )

        return Snapshot(zones, ZoneData.defaultZones(zones, defaultZoneIds?.toSet(), anchorOffsetSeconds), now)
    }

    /** Zone display-name inputs: the generation locale plus host-overridable names. */
    internal data class ZoneNames(val nameLocale: Locale, val overrides: Map<String, String>)

    internal class Snapshot(val zones: List<ZoneData.Zone>, val defaultZones: List<ZoneData.Zone>, val builtAtMillis: Long)

    internal companion object {
        /** Offsets are DST snapshots; a short rebuild window bounds staleness at transitions. */
        const val STALE_AFTER_MILLIS = 30 * 60 * 1000L

        /** Only used if a build somehow starts before the activity has pushed real values. */
        private val DEFAULT_ZONE_NAMES = ZoneNames(Locale.SIMPLIFIED_CHINESE, emptyMap())
    }
}
