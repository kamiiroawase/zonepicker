package io.github.kamiiroawase.zonepicker

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Accent-derived colors for runtime accent inputs; pure WCAG color math over opaque ARGB
 *  ints (alpha ignored), unit-testable with no Android framework underneath. */
internal object AccentColors {
    /** Black or white — whichever reads over [accentColor]. For chrome drawn directly on the
     *  accent: the header title and back arrow. */
    fun onAccentColor(accentColor: Int): Int = if (luminance(accentColor) > 0.5) Color.BLACK else Color.WHITE

    /** [accentColor] itself while it contrasts enough with [surfaceColor], [fallbackColor]
     *  otherwise. For selection marks on a surface (checkmark, cursor) — a white accent on a
     *  white surface stays visible through the fallback in either day or night mode. */
    fun markColor(
        accentColor: Int,
        surfaceColor: Int,
        fallbackColor: Int,
    ): Int = if (contrast(accentColor, surfaceColor) >= MIN_MARK_CONTRAST) accentColor else fallbackColor

    /** WCAG sRGB relative luminance, the same quantity androidx ColorUtils computes. */
    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val linear = value / 255.0

            return if (linear <= 0.03928) linear / 12.92 else ((linear + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * channel(color shr 16 and 0xFF) +
            0.7152 * channel(color shr 8 and 0xFF) +
            0.0722 * channel(color and 0xFF)
    }

    /** WCAG contrast ratio: 1 for identical colors, up to 21 for black on white. */
    private fun contrast(
        first: Int,
        second: Int,
    ): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)

        return (max(firstLuminance, secondLuminance) + 0.05) / (min(firstLuminance, secondLuminance) + 0.05)
    }

    /** WCAG's graphics threshold: the minimum contrast a mark needs to stay readable. */
    private const val MIN_MARK_CONTRAST = 3.0
}
