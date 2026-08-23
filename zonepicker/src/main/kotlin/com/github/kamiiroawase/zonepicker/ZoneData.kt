package com.github.kamiiroawase.zonepicker

import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Picker data logic: free of Android context dependencies, unit-testable. */
internal object ZoneData {
    data class Zone(
        val zoneId: String,
        val displayName: String,
        val offsetSeconds: Int,
    )

    /** GMT offset label, e.g. GMT+08:00. */
    fun offsetLabel(seconds: Int): String {
        val sign = if (seconds < 0) "-" else "+"

        val absSeconds = abs(seconds)

        return "GMT$sign%02d:%02d".format(absSeconds / 3600, absSeconds % 3600 / 60)
    }

    /** Circular offset key starting at GMT+08: +08 first, then +09…+14, wrapping back to -12…+07. */
    fun wrapOffset(offsetSeconds: Int): Int = (offsetSeconds - 8 * 3600).mod(24 * 3600)

    /** All zones sorted by circular offset then display name; offsets snapshot at nowMillis (DST aware). */
    fun buildZones(
        nowMillis: Long,
        nameOverrides: Map<String, String> = emptyMap(),
        ids: Array<String> = TimeZone.getAvailableIDs(),
        nameLocale: Locale = Locale.SIMPLIFIED_CHINESE,
    ): List<Zone> =
        ids
            .filter { it !in HIDDEN_ZONE_IDS && !isLegacyZoneId(it) }
            .map { id ->
                val timeZone = TimeZone.getTimeZone(id)

                Zone(
                    zoneId = id,
                    displayName =
                        nameOverrides[id]
                            ?: timeZone.getDisplayName(false, TimeZone.LONG, nameLocale),
                    offsetSeconds = timeZone.getOffset(nowMillis) / 1000,
                )
            }.sortedWith(zoneOrder)

    /** Chinese zones have a fixed order (Shanghai, Hong Kong, Macau, Taipei); others follow. */
    private fun cnOrder(zoneId: String): Int = CN_ZONE_ORDER[zoneId] ?: CN_ZONE_ORDER.size

    /** Shared sort order: circular offset from GMT+08, then Chinese zone order, then display name. */
    private val zoneOrder =
        compareBy<Zone>(
            { wrapOffset(it.offsetSeconds) },
            { cnOrder(it.zoneId) },
            { it.displayName },
        )

    /** Redundant UTC/GMT aliases hidden from the list; bare UTC/GMT and Etc/GMT±N stay. */
    private val HIDDEN_ZONE_IDS =
        setOf(
            "GMT0",
            "GMT+0",
            "GMT-0",
            "Etc/GMT0",
            "Etc/GMT+0",
            "Etc/GMT-0",
            "Greenwich",
            "Etc/Greenwich",
            "UCT",
            "Etc/UCT",
            "Universal",
            "Etc/Universal",
            "Zulu",
            "Etc/Zulu",
            "Etc/UTC",
        )

    /** Legacy three-letter zone IDs (EST, CET, ROC…) and their DST composites (EST5EDT…). */
    private fun isLegacyZoneId(zoneId: String): Boolean = zoneId !in SHORT_ID_KEEP && LEGACY_ZONE_ID_REGEX.matches(zoneId)

    private val SHORT_ID_KEEP = setOf("UTC", "GMT")

    private val LEGACY_ZONE_ID_REGEX = Regex("^[A-Z]{3}(?:[0-9]+[A-Z]{3})?$")

    /** Default list: popular zones plus one representative for each uncovered offset. */
    fun defaultZones(zones: List<Zone>): List<Zone> {
        val preferred = zones.filter { it.zoneId in PREFERRED_ZONE_IDS }

        val covered = preferred.map { it.offsetSeconds }.toSet()

        val fillers =
            zones
                .groupBy { it.offsetSeconds }
                .filterKeys { it !in covered }
                .mapNotNull { (_, group) ->
                    group.firstOrNull { !it.zoneId.startsWith("Etc/") } ?: group.firstOrNull()
                }

        return (preferred + fillers).sortedWith(zoneOrder)
    }

    /** Search matching against display name, zone ID and offset label; case-insensitive. */
    fun matches(
        zone: Zone,
        query: String,
    ): Boolean {
        if (query.isEmpty()) return true

        return matchesCore(zone, query.lowercase(), normalizeOffsetText(query))
    }

    /** Bulk matching for a whole list; the query is normalized once instead of once per zone. */
    fun filter(
        zones: List<Zone>,
        query: String,
        extraZoneIds: Set<String> = emptySet(),
    ): List<Zone> {
        if (query.isEmpty()) return zones

        val lower = query.lowercase()
        val offsetQuery = normalizeOffsetText(query)

        return zones.filter { matchesCore(it, lower, offsetQuery) || it.zoneId in extraZoneIds }
    }

    private fun matchesCore(
        zone: Zone,
        lowerQuery: String,
        offsetQuery: String,
    ): Boolean =
        zone.zoneId.lowercase().contains(lowerQuery) ||
            zone.displayName.lowercase().contains(lowerQuery) ||
            normalizeOffsetText(offsetLabel(zone.offsetSeconds)).contains(offsetQuery)

