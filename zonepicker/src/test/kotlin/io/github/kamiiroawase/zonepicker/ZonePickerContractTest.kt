package io.github.kamiiroawase.zonepicker

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

class ZonePickerContractTest {
    @Test
    fun `ok result with a zone id selects it`() {
        assertEquals(ZonePickerResult.Selected("Asia/Shanghai"), parsePickerResult(Activity.RESULT_OK, "Asia/Shanghai"))
    }

    @Test
    fun `ok result without a zone id follows the system`() {
        assertEquals(ZonePickerResult.FollowSystem, parsePickerResult(Activity.RESULT_OK, null))
    }

    @Test
    fun `canceled result stays canceled even with a stray zone id`() {
        assertEquals(ZonePickerResult.Canceled, parsePickerResult(Activity.RESULT_CANCELED, "Asia/Shanghai"))
        assertEquals(ZonePickerResult.Canceled, parsePickerResult(Activity.RESULT_CANCELED, null))
    }
}
