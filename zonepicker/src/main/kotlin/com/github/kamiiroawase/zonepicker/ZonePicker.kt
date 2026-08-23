package com.github.kamiiroawase.zonepicker

import android.content.Context
import android.content.Intent

/**
 * Entry point of the time zone picker: curated grouped list + full search + follow-system option.
 * The result is returned via Activity result; persistence is left to the host app.
 */
object ZonePicker {
    const val EXTRA_ZONE_ID = "com.github.kamiiroawase.zonepicker.EXTRA_ZONE_ID"
    const val EXTRA_ACCENT_COLOR = "com.github.kamiiroawase.zonepicker.EXTRA_ACCENT_COLOR"
    const val EXTRA_TITLE = "com.github.kamiiroawase.zonepicker.EXTRA_TITLE"

    /**
     * @param selectedZoneId currently selected zone ID (e.g. Asia/Shanghai), checkmarked in the
     * list; null means "follow system"
     * @param accentColor accent color (header background, checkmark); library default when omitted
     * @param title page title
     */
    fun createIntent(
        context: Context,
        selectedZoneId: String? = null,
        accentColor: Int? = null,
        title: CharSequence? = null,
    ): Intent =
        Intent(context, ZonePickerActivity::class.java).apply {
            selectedZoneId?.let { putExtra(EXTRA_ZONE_ID, it) }
            accentColor?.let { putExtra(EXTRA_ACCENT_COLOR, it) }
            title?.let { putExtra(EXTRA_TITLE, it) }
        }

    /**
     * Parses the picker result; call only when the result code is RESULT_OK.
     * Returns the zone ID, or null for "follow system".
     */
    fun getResultZoneId(data: Intent?): String? = data?.getStringExtra(EXTRA_ZONE_ID)
}
