package com.github.kamiiroawase.zonepicker

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import java.util.Locale

/** Caches the zone snapshot across configuration changes; rebuilds when the DST window expires. */
internal class ZonePickerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private var snapshot: Snapshot? = null

    fun snapshot(): Snapshot {
        val current = snapshot

        if (current != null && System.currentTimeMillis() - current.builtAtMillis < STALE_AFTER_MILLIS) {
            return current
        }

        return buildSnapshot().also { snapshot = it }
    }

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