    /** Offset search form without spaces/colons and leading zeros, so "gmt+8" matches "GMT+08:00". */
    private fun normalizeOffsetText(text: String): String =
        text
            .lowercase()
            .replace(SEPARATOR_REGEX, "")
            .replace(DIGIT_RUN_REGEX) { digits -> digits.value.dropWhile { it == '0' }.ifEmpty { "0" } }

    private val SEPARATOR_REGEX = Regex("[\\s:]")

    private val DIGIT_RUN_REGEX = Regex("\\d+")

    /** Returns all zones of a country/region when the query matches its Chinese or English name. */
    fun countryZoneIds(query: String): Set<String> {
        val trimmed = query.trim()

        if (trimmed.isEmpty()) return emptySet()

        return COUNTRY_ZONES
            .filter { country -> country.keywords.any { keywordMatches(trimmed, it) } }
            .flatMap { it.zoneIds }
            .toSet()
    }

    /**
     * CJK keywords have no word boundaries, so plain substring matching either way is right.
     * Latin keywords match whole-word prefixes instead, so "us" hits "us" but not "austria".
     */
    private fun keywordMatches(
        query: String,
        keyword: String,
    ): Boolean {
        val keywordLower = keyword.lowercase()

        return if (keywordLower.any { it in 'a'..'z' }) {
            val keywordTokens = keywordLower.split(' ').filter { it.isNotEmpty() }
            val queryTokens = query.lowercase().split(' ').filter { it.isNotEmpty() }

            queryTokens.isNotEmpty() && queryTokens.all { q -> keywordTokens.any { it.startsWith(q) } }
        } else {
            keywordLower.contains(query) || query.contains(keywordLower)
        }
    }

    private data class CountryZones(
        val keywords: List<String>,
        val zoneIds: List<String>,
    )

    /** Fixed order of Chinese zones within the same offset group. */
    private val CN_ZONE_ORDER =
        mapOf(
            "Asia/Shanghai" to 0,
            "Asia/Hong_Kong" to 1,
            "Asia/Macau" to 2,
            "Asia/Taipei" to 3,
        )

