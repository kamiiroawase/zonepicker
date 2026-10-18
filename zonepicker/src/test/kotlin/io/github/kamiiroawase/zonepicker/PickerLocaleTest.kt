package io.github.kamiiroawase.zonepicker

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PickerLocaleTest {
    @Test
    fun `rtl locales mirror`() {
        for (language in listOf("ar", "fa", "he", "iw", "ur")) {
            assertEquals("$language should mirror", LayoutDirection.Rtl, PickerLocale.layoutDirection(Locale(language)))
        }
    }

    @Test
    fun `ltr locales stay ltr`() {
        for (locale in listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.JAPAN, Locale.ROOT)) {
            assertEquals("$locale should not mirror", LayoutDirection.Ltr, PickerLocale.layoutDirection(locale))
        }
    }
}
