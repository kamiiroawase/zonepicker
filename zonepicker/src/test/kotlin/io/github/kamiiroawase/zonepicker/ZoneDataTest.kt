package io.github.kamiiroawase.zonepicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

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
    fun `country search accepts hyphen and underscore spellings`() {
        assertTrue(ZoneData.countryZoneIds("united-states").contains("America/New_York"))
        assertTrue(ZoneData.countryZoneIds("hong-kong").contains("Asia/Hong_Kong"))
        assertTrue(ZoneData.countryZoneIds("south_korea").contains("Asia/Seoul"))
        assertTrue(ZoneData.countryZoneIds("new-zealand").contains("Pacific/Auckland"))
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
    fun `positive hour queries match exactly that hour`() {
        val paris = ZoneData.Zone("Europe/Paris", "中欧时间", 3600)
        val auckland = ZoneData.Zone("Pacific/Auckland", "新西兰标准时间", 13 * 3600)
        val kiritimati = ZoneData.Zone("Pacific/Kiritimati", "莱恩群岛时间", 14 * 3600)

        assertTrue(ZoneData.matches(paris, "gmt+1"))
        assertFalse(ZoneData.matches(auckland, "gmt+1"))
        assertFalse(ZoneData.matches(kiritimati, "gmt+1"))
        assertTrue(ZoneData.matches(auckland, "gmt+13"))
        assertTrue(ZoneData.matches(kiritimati, "gmt+14"))
        assertFalse(ZoneData.matches(paris, "gmt+13"))
    }

    @Test
    fun `negative hour queries match exactly that hour`() {
        val azores = ZoneData.Zone("Atlantic/Azores", "亚速尔群岛时间", -3600)
        val honolulu = ZoneData.Zone("Pacific/Honolulu", "夏威夷标准时间", -10 * 3600)
        val midway = ZoneData.Zone("Pacific/Midway", "萨摩亚时间", -11 * 3600)

        assertTrue(ZoneData.matches(azores, "gmt-1"))
        assertFalse(ZoneData.matches(honolulu, "gmt-1"))
        assertFalse(ZoneData.matches(midway, "gmt-1"))
        assertTrue(ZoneData.matches(honolulu, "gmt-10"))
        assertTrue(ZoneData.matches(midway, "gmt-11"))
    }

    @Test
    fun `malformed minute fragments keep their substring hits`() {
        val kolkata = ZoneData.Zone("Asia/Kolkata", "印度标准时间", 5 * 3600 + 1800)

        // Typed mid-way to "5:30": not parseable, falls back to the old label substring match
        assertTrue(ZoneData.matches(kolkata, "gmt+5:3"))
    }

    @Test
    fun `matches zone ids across separator spellings`() {
        val newYork = ZoneData.Zone("America/New_York", "美国东部时间", -5 * 3600)
        val hoChiMinh = ZoneData.Zone("Asia/Ho_Chi_Minh", "印度支那时间", 7 * 3600)
        val portAuPrince = ZoneData.Zone("America/Port-au-Prince", "美国东部时间", -5 * 3600)

        assertTrue(ZoneData.matches(newYork, "new york"))
        assertTrue(ZoneData.matches(newYork, "New York"))
        assertTrue(ZoneData.matches(newYork, "newyork"))
        assertTrue(ZoneData.matches(newYork, "new_york"))
        assertFalse(ZoneData.matches(newYork, "york new"))

        assertTrue(ZoneData.matches(hoChiMinh, "ho chi minh"))
        assertTrue(ZoneData.matches(portAuPrince, "port au prince"))
    }

    @Test
    fun `filter finds zones by spaced city names`() {
        val zones =
            listOf(
                ZoneData.Zone("America/New_York", "美国东部时间", -5 * 3600),
                ZoneData.Zone("America/Chicago", "美国中部时间", -6 * 3600),
            )

        assertEquals(listOf("America/New_York"), ZoneData.filter(zones, "new york").map { it.zoneId })
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
    fun `buildZones breaks display name ties by zone id`() {
        val zones =
            ZoneData.buildZones(
                0L,
                ids = arrayOf("Africa/Tunis", "Africa/Algiers"),
            )

        // Precondition: the pair shares a Chinese display name, so only the zone ID key can order it
        assertEquals(zones[0].displayName, zones[1].displayName)

        assertEquals(
            listOf("Africa/Algiers", "Africa/Tunis"),
            zones.map { it.zoneId },
        )
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
    fun `buildZones hides backward aliases when the canonical zone exists`() {
        val zones =
            ZoneData.buildZones(
                0L,
                ids =
                    arrayOf(
                        "Asia/Tokyo",
                        "Japan",
                        "America/Los_Angeles",
                        "US/Pacific",
                        "US/Pacific-New",
                        "SystemV/YST9YDT",
                        "Asia/Singapore",
                        "Singapore",
                        "Europe/Kyiv",
                        "Europe/Kiev",
                    ),
            )

        val shown = zones.map { it.zoneId }.toSet()

        assertEquals(setOf("Asia/Tokyo", "America/Los_Angeles", "Asia/Singapore", "Europe/Kyiv"), shown)
    }

    @Test
    fun `buildZones keeps a renamed zone when only the old ID exists (old tzdata)`() {
        val zones =
            ZoneData.buildZones(
                0L,
                ids = arrayOf("Europe/Kiev", "Asia/Tokyo", "Japan"),
            )

        val shown = zones.map { it.zoneId }.toSet()

        // No Europe/Kyiv on this device → the pre-rename ID stays; Japan still hides behind Tokyo
        assertEquals(setOf("Europe/Kiev", "Asia/Tokyo"), shown)
    }

    @Test
    fun `buildZones default list contains no backward or SystemV aliases`() {
        val ids = TimeZone.getAvailableIDs()
        val availableIds = ids.toHashSet()
        val shown = ZoneData.buildZones(0L, ids = ids).map { it.zoneId }

        // An alias hides only while its canonical zone exists on this tzdata — assert exactly
        // that rule, so a future tzdb rename cannot false-fail this test on exotic JVMs
        listOf(
            "Japan" to "Asia/Tokyo",
            "Hongkong" to "Asia/Hong_Kong",
            "Singapore" to "Asia/Singapore",
            "Turkey" to "Europe/Istanbul",
            "W-SU" to "Europe/Moscow",
            "US/Pacific" to "America/Los_Angeles",
            "US/Eastern" to "America/New_York",
            "Canada/Pacific" to "America/Vancouver",
            "Mexico/BajaNorte" to "America/Tijuana",
            "Brazil/East" to "America/Sao_Paulo",
            "Chile/Continental" to "America/Santiago",
            "Australia/ACT" to "Australia/Sydney",
            "America/Buenos_Aires" to "America/Argentina/Buenos_Aires",
            "Asia/Katmandu" to "Asia/Kathmandu",
            "Asia/Saigon" to "Asia/Ho_Chi_Minh",
            "Europe/Kiev" to "Europe/Kyiv",
        ).forEach { (alias, canonical) ->
            assertTrue(alias !in shown || canonical !in availableIds)
        }

        assertTrue(shown.none { it.startsWith("SystemV/") })

        if ("Asia/Tokyo" in availableIds) assertTrue(shown.contains("Asia/Tokyo"))
        if ("Europe/Kyiv" in availableIds) assertTrue(shown.contains("Europe/Kyiv"))
    }

    @Test
    fun `offset queries do not text-match etc zone ids with inverted sign`() {
        // Etc/GMT+8 is UTC-8: its ID text must not answer a "+8" search
        val etcGmtPlus8 = ZoneData.Zone("Etc/GMT+8", "GMT+08:00", -8 * 3600)
        val etcGmtMinus12 = ZoneData.Zone("Etc/GMT-12", "GMT-12:00", 12 * 3600)

        assertFalse(ZoneData.matches(etcGmtPlus8, "gmt+8"))
        assertTrue(ZoneData.matches(etcGmtPlus8, "gmt-8"))
        assertFalse(ZoneData.matches(etcGmtMinus12, "gmt-12"))
        assertTrue(ZoneData.matches(etcGmtMinus12, "gmt+12"))

        // Exact ID search keeps working — the query is not offset-shaped
        assertTrue(ZoneData.matches(etcGmtPlus8, "etc/gmt+8"))

        // Sign-less queries keep their text behavior: "gmt8" hits Etc/GMT-8's ID (UTC+8)
        assertTrue(ZoneData.matches(ZoneData.Zone("Etc/GMT-8", "GMT-08:00", 8 * 3600), "gmt8"))
    }

    @Test
    fun `offset queries accept the utc prefix`() {
        val shanghai = ZoneData.Zone("Asia/Shanghai", "中国标准时间", 8 * 3600)
        val utc = ZoneData.Zone("UTC", "协调世界时", 0)

        assertTrue(ZoneData.matches(shanghai, "utc+8"))
        assertFalse(ZoneData.matches(shanghai, "utc+9"))

        // Bare "utc" stays a text query and still finds the zone
        assertTrue(ZoneData.matches(utc, "utc"))
    }

    @Test
    fun `bare digit queries anchor the zero-stripped offset form`() {
        val shanghai = ZoneData.Zone("Asia/Shanghai", "中国标准时间", 8 * 3600)
        val kolkata = ZoneData.Zone("Asia/Kolkata", "印度标准时间", 5 * 3600 + 1800)

        // "80" hits the label form of GMT+08:00, "530" that of GMT+05:30
        assertTrue(ZoneData.matches(shanghai, "80"))
        assertFalse(ZoneData.matches(shanghai, "90"))
        assertTrue(ZoneData.matches(kolkata, "530"))
        assertFalse(ZoneData.matches(kolkata, "500"))
    }

    @Test
    fun `short latin prefixes no longer blur single-word countries`() {
        val uk = ZoneData.countryZoneIds("uk")

        assertTrue(uk.contains("Europe/London"))
        assertFalse(uk.contains("Europe/Kyiv"))

        // Whole tokens of any length and 3+-letter prefixes keep matching
        assertTrue(ZoneData.countryZoneIds("ukraine").contains("Europe/Kyiv"))
        assertTrue(ZoneData.countryZoneIds("ame").contains("America/New_York"))

        // 1-2 letter fragments without an exact keyword match find nothing
        assertTrue(ZoneData.countryZoneIds("au").isEmpty())
    }

    @Test
    fun `resolveDisplayZoneId maps aliases to the zone the list shows`() {
        assertEquals("America/Los_Angeles", ZoneData.resolveDisplayZoneId("US/Pacific"))
        assertEquals("Asia/Kolkata", ZoneData.resolveDisplayZoneId("Asia/Calcutta"))
        assertEquals("UTC", ZoneData.resolveDisplayZoneId("Etc/UTC"))
        assertEquals("GMT", ZoneData.resolveDisplayZoneId("Greenwich"))

        // Known zones and unknown IDs pass through unchanged
        assertEquals("Asia/Tokyo", ZoneData.resolveDisplayZoneId("Asia/Tokyo"))
        assertEquals("Mars/Olympus", ZoneData.resolveDisplayZoneId("Mars/Olympus"))
    }

    @Test
    fun `resolveDisplayZoneId falls back to the pre-rename id on old tzdata`() {
        // JVM tzdata knows the canonical Kyiv
        assertEquals("Europe/Kyiv", ZoneData.resolveDisplayZoneId("Europe/Kyiv"))

        // A device still on pre-2022 tzdata shows the pre-rename ID for both directions
        val oldTzdata = arrayOf("Europe/Kiev", "Asia/Tokyo")

        assertEquals("Europe/Kiev", ZoneData.resolveDisplayZoneId("Europe/Kyiv", ids = oldTzdata))
        assertEquals("Europe/Kiev", ZoneData.resolveDisplayZoneId("Europe/Kiev", ids = oldTzdata))
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
