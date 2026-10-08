package io.github.kamiiroawase.zonepicker

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentColorsTest {
    @Test
    fun `onAccentColor picks the readable black or white side`() {
        assertEquals(Color.BLACK, AccentColors.onAccentColor(Color.WHITE))
        assertEquals(Color.WHITE, AccentColors.onAccentColor(Color.BLACK))

        // The default orange accent keeps the white chrome it ships with
        assertEquals(Color.WHITE, AccentColors.onAccentColor(0xFFF05E1C.toInt()))

        // A light custom accent flips the header to black text and icons
        assertEquals(Color.BLACK, AccentColors.onAccentColor(0xFFFFEB3B.toInt()))
    }

    @Test
    fun `markColor keeps the accent while it contrasts with the surface`() {
        val lightSurface = Color.WHITE
        val darkSurface = 0xFF1E1E1E.toInt()
        val lightFallback = 0xFF333333.toInt()
        val darkFallback = 0xFFE0E0E0.toInt()
        val accent = 0xFFF05E1C.toInt()

        assertEquals(accent, AccentColors.markColor(accent, lightSurface, lightFallback))
        assertEquals(accent, AccentColors.markColor(accent, darkSurface, darkFallback))

        // A light accent on the light surface falls back; the same accent stays on the dark one
        val lightAccent = 0xFFFFEB3B.toInt()

        assertEquals(lightFallback, AccentColors.markColor(lightAccent, lightSurface, lightFallback))
        assertEquals(lightAccent, AccentColors.markColor(lightAccent, darkSurface, darkFallback))

        // Even a white accent survives on the dark surface
        assertEquals(Color.WHITE, AccentColors.markColor(Color.WHITE, darkSurface, darkFallback))
    }
}
