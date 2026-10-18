package io.github.kamiiroawase.zonepicker

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Entry point of the time zone picker: curated grouped list + full search + follow-system option.
 * The result is returned via Activity result; persistence is left to the host app.
 */
object ZonePicker {
    const val EXTRA_ZONE_ID = "io.github.kamiiroawase.zonepicker.EXTRA_ZONE_ID"
    const val EXTRA_ACCENT_COLOR = "io.github.kamiiroawase.zonepicker.EXTRA_ACCENT_COLOR"
    const val EXTRA_TITLE = "io.github.kamiiroawase.zonepicker.EXTRA_TITLE"
    const val EXTRA_LOCALE = "io.github.kamiiroawase.zonepicker.EXTRA_LOCALE"
    const val EXTRA_DEFAULT_ZONE_IDS = "io.github.kamiiroawase.zonepicker.EXTRA_DEFAULT_ZONE_IDS"

    /**
     * @param selectedZoneId currently selected zone ID (e.g. Asia/Shanghai), checkmarked in the
     * list; null means "follow system"
     * @param accentColor accent color (header background, checkmark); library default when
     * omitted — any alpha in the value is forced opaque
     * @param title page title
     * @param locale explicit UI locale (BCP-47 tag serialized): strings, zone display names
     * and layout direction all follow it. For an in-app language switcher on API 26-32, where
     * no platform per-app locale exists for the picker activity to pick up; null (default)
     * follows the system locale (and the system per-app locale from API 33)
     * @param defaultZoneIds the default list's zones (e.g. an app's key markets); the
     * library's curated 27-zone list when null. Legacy alias IDs (US/Pacific, Etc/UTC)
     * resolve to the row the list actually shows; unresolvable IDs drop out, and every GMT
     * offset the list leaves uncovered still gets one representative zone
     */
    fun createIntent(
        context: Context,
        selectedZoneId: String? = null,
        accentColor: Int? = null,
        title: CharSequence? = null,
        locale: Locale? = null,
        defaultZoneIds: List<String>? = null,
    ): Intent = Intent(context, ZonePickerActivity::class.java).apply {
        selectedZoneId?.let { putExtra(EXTRA_ZONE_ID, it) }
        accentColor?.let { putExtra(EXTRA_ACCENT_COLOR, it) }
        title?.let { putExtra(EXTRA_TITLE, it) }
        locale?.let { putExtra(EXTRA_LOCALE, it.toLanguageTag()) }
        defaultZoneIds?.let { putStringArrayListExtra(EXTRA_DEFAULT_ZONE_IDS, ArrayList(it)) }
    }

    /**
     * Parses the picker result; call only when the result code is RESULT_OK.
     * Returns the zone ID, or null for "follow system".
     */
    fun getResultZoneId(data: Intent?): String? = data?.getStringExtra(EXTRA_ZONE_ID)
}
