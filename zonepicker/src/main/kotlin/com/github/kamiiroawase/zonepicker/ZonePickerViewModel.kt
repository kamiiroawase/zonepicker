package com.github.kamiiroawase.zonepicker

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import kotlin.concurrent.thread

/** Caches the zone snapshot across configuration changes; builds it off the main thread and
 *  rebuilds when the DST window expires. */
internal class ZonePickerViewModel : ViewModel() {
    private val snapshotLiveData = MutableLiveData<Snapshot>()

    /** Null while the first background build is still running. */
    val snapshot: LiveData<Snapshot> = snapshotLiveData

    /** Display-name inputs for the current UI language, resolved against the picker
     *  activity's context — AppCompat per-app locales never reach the application context,
     *  so only the activity can supply these. Main-thread confined, like [building]. */
    private var zoneNames: ZoneNames? = null

    private var building = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /** First call starts the initial build; a later call with a different value — an in-app
     *  language switch surviving recreation — triggers a rebuild. */
    fun setZoneNames(names: ZoneNames) {
        if (zoneNames == names) return

        zoneNames = names

        rebuild()
    }

    /** Starts a background rebuild when the cached snapshot has crossed the DST window. */
    fun requestFreshSnapshot() {
        val current = snapshotLiveData.value

        if (current == null || isStale(current)) rebuild()
    }

    private fun rebuild() {
        if (building) return

        building = true

        val names = zoneNames ?: DEFAULT_ZONE_NAMES

        thread(isDaemon = true) {
            val snapshot = buildSnapshot(names)

            snapshotLiveData.postValue(snapshot)

            // The flag clears on the main thread only after the posted value has landed there
            // (both runnables queue in order on the main looper), so requestFreshSnapshot
            // never observes building == false alongside the stale value. A names change that
            // arrived mid-build — its setZoneNames call was skipped by the guard above —
            // starts the deferred rebuild here.
            mainHandler.post {
                building = false

                if (zoneNames != names) rebuild()
            }
        }
    }

    private fun isStale(snapshot: Snapshot): Boolean = System.currentTimeMillis() - snapshot.builtAtMillis >= STALE_AFTER_MILLIS

    private fun buildSnapshot(names: ZoneNames): Snapshot {
        val now = System.currentTimeMillis()

        val zones =
            ZoneData.buildZones(
                now,
                names.overrides,
                nameLocale = names.nameLocale,
            )

        return Snapshot(zones, ZoneData.defaultZones(zones), now)
    }

    /** Zone display-name inputs: the generation locale plus host-overridable names. */
    internal data class ZoneNames(
        val nameLocale: Locale,
        val overrides: Map<String, String>,
    )

    internal class Snapshot(
        val zones: List<ZoneData.Zone>,
        val defaultZones: List<ZoneData.Zone>,
        val builtAtMillis: Long,
    )

    private companion object {
        /** Offsets are DST snapshots; a short rebuild window bounds staleness at transitions. */
        private const val STALE_AFTER_MILLIS = 30 * 60 * 1000L

        /** Only used if a build somehow starts before the activity has pushed real values. */
        private val DEFAULT_ZONE_NAMES = ZoneNames(Locale.SIMPLIFIED_CHINESE, emptyMap())
    }
}
