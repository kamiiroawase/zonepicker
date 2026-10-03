package com.github.kamiiroawase.zonepicker

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale
import kotlin.concurrent.thread

/** Caches the zone snapshot across configuration changes; builds it off the main thread and
 *  rebuilds when the DST window expires. */
internal class ZonePickerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val snapshotLiveData = MutableLiveData<Snapshot>()

    /** Null while the first background build is still running. */
    val snapshot: LiveData<Snapshot> = snapshotLiveData

    @Volatile
    private var building = false

    init {
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

        thread(isDaemon = true) {
            val snapshot = buildSnapshot()

            building = false

            snapshotLiveData.postValue(snapshot)
        }
    }

    private fun isStale(snapshot: Snapshot): Boolean = System.currentTimeMillis() - snapshot.builtAtMillis >= STALE_AFTER_MILLIS

    private fun buildSnapshot(): Snapshot {
        val app = getApplication<Application>()

        val overrides =
            mapOf(
                "Asia/Taipei" to app.getString(R.string.zp_taipei_name),
                "Asia/Hong_Kong" to app.getString(R.string.zp_hong_kong_name),
                "Asia/Macau" to app.getString(R.string.zp_macau_name),
            )

        val zones =
            ZoneData.buildZones(
                System.currentTimeMillis(),
                overrides,
                nameLocale = nameLocale(),
            )

        return Snapshot(zones, ZoneData.defaultZones(zones), System.currentTimeMillis())
    }

    /**
     * Zone names follow the library UI language: the default Chinese strings yield Chinese
     * names on any device; a host overriding the strings to another locale gets names in the
     * app locale instead.
     */
    private fun nameLocale(): Locale {
        val app = getApplication<Application>()

        val chinese = Configuration(app.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }

        return if (app.createConfigurationContext(chinese).getString(R.string.zp_title) == app.getString(R.string.zp_title)) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            app.resources.configuration.locales[0] ?: Locale.getDefault()
        }
    }

    internal class Snapshot(
        val zones: List<ZoneData.Zone>,
        val defaultZones: List<ZoneData.Zone>,
        val builtAtMillis: Long,
    )

    private companion object {
        /** Offsets are DST snapshots; a short rebuild window bounds staleness at transitions. */
        private const val STALE_AFTER_MILLIS = 30 * 60 * 1000L
    }
}
