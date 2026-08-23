package com.github.kamiiroawase.zonepicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ZoneDataTest {
    @Test
    fun `wrapOffset puts GMT+08 first`() {
        assertEquals(0, ZoneData.wrapOffset(8 * 3600))
        assertEquals(3600, ZoneData.wrapOffset(9 * 3600))
        assertEquals(23 * 3600, ZoneData.wrapOffset(7 * 3600))
        // UTC-12 and UTC+12 show the same wall-clock time, so their circular keys are equal
        assertEquals(ZoneData.wrapOffset(12 * 3600), ZoneData.wrapOffset(-12 * 3600))
    }

    @Test
    fun `offsetLabel formats sign and padding`() {
        assertEquals("GMT+08:00", ZoneData.offsetLabel(8 * 3600))
        assertEquals("GMT-05:30", ZoneData.offsetLabel(-(5 * 3600 + 1800)))
        assertEquals("GMT+00:00", ZoneData.offsetLabel(0))
    }

    @Test
    fun `country search covers chinese region by chinese and english name`() {
        val byChinese = ZoneData.countryZoneIds("中国")
        val byEnglish = ZoneData.countryZoneIds("china")

        for (ids in listOf(byChinese, byEnglish)) {
            assertTrue(ids.contains("Asia/Shanghai"))
            assertTrue(ids.contains("Asia/Singapore"))
            assertTrue(ids.contains("Asia/Taipei"))
            assertTrue(ids.contains("Asia/Hong_Kong"))
        }
    }

    @Test
    fun `country search matches other countries`() {
        assertTrue(ZoneData.countryZoneIds("usa").contains("America/New_York"))
        assertTrue(ZoneData.countryZoneIds("美国").contains("America/Los_Angeles"))
        assertTrue(ZoneData.countryZoneIds("russia").contains("Europe/Moscow"))
    }

    @Test
    fun `country search avoids latin substring false positives`() {
        val us = ZoneData.countryZoneIds("us")

        assertTrue(us.contains("America/New_York"))
        assertFalse(us.contains("Europe/Vienna"))
        assertFalse(us.contains("Australia/Sydney"))
        assertFalse(us.contains("Europe/Moscow"))

        val ukraine = ZoneData.countryZoneIds("ukraine")

        assertTrue(ukraine.contains("Europe/Kyiv"))
        assertFalse(ukraine.contains("Europe/London"))
    }

    @Test
    fun `country search matches latin keywords by word prefix`() {
        val united = ZoneData.countryZoneIds("united")

        assertTrue(united.containsAll(listOf("America/New_York", "Europe/London", "Asia/Dubai")))

        val south = ZoneData.countryZoneIds("south")

        assertTrue(south.contains("Asia/Seoul"))
        assertFalse(south.contains("Asia/Pyongyang"))
    }

    @Test
    fun `country search empty for unknown keyword`() {
        assertTrue(ZoneData.countryZoneIds("zzz不存在zzz").isEmpty())
    }

    @Test
    fun `country search returns kyiv with legacy kiev alias`() {
        val ids = ZoneData.countryZoneIds("ukraine")

        assertTrue(ids.contains("Europe/Kyiv"))
        // Older tzdata only knows the pre-2022 alias
        assertTrue(ids.contains("Europe/Kiev"))
    }

    @Test
    fun `matches by id display name and offset label`() {
        val zone = ZoneData.Zone("Asia/Shanghai", "中国标准时间", 8 * 3600)

        assertTrue(ZoneData.matches(zone, "shang"))
        assertTrue(ZoneData.matches(zone, "标准"))
        assertTrue(ZoneData.matches(zone, "gmt+08"))
        assertFalse(ZoneData.matches(zone, "tokyo"))
    }

    @Test
    fun `matches latin display name case-insensitively`() {
        val zone = ZoneData.Zone("Asia/Shanghai", "China Standard Time", 8 * 3600)

        assertTrue(ZoneData.matches(zone, "china"))
        assertTrue(ZoneData.matches(zone, "CHINA"))
        assertTrue(ZoneData.matches(zone, "Standard"))
        assertFalse(ZoneData.matches(zone, "tokyo"))
    }

    @Test
    fun `matches offset queries regardless of zero padding`() {
        val shanghai = ZoneData.Zone("Asia/Shanghai", "中国标准时间", 8 * 3600)
        val kolkata = ZoneData.Zone("Asia/Kolkata", "印度标准时间", 5 * 3600 + 1800)

        assertTrue(ZoneData.matches(shanghai, "gmt+8"))
        assertTrue(ZoneData.matches(shanghai, "gmt+08"))
        assertTrue(ZoneData.matches(shanghai, "gmt+8:00"))
        assertTrue(ZoneData.matches(shanghai, "GMT +8"))
        assertTrue(ZoneData.matches(kolkata, "gmt+5:30"))
        assertTrue(ZoneData.matches(kolkata, "gmt+530"))
        assertFalse(ZoneData.matches(shanghai, "gmt+9"))
        assertFalse(ZoneData.matches(kolkata, "gmt+5:00"))
    }

    @Test
    fun `filter matches zones and folds in extra country ids`() {
        val zones =
            listOf(
                ZoneData.Zone("Asia/Shanghai", "中国标准时间", 8 * 3600),
                ZoneData.Zone("Asia/Tokyo", "日本标准时间", 9 * 3600),
                ZoneData.Zone("Europe/London", "格林尼治时间", 0),
            )

        assertEquals(listOf("Asia/Shanghai"), ZoneData.filter(zones, "shanghai").map { it.zoneId })
        assertEquals(
            listOf("Asia/Shanghai", "Asia/Tokyo"),
            ZoneData.filter(zones, "标准").map { it.zoneId },
        )

        // Zone hit only via the extra country IDs still shows up
        assertEquals(
            listOf("Asia/Tokyo"),
            ZoneData.filter(zones, "zzz不存在zzz", setOf("Asia/Tokyo")).map { it.zoneId },
        )

        // Empty query returns everything unchanged
        assertEquals(zones, ZoneData.filter(zones, ""))
    }

    @Test
    fun `buildZones orders by wrap offset`() {
        val zones = ZoneData.buildZones(0L, ZONE_NAME_OVERRIDES)

        val keys = zones.map { ZoneData.wrapOffset(it.offsetSeconds) }

        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun `buildZones applies display name overrides`() {
        val zones = ZoneData.buildZones(0L, ZONE_NAME_OVERRIDES)

        assertEquals("中国台北时间", zones.first { it.zoneId == "Asia/Taipei" }.displayName)
        assertEquals("中国香港时间", zones.first { it.zoneId == "Asia/Hong_Kong" }.displayName)
        assertEquals("中国澳门时间", zones.first { it.zoneId == "Asia/Macau" }.displayName)
    }

    @Test
    fun `buildZones renders display names in chinese regardless of system locale`() {
        val originalLocale = Locale.getDefault()

        Locale.setDefault(Locale.ENGLISH)

        try {
            val zones = ZoneData.buildZones(0L, ids = arrayOf("Asia/Shanghai", "Asia/Tokyo"))

            assertEquals("中国标准时间", zones.first { it.zoneId == "Asia/Shanghai" }.displayName)
            assertEquals("日本标准时间", zones.first { it.zoneId == "Asia/Tokyo" }.displayName)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `buildZones hides legacy and redundant zone ids`() {
        val zones =
            ZoneData.buildZones(
                0L,
                ids =
                    arrayOf(
                        "Asia/Tokyo",
                        "Europe/Kyiv",
                        "EST",
                        "CET",
                        "ROC",
                        "EST5EDT",
                        "GMT0",
                        "Greenwich",
                        "Zulu",
                        "Etc/UTC",
                        "Etc/GMT+0",
                        "UTC",
                        "GMT",
                        "Etc/GMT+12",
                    ),
            )

        val shown = zones.map { it.zoneId }.toSet()

        assertTrue(shown.containsAll(listOf("Asia/Tokyo", "Europe/Kyiv", "UTC", "GMT", "Etc/GMT+12")))

        val hidden = setOf("EST", "CET", "ROC", "EST5EDT", "GMT0", "Greenwich", "Zulu", "Etc/UTC", "Etc/GMT+0")

        assertTrue(shown.none { it in hidden })
        assertEquals(5, zones.size)
    }

    @Test
    fun `defaultZones covers every offset and keeps preferred`() {
        val zones = ZoneData.buildZones(0L, ZONE_NAME_OVERRIDES)

        val allOffsets = zones.map { it.offsetSeconds }.toSet()

        val default = ZoneData.defaultZones(zones)

        assertEquals(allOffsets, default.map { it.offsetSeconds }.toSet())
        assertTrue(default.size < zones.size)
        assertTrue(default.any { it.zoneId == "Asia/Shanghai" })
        assertTrue(default.any { it.zoneId == "Asia/Macau" })
    }

    private companion object {
        val ZONE_NAME_OVERRIDES =
            mapOf(
                "Asia/Taipei" to "中国台北时间",
                "Asia/Hong_Kong" to "中国香港时间",
                "Asia/Macau" to "中国澳门时间",
            )
    }
}
