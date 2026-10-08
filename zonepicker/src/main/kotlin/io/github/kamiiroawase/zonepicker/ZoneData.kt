package io.github.kamiiroawase.zonepicker

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
    ): List<Zone> {
        val availableIds = ids.toHashSet()

        // SystemV/* IDs linger only in some runtimes' tzdata; the picker never wants them.
        return ids
            .filter { it !in HIDDEN_ZONE_IDS && !it.startsWith("SystemV/") }
            .filter { !isLegacyZoneId(it) && !isDuplicateAlias(it, availableIds) }
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
    }

    /** Chinese zones have a fixed order (Shanghai, Hong Kong, Macau, Taipei); others follow. */
    private fun cnOrder(zoneId: String): Int = CN_ZONE_ORDER[zoneId] ?: CN_ZONE_ORDER.size

    /** Shared sort order: circular offset from GMT+08, Chinese zone order, display name, then
     *  zone ID — the final key pins the order of same-name pairs across devices and tzdata versions. */
    private val zoneOrder =
        compareBy<Zone>(
            { wrapOffset(it.offsetSeconds) },
            { cnOrder(it.zoneId) },
            { it.displayName },
            { it.zoneId },
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

    /** tzdb `backward`-file aliases → their canonical zone IDs (IANA tzdb 2026e), plus the one
     *  link tzdb itself deleted (US/Pacific-New, gone since 2020a) — older devices' tzdata
     *  still carries it. Entries already covered by [HIDDEN_ZONE_IDS] or [LEGACY_ZONE_ID_REGEX]
     *  are omitted; bare UTC/GMT stay visible. */
    private val BACKWARD_ALIAS_TARGETS =
        mapOf(
            "Africa/Accra" to "Africa/Abidjan",
            "Africa/Addis_Ababa" to "Africa/Nairobi",
            "Africa/Asmara" to "Africa/Nairobi",
            "Africa/Asmera" to "Africa/Nairobi",
            "Africa/Bamako" to "Africa/Abidjan",
            "Africa/Bangui" to "Africa/Lagos",
            "Africa/Banjul" to "Africa/Abidjan",
            "Africa/Blantyre" to "Africa/Maputo",
            "Africa/Brazzaville" to "Africa/Lagos",
            "Africa/Bujumbura" to "Africa/Maputo",
            "Africa/Conakry" to "Africa/Abidjan",
            "Africa/Dakar" to "Africa/Abidjan",
            "Africa/Dar_es_Salaam" to "Africa/Nairobi",
            "Africa/Djibouti" to "Africa/Nairobi",
            "Africa/Douala" to "Africa/Lagos",
            "Africa/Freetown" to "Africa/Abidjan",
            "Africa/Gaborone" to "Africa/Maputo",
            "Africa/Harare" to "Africa/Maputo",
            "Africa/Kampala" to "Africa/Nairobi",
            "Africa/Kigali" to "Africa/Maputo",
            "Africa/Kinshasa" to "Africa/Lagos",
            "Africa/Libreville" to "Africa/Lagos",
            "Africa/Lome" to "Africa/Abidjan",
            "Africa/Luanda" to "Africa/Lagos",
            "Africa/Lubumbashi" to "Africa/Maputo",
            "Africa/Lusaka" to "Africa/Maputo",
            "Africa/Malabo" to "Africa/Lagos",
            "Africa/Maseru" to "Africa/Johannesburg",
            "Africa/Mbabane" to "Africa/Johannesburg",
            "Africa/Mogadishu" to "Africa/Nairobi",
            "Africa/Niamey" to "Africa/Lagos",
            "Africa/Nouakchott" to "Africa/Abidjan",
            "Africa/Ouagadougou" to "Africa/Abidjan",
            "Africa/Porto-Novo" to "Africa/Lagos",
            "Africa/Timbuktu" to "Africa/Abidjan",
            "America/Anguilla" to "America/Puerto_Rico",
            "America/Antigua" to "America/Puerto_Rico",
            "America/Argentina/ComodRivadavia" to "America/Argentina/Catamarca",
            "America/Aruba" to "America/Puerto_Rico",
            "America/Atikokan" to "America/Panama",
            "America/Atka" to "America/Adak",
            "America/Blanc-Sablon" to "America/Puerto_Rico",
            "America/Buenos_Aires" to "America/Argentina/Buenos_Aires",
            "America/Catamarca" to "America/Argentina/Catamarca",
            "America/Cayman" to "America/Panama",
            "America/Coral_Harbour" to "America/Panama",
            "America/Cordoba" to "America/Argentina/Cordoba",
            "America/Creston" to "America/Phoenix",
            "America/Curacao" to "America/Puerto_Rico",
            "America/Dominica" to "America/Puerto_Rico",
            "America/Ensenada" to "America/Tijuana",
            "America/Fort_Wayne" to "America/Indiana/Indianapolis",
            "America/Godthab" to "America/Nuuk",
            "America/Grenada" to "America/Puerto_Rico",
            "America/Guadeloupe" to "America/Puerto_Rico",
            "America/Indianapolis" to "America/Indiana/Indianapolis",
            "America/Jujuy" to "America/Argentina/Jujuy",
            "America/Knox_IN" to "America/Indiana/Knox",
            "America/Kralendijk" to "America/Puerto_Rico",
            "America/Louisville" to "America/Kentucky/Louisville",
            "America/Lower_Princes" to "America/Puerto_Rico",
            "America/Marigot" to "America/Puerto_Rico",
            "America/Mendoza" to "America/Argentina/Mendoza",
            "America/Montreal" to "America/Toronto",
            "America/Montserrat" to "America/Puerto_Rico",
            "America/Nassau" to "America/Toronto",
            "America/Nipigon" to "America/Toronto",
            "America/Pangnirtung" to "America/Iqaluit",
            "America/Port_of_Spain" to "America/Puerto_Rico",
            "America/Porto_Acre" to "America/Rio_Branco",
            "America/Rainy_River" to "America/Winnipeg",
            "America/Rosario" to "America/Argentina/Cordoba",
            "America/Santa_Isabel" to "America/Tijuana",
            "America/Shiprock" to "America/Denver",
            "America/St_Barthelemy" to "America/Puerto_Rico",
            "America/St_Kitts" to "America/Puerto_Rico",
            "America/St_Lucia" to "America/Puerto_Rico",
            "America/St_Thomas" to "America/Puerto_Rico",
            "America/St_Vincent" to "America/Puerto_Rico",
            "America/Thunder_Bay" to "America/Toronto",
            "America/Tortola" to "America/Puerto_Rico",
            "America/Virgin" to "America/Puerto_Rico",
            "America/Yellowknife" to "America/Edmonton",
            "Antarctica/DumontDUrville" to "Pacific/Port_Moresby",
            "Antarctica/McMurdo" to "Pacific/Auckland",
            "Antarctica/South_Pole" to "Pacific/Auckland",
            "Antarctica/Syowa" to "Asia/Riyadh",
            "Arctic/Longyearbyen" to "Europe/Berlin",
            "Asia/Aden" to "Asia/Riyadh",
            "Asia/Ashkhabad" to "Asia/Ashgabat",
            "Asia/Bahrain" to "Asia/Qatar",
            "Asia/Brunei" to "Asia/Kuching",
            "Asia/Calcutta" to "Asia/Kolkata",
            "Asia/Choibalsan" to "Asia/Ulaanbaatar",
            "Asia/Chongqing" to "Asia/Shanghai",
            "Asia/Chungking" to "Asia/Shanghai",
            "Asia/Dacca" to "Asia/Dhaka",
            "Asia/Harbin" to "Asia/Shanghai",
            "Asia/Istanbul" to "Europe/Istanbul",
            "Asia/Kashgar" to "Asia/Urumqi",
            "Asia/Katmandu" to "Asia/Kathmandu",
            "Asia/Kuala_Lumpur" to "Asia/Singapore",
            "Asia/Kuwait" to "Asia/Riyadh",
            "Asia/Macao" to "Asia/Macau",
            "Asia/Muscat" to "Asia/Dubai",
            "Asia/Phnom_Penh" to "Asia/Bangkok",
            "Asia/Rangoon" to "Asia/Yangon",
            "Asia/Saigon" to "Asia/Ho_Chi_Minh",
            "Asia/Tel_Aviv" to "Asia/Jerusalem",
            "Asia/Thimbu" to "Asia/Thimphu",
            "Asia/Ujung_Pandang" to "Asia/Makassar",
            "Asia/Ulan_Bator" to "Asia/Ulaanbaatar",
            "Asia/Vientiane" to "Asia/Bangkok",
            "Atlantic/Faeroe" to "Atlantic/Faroe",
            "Atlantic/Jan_Mayen" to "Europe/Berlin",
            "Atlantic/Reykjavik" to "Africa/Abidjan",
            "Atlantic/St_Helena" to "Africa/Abidjan",
            "Australia/ACT" to "Australia/Sydney",
            "Australia/Canberra" to "Australia/Sydney",
            "Australia/Currie" to "Australia/Hobart",
            "Australia/LHI" to "Australia/Lord_Howe",
            "Australia/NSW" to "Australia/Sydney",
            "Australia/North" to "Australia/Darwin",
            "Australia/Queensland" to "Australia/Brisbane",
            "Australia/South" to "Australia/Adelaide",
            "Australia/Tasmania" to "Australia/Hobart",
            "Australia/Victoria" to "Australia/Melbourne",
            "Australia/West" to "Australia/Perth",
            "Australia/Yancowinna" to "Australia/Broken_Hill",
            "Brazil/Acre" to "America/Rio_Branco",
            "Brazil/DeNoronha" to "America/Noronha",
            "Brazil/East" to "America/Sao_Paulo",
            "Brazil/West" to "America/Manaus",
            "Canada/Atlantic" to "America/Halifax",
            "Canada/Central" to "America/Winnipeg",
            "Canada/Eastern" to "America/Toronto",
            "Canada/Mountain" to "America/Edmonton",
            "Canada/Newfoundland" to "America/St_Johns",
            "Canada/Pacific" to "America/Vancouver",
            "Canada/Saskatchewan" to "America/Regina",
            "Canada/Yukon" to "America/Whitehorse",
            "Chile/Continental" to "America/Santiago",
            "Chile/EasterIsland" to "Pacific/Easter",
            "Cuba" to "America/Havana",
            "Egypt" to "Africa/Cairo",
            "Eire" to "Europe/Dublin",
            "Europe/Amsterdam" to "Europe/Brussels",
            "Europe/Belfast" to "Europe/London",
            "Europe/Bratislava" to "Europe/Prague",
            "Europe/Busingen" to "Europe/Zurich",
            "Europe/Copenhagen" to "Europe/Berlin",
            "Europe/Guernsey" to "Europe/London",
            "Europe/Isle_of_Man" to "Europe/London",
            "Europe/Jersey" to "Europe/London",
            "Europe/Kiev" to "Europe/Kyiv",
            "Europe/Ljubljana" to "Europe/Belgrade",
            "Europe/Luxembourg" to "Europe/Brussels",
            "Europe/Mariehamn" to "Europe/Helsinki",
            "Europe/Monaco" to "Europe/Paris",
            "Europe/Nicosia" to "Asia/Nicosia",
            "Europe/Oslo" to "Europe/Berlin",
            "Europe/Podgorica" to "Europe/Belgrade",
            "Europe/San_Marino" to "Europe/Rome",
            "Europe/Sarajevo" to "Europe/Belgrade",
            "Europe/Skopje" to "Europe/Belgrade",
            "Europe/Stockholm" to "Europe/Berlin",
            "Europe/Tiraspol" to "Europe/Chisinau",
            "Europe/Uzhgorod" to "Europe/Kyiv",
            "Europe/Vaduz" to "Europe/Zurich",
            "Europe/Vatican" to "Europe/Rome",
            "Europe/Zagreb" to "Europe/Belgrade",
            "Europe/Zaporozhye" to "Europe/Kyiv",
            "GB" to "Europe/London",
            "GB-Eire" to "Europe/London",
            "Hongkong" to "Asia/Hong_Kong",
            "Iceland" to "Africa/Abidjan",
            "Indian/Antananarivo" to "Africa/Nairobi",
            "Indian/Christmas" to "Asia/Bangkok",
            "Indian/Cocos" to "Asia/Yangon",
            "Indian/Comoro" to "Africa/Nairobi",
            "Indian/Kerguelen" to "Indian/Maldives",
            "Indian/Mahe" to "Asia/Dubai",
            "Indian/Mayotte" to "Africa/Nairobi",
            "Indian/Reunion" to "Asia/Dubai",
            "Iran" to "Asia/Tehran",
            "Israel" to "Asia/Jerusalem",
            "Jamaica" to "America/Jamaica",
            "Japan" to "Asia/Tokyo",
            "Kwajalein" to "Pacific/Kwajalein",
            "Libya" to "Africa/Tripoli",
            "Mexico/BajaNorte" to "America/Tijuana",
            "Mexico/BajaSur" to "America/Mazatlan",
            "Mexico/General" to "America/Mexico_City",
            "NZ" to "Pacific/Auckland",
            "NZ-CHAT" to "Pacific/Chatham",
            "Navajo" to "America/Denver",
            "Pacific/Chuuk" to "Pacific/Port_Moresby",
            "Pacific/Enderbury" to "Pacific/Kanton",
            "Pacific/Funafuti" to "Pacific/Tarawa",
            "Pacific/Johnston" to "Pacific/Honolulu",
            "Pacific/Majuro" to "Pacific/Tarawa",
            "Pacific/Midway" to "Pacific/Pago_Pago",
            "Pacific/Pohnpei" to "Pacific/Guadalcanal",
            "Pacific/Ponape" to "Pacific/Guadalcanal",
            "Pacific/Saipan" to "Pacific/Guam",
            "Pacific/Samoa" to "Pacific/Pago_Pago",
            "Pacific/Truk" to "Pacific/Port_Moresby",
            "Pacific/Wake" to "Pacific/Tarawa",
            "Pacific/Wallis" to "Pacific/Tarawa",
            "Pacific/Yap" to "Pacific/Port_Moresby",
            "Poland" to "Europe/Warsaw",
            "Portugal" to "Europe/Lisbon",
            "Singapore" to "Asia/Singapore",
            "Turkey" to "Europe/Istanbul",
            "US/Alaska" to "America/Anchorage",
            "US/Aleutian" to "America/Adak",
            "US/Arizona" to "America/Phoenix",
            "US/Central" to "America/Chicago",
            "US/East-Indiana" to "America/Indiana/Indianapolis",
            "US/Eastern" to "America/New_York",
            "US/Hawaii" to "Pacific/Honolulu",
            "US/Indiana-Starke" to "America/Indiana/Knox",
            "US/Michigan" to "America/Detroit",
            "US/Mountain" to "America/Denver",
            "US/Pacific" to "America/Los_Angeles",
            "US/Pacific-New" to "America/Los_Angeles",
            "US/Samoa" to "Pacific/Pago_Pago",
            "W-SU" to "Europe/Moscow",
        )

    /** Hides a backward alias only when its canonical ID exists on this device: newer tzdata shows
     *  one entry per zone, while older tzdata keeps the pre-rename ID (Europe/Kiev before 2022b). */
    private fun isDuplicateAlias(
        zoneId: String,
        availableIds: Set<String>,
    ): Boolean {
        val canonicalId = BACKWARD_ALIAS_TARGETS[zoneId] ?: return false

        return canonicalId in availableIds
    }

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

    /** Search matching against display name, zone ID and offset label; case-insensitive and
     *  separator-insensitive ("new york" matches America/New_York). */
    fun matches(
        zone: Zone,
        query: String,
    ): Boolean {
        if (query.isEmpty()) return true

        return matchesCore(zone, normalizeZoneText(query), normalizeOffsetText(query))
    }

    /** Bulk matching for a whole list; the query is normalized once instead of once per zone. */
    fun filter(
        zones: List<Zone>,
        query: String,
        extraZoneIds: Set<String> = emptySet(),
    ): List<Zone> {
        if (query.isEmpty()) return zones

        val textQuery = normalizeZoneText(query)
        val offsetQuery = normalizeOffsetText(query)

        return zones.filter { matchesCore(it, textQuery, offsetQuery) || it.zoneId in extraZoneIds }
    }

    private fun matchesCore(
        zone: Zone,
        textQuery: String,
        offsetQuery: String,
    ): Boolean =
        normalizeZoneText(zone.zoneId).contains(textQuery) ||
            normalizeZoneText(zone.displayName).contains(textQuery) ||
            normalizeOffsetText(offsetLabel(zone.offsetSeconds)).contains(offsetQuery)

    /** Zone-text search form with every separator (spaces, underscores, hyphens) removed from
     *  both sides of the match, so "new york", "newyork" and "new_york" all hit
     *  America/New_York — stripping separators only merges spellings, never drops a match. */
    private fun normalizeZoneText(text: String): String = text.lowercase().replace(ZONE_TEXT_SEPARATOR_REGEX, "")

    private val ZONE_TEXT_SEPARATOR_REGEX = Regex("[\\s_-]")

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