    /** Popular country/region keywords (Chinese & English) → their zone IDs, for country search. */
    private val COUNTRY_ZONES =
        listOf(
            CountryZones(
                listOf("中国", "香港", "澳门", "台湾", "新加坡", "china", "hong kong", "hongkong", "macau", "macao", "taiwan", "singapore"),
                listOf("Asia/Shanghai", "Asia/Urumqi", "Asia/Hong_Kong", "Asia/Macau", "Asia/Taipei", "Asia/Singapore"),
            ),
            CountryZones(listOf("日本", "japan"), listOf("Asia/Tokyo")),
            CountryZones(listOf("韩国", "南韩", "korea", "south korea"), listOf("Asia/Seoul")),
            CountryZones(listOf("朝鲜", "北韩", "north korea"), listOf("Asia/Pyongyang")),
            CountryZones(
                listOf("美国", "美利坚", "usa", "us", "united states", "america"),
                listOf(
                    "America/New_York",
                    "America/Chicago",
                    "America/Denver",
                    "America/Los_Angeles",
                    "America/Phoenix",
                    "America/Anchorage",
                    "America/Honolulu",
                ),
            ),
            CountryZones(
                listOf("加拿大", "canada"),
                listOf("America/Toronto", "America/Vancouver", "America/Edmonton", "America/Winnipeg", "America/Halifax", "America/St_Johns"),
            ),
            CountryZones(
                listOf("墨西哥", "mexico"),
                listOf("America/Mexico_City", "America/Cancun", "America/Tijuana", "America/Monterrey", "America/Chihuahua", "America/Mazatlan"),
            ),
            CountryZones(
                listOf("英国", "英格兰", "联合王国", "uk", "united kingdom", "britain", "england"),
                listOf("Europe/London"),
            ),
            CountryZones(listOf("法国", "france"), listOf("Europe/Paris")),
            CountryZones(listOf("德国", "germany"), listOf("Europe/Berlin", "Europe/Busingen")),
            CountryZones(listOf("意大利", "italy"), listOf("Europe/Rome")),
            CountryZones(listOf("西班牙", "spain"), listOf("Europe/Madrid", "Africa/Ceuta", "Atlantic/Canary")),
            CountryZones(listOf("葡萄牙", "portugal"), listOf("Europe/Lisbon", "Atlantic/Madeira", "Atlantic/Azores")),
            CountryZones(listOf("荷兰", "netherlands"), listOf("Europe/Amsterdam")),
            CountryZones(listOf("比利时", "belgium"), listOf("Europe/Brussels")),
            CountryZones(listOf("瑞士", "switzerland"), listOf("Europe/Zurich")),
            CountryZones(listOf("奥地利", "austria"), listOf("Europe/Vienna")),
            CountryZones(listOf("爱尔兰", "ireland"), listOf("Europe/Dublin")),
            CountryZones(listOf("瑞典", "sweden"), listOf("Europe/Stockholm")),
            CountryZones(listOf("挪威", "norway"), listOf("Europe/Oslo")),
            CountryZones(listOf("丹麦", "denmark"), listOf("Europe/Copenhagen")),
            CountryZones(listOf("芬兰", "finland"), listOf("Europe/Helsinki")),
            CountryZones(listOf("波兰", "poland"), listOf("Europe/Warsaw")),
            CountryZones(listOf("希腊", "greece"), listOf("Europe/Athens")),
            CountryZones(listOf("土耳其", "turkey"), listOf("Europe/Istanbul")),
            CountryZones(
                listOf("俄罗斯", "俄国", "russia"),
                listOf(
                    "Europe/Moscow",
                    "Europe/Kaliningrad",
                    "Asia/Yekaterinburg",
                    "Asia/Omsk",
                    "Asia/Novosibirsk",
                    "Asia/Krasnoyarsk",
                    "Asia/Irkutsk",
                    "Asia/Yakutsk",
                    "Asia/Vladivostok",
                    "Asia/Magadan",
                    "Asia/Kamchatka",
                    "Asia/Anadyr",
                ),
            ),
            // Kyiv is canonical since tzdata 2022b; keep the Kiev alias for older tzdata devices.
            CountryZones(listOf("乌克兰", "ukraine"), listOf("Europe/Kyiv", "Europe/Kiev")),
            CountryZones(listOf("印度", "india"), listOf("Asia/Kolkata")),
            CountryZones(listOf("泰国", "thailand"), listOf("Asia/Bangkok")),
            CountryZones(listOf("越南", "vietnam"), listOf("Asia/Ho_Chi_Minh")),
            CountryZones(listOf("马来西亚", "malaysia"), listOf("Asia/Kuala_Lumpur", "Asia/Kuching")),
            CountryZones(listOf("印度尼西亚", "印尼", "indonesia"), listOf("Asia/Jakarta", "Asia/Pontianak", "Asia/Makassar", "Asia/Jayapura")),
            CountryZones(listOf("菲律宾", "philippines"), listOf("Asia/Manila")),
            CountryZones(listOf("缅甸", "myanmar"), listOf("Asia/Yangon")),
            CountryZones(listOf("尼泊尔", "nepal"), listOf("Asia/Kathmandu")),
            CountryZones(listOf("巴基斯坦", "pakistan"), listOf("Asia/Karachi")),
            CountryZones(listOf("孟加拉国", "孟加拉", "bangladesh"), listOf("Asia/Dhaka")),
            CountryZones(listOf("阿联酋", "迪拜", "united arab emirates", "uae", "dubai"), listOf("Asia/Dubai")),
            CountryZones(listOf("沙特阿拉伯", "沙特", "saudi arabia"), listOf("Asia/Riyadh")),
            CountryZones(listOf("卡塔尔", "qatar"), listOf("Asia/Qatar")),
            CountryZones(listOf("以色列", "israel"), listOf("Asia/Jerusalem")),
            CountryZones(listOf("埃及", "egypt"), listOf("Africa/Cairo")),
            CountryZones(listOf("南非", "south africa"), listOf("Africa/Johannesburg")),
            CountryZones(listOf("尼日利亚", "nigeria"), listOf("Africa/Lagos")),
            CountryZones(listOf("肯尼亚", "kenya"), listOf("Africa/Nairobi")),
            CountryZones(
                listOf("巴西", "brazil"),
                listOf("America/Sao_Paulo", "America/Manaus", "America/Fortaleza", "America/Cuiaba", "America/Rio_Branco", "America/Noronha"),
            ),
            CountryZones(listOf("阿根廷", "argentina"), listOf("America/Argentina/Buenos_Aires")),
            CountryZones(listOf("智利", "chile"), listOf("America/Santiago", "America/Punta_Arenas")),
            CountryZones(
                listOf("澳大利亚", "澳洲", "australia"),
                listOf(
                    "Australia/Sydney",
                    "Australia/Melbourne",
                    "Australia/Brisbane",
                    "Australia/Adelaide",
                    "Australia/Darwin",
                    "Australia/Perth",
                    "Australia/Hobart",
                ),
            ),
            CountryZones(listOf("新西兰", "new zealand"), listOf("Pacific/Auckland", "Pacific/Chatham")),
        )

    /** Popular zones shown by default; search is not limited to them. */
    private val PREFERRED_ZONE_IDS =
        setOf(
            "Pacific/Honolulu",
            "America/Anchorage",
            "America/Los_Angeles",
            "America/Denver",
            "America/Chicago",
            "America/Mexico_City",
            "America/New_York",
            "America/Toronto",
            "America/Sao_Paulo",
            "Europe/London",
            "Europe/Paris",
            "Europe/Berlin",
            "Europe/Istanbul",
            "Europe/Moscow",
            "Africa/Cairo",
            "Asia/Dubai",
            "Asia/Kolkata",
            "Asia/Bangkok",
            "Asia/Shanghai",
            "Asia/Hong_Kong",
            "Asia/Macau",
            "Asia/Taipei",
            "Asia/Singapore",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Australia/Sydney",
            "Pacific/Auckland",
        )
}
