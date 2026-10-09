package io.github.kamiiroawase.zonepicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/** Parameters for [ZonePickerContract]; a null selectedZoneId means "follow system". */
data class ZonePickerRequest(
    val selectedZoneId: String? = null,
    val accentColor: Int? = null,
    val title: CharSequence? = null,
)

/** Picker outcome; distinguishes follow-system from cancellation, unlike the raw intent API. */
sealed interface ZonePickerResult {
    data object FollowSystem : ZonePickerResult

    data class Selected(
        val zoneId: String,
    ) : ZonePickerResult

    data object Canceled : ZonePickerResult
}

/** Result classification behind [ZonePickerContract.parseResult]: a non-OK result is a
 *  cancellation no matter what data the platform attached, and only RESULT_OK distinguishes
 *  follow-system (no zone ID) from selected. */
internal fun parsePickerResult(
    resultCode: Int,
    zoneId: String?,
): ZonePickerResult =
    when {
        resultCode != Activity.RESULT_OK -> ZonePickerResult.Canceled
        zoneId == null -> ZonePickerResult.FollowSystem
        else -> ZonePickerResult.Selected(zoneId)
    }

/** Type-safe contract for launching the picker via registerForActivityResult. */
class ZonePickerContract : ActivityResultContract<ZonePickerRequest, ZonePickerResult>() {
    override fun createIntent(
        context: Context,
        input: ZonePickerRequest,
    ): Intent = ZonePicker.createIntent(context, input.selectedZoneId, input.accentColor, input.title)

    override fun parseResult(
        resultCode: Int,
        data: Intent?,
    ): ZonePickerResult = parsePickerResult(resultCode, data?.getStringExtra(ZonePicker.EXTRA_ZONE_ID))
}
