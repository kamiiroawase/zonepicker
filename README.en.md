# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamiiroawase/zonepicker.svg)](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue.svg)](LICENSE)

An Android time zone picker library: a curated grouped list, full search and a "follow system" option, shared by three entries:

- **`ZonePickerContract`** — the type-safe Activity entry; results distinguish selected / follow-system / canceled
- **`ZonePickerDialog` / `ZonePickerBottomSheet`** — Compose entries embedding the same picker as a dialog or bottom sheet, no Activity or result wiring

Built with Jetpack Compose, Material styling, automatic dark-mode and edge-to-edge adaptation. The accent takes light / dark pairs, and the bands and the checkmark color independently. The UI ships English (default) plus Simplified/Traditional Chinese, Japanese and Korean; zone display names come from ICU in the running language.

[中文版](README.md) · [API docs](https://kamiiroawase.github.io/zonepicker/) · [Releases](https://github.com/kamiiroawase/zonepicker/releases)

## Installation

```kotlin
dependencies {
    implementation("io.github.kamiiroawase:zonepicker:3.3.0")
}
```

Published on [Maven Central](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker); versions follow `v*` git tags. minSdk 26, no desugaring; `mavenCentral()` is the default repository in Android Studio templates. Compose dependencies flow in through Gradle, so hosts integrate it with or without Compose of their own. As of 3.0 the UI is fully Compose with no AppCompat dependency; the public API stayed compatible.

## Quick start

```kotlin
private val pickerLauncher =
    registerForActivityResult(ZonePickerContract()) { result ->
        when (result) {
            is ZonePickerResult.Selected -> { /* result.zoneId, e.g. "Asia/Shanghai" */ }
            ZonePickerResult.FollowSystem -> { /* the user chose to follow the system */ }
            ZonePickerResult.Canceled -> { /* the user canceled; nothing to do */ }
        }
    }

pickerLauncher.launch(
    ZonePickerRequest(
        selectedZoneId = currentZoneId,   // null means follow-system
        title = "Choose time zone",       // optional; the built-in default is used when omitted
        locale = inAppLocale,             // optional; the in-app language (see "Language")
        defaultZoneIds = marketZones,     // optional; a custom default list (see "Default list")
    )
)
```

## Parameters

Every parameter defaults sensibly; `ZonePicker.createIntent(...)` takes the same names with the same meanings.

| Parameter | Effect | Default |
|---|---|---|
| `selectedZoneId` | the zone ID checkmarked; a legacy alias passed in (`US/Pacific`, `PRC`, …) maps onto the row the list actually shows | `null` = follow-system |
| `accentColor` | the header band (status-bar area included) and the bottom navigation-bar band; the title and back arrow pick black/white from its luminance | resource `zpPrimaryColor` (#F05E1C) |
| `accentColorDark` | replaces the bands in night mode — including a system day/night switch made while the full-screen picker is open (the recreated activity re-resolves) | `null` = both modes share `accentColor` |
| `markColor` | the checkmark and search cursor; `null` derives from the accent, an explicit value applies as-is — see "Color rules" | `null` = derived |
| `markColorDark` | replaces `markColor` in night mode | `null` = both modes share `markColor` |
| `title` | the page title | resource `zp_title` |
| `locale` | an explicit UI language (BCP-47): strings, zone display names and RTL follow it — see "Language" | `null` = system language |
| `defaultZoneIds` | a custom default list — see "Default list" | `null` = 27 built-in curated zones |

### Color rules

A single band color adapts to both modes; accents that differ per mode (black in light, say, while dark mode needs a color that lifts off the dark page) pass `accentColorDark`:

```kotlin
accentColor = Color.parseColor("#3F51B5"),     // light-mode bands
accentColorDark = Color.parseColor("#5C6BC0"), // night bands (a lighter indigo lifts off the dark page)
markColorDark = Color.parseColor("#9FA8DA"),   // the night checkmark and cursor, colored apart from the bands
```

The checkmark and search-cursor color falls into two cases:

- **No `markColor` (the default)**: derived from the accent — the accent itself while it clears 3:1 against the current mode's surface, the row text color otherwise (#333333 light, #E0E0E0 dark). An amber (#FFD54F, ~1.5:1) or white accent in light mode therefore renders the dark-gray fallback checkmark; black in dark mode falls back to the light gray. The fallback is the guardrail: a derived mark always stays readable. The night derivation tracks `accentColorDark` when passed.
- **`markColor` passed (with its `markColorDark` twin)**: honored as handed in — no contrast gate, alpha forced opaque — repainting only the mark and cursor, never the bands. A low-contrast explicit pick (white on the light surface, say) is the host's own call.

### Language

Without `locale` the picker follows the system language (and the system per-app locale from API 33+); an in-app language switcher has no system channel to read on API 26-32, so pass it explicitly. The Compose entries render inside the host's UI and inherit its language on their own — no parameter.

### Default list

The default list orders itself circularly from the device's own time zone (its offset group leads; a GMT+8 device starts at UTC+8) and ships 27 curated zones covering every GMT offset, with "follow system" pinned to the top. Passing `defaultZoneIds` swaps in the host's curated zones (legacy alias IDs in the list map onto their display entries the way a passed-in selection does), with every GMT offset the list leaves uncovered still getting one representative zone. The two Compose entries take the same-named parameter.

## Basic form

When cancel and follow-system need no distinguishing, plain intents suffice:

```kotlin
private val pickerLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val zoneId = ZonePicker.getResultZoneId(result.data)
            // e.g. "Asia/Shanghai"; null means "follow system"
        }
    }

pickerLauncher.launch(
    ZonePicker.createIntent(
        context = this,
        selectedZoneId = currentZoneId,   // null means follow-system
        title = "Choose time zone",
    )
)
```

`ZonePicker.createIntent` takes the parameters from the table above; outside the contract, the `EXTRA_*` constants assemble intents by hand.

## Compose entries: dialog and bottom sheet

```kotlin
var showDialog by rememberSaveable { mutableStateOf(false) }

if (showDialog) {
    ZonePickerDialog(
        onResult = { result ->
            when (result) {
                is ZonePickerResult.Selected -> currentZoneId = result.zoneId
                ZonePickerResult.FollowSystem -> currentZoneId = null
                ZonePickerResult.Canceled -> Unit   // never delivered by the dialog entry
            }
            showDialog = false
        },
        onDismissRequest = { showDialog = false },
        selectedZoneId = currentZoneId,            // null means follow-system
        accentColor = Color(0xFF3F51B5),           // the mark and cursor color (see below)
        title = "Choose time zone",
        defaultZoneIds = marketZones,              // optional, same meaning as the table
    )
}
```

`ZonePickerBottomSheet(...)` takes the same parameters and renders as a Material bottom sheet; a tap first plays the sheet's hide animation and only then fires `onResult`.

Notes:

- The list, search and follow-system option are identical to the full-screen entry; results reuse `ZonePickerResult` and only ever deliver selected / follow-system — cancellation never arrives through `onResult`, it is expressed by `onDismissRequest` alone. The caller owns visibility; close in both callbacks
- No Activity means no bands: `accentColor` colors the mark and cursor — explicit values apply as-is, the omitted default resolves to the library orange and rides the derivation rule above
- No `locale` (the entries render in the host's window and follow its language) and no `*Dark` parameters (the host passes the current mode's value per composition)

## Highlights

- Full instant search over display names / zone IDs / GMT offsets (`gmt+8`, `utc-5` both work) / country names in Chinese and English (searching "中国" surfaces the mainland, Hong Kong, Macau, Taiwan and Singapore zones) / common Chinese city names (上海, 纽约, 東京 — simplified or traditional)
- All legacy aliases pruned per the IANA `backward` list (`US/Pacific`, `Japan`, …); a legacy alias passed in maps automatically onto its display entry
- Automatic dark-mode and edge-to-edge adaptation (status bar / navigation bar / keyboard), independent of the host's targetSdk; content caps its width and centers on tablets and landscape
- Under dark mode the chrome, accent and mark colors each switch to their night values; a day/night switch made while the full-screen picker is open re-resolves on the spot
- Ships a baseline profile inside the AAR — AGP 8+ merges it into the consuming build, Profileinstaller (already on the classpath through Compose) AOT-compiles it at install or first launch, and every entry's first open benefits
- TalkBack announces the selected, loading and no-result states; the back arrow mirrors automatically under RTL
- The result returns via Activity Result; persistence stays with the host

## Resource overrides

Hosts can override every color and string through same-name resources. Colors exist in `values/` and `values-night/` pairs, applying per mode:

| Resource | Effect |
|---|---|
| `zpPrimaryColor` / `zpOnAccent` | the default accent / the title and back arrow over it (used by the resource-default path only) |
| `zpBackground` / `zpSurface` | page background / cards and row surfaces |
| `zpTextStrong` / `zpTextPrimary` / `zpTextSecondary` / `zpTextHint` | input text / primary row text / secondary row text and group headers / search hint |
| `zpDivider` | row dividers |

Strings `zp_title` … `zp_back` (eight); `zp_taipei_name` / `zp_hong_kong_name` / `zp_macau_name` are the greater-China display-name overrides, valued only in Chinese buckets — keep them **empty** elsewhere (ICU supplies the names).

## Known limitations

- The Chinese city-alias table covers common cities (the default list's cities plus major Chinese ones); for an unlisted Chinese city name use the English name (`shanghai`) or the country name instead — likewise for countries missing from the country table
- The UI ships English plus Simplified/Traditional Chinese, Japanese and Korean; other languages fall back to the English chrome (zone display names still render in that language via ICU) — override the `zp_*` resources to add more
- A `selectedZoneId` that still resolves to nothing in the list (e.g. `EST`, `SystemV/*`, or a typo) shows no selection mark
- The Activity entry follows the system language on API 26-32; an in-app language needs the explicit `locale` parameter (the system per-app locale applies automatically from API 33+; the Compose entries always follow the host)
- On Android 15+, a few system skins draw their own three-button navigation-bar background over the bottom accent band

## Development

```bash
git clone https://github.com/kamiiroawase/zonepicker.git
cd zonepicker
./gradlew build                            # build, tests, lint, Spotless and the public-API golden check
./gradlew :zonepicker:testDebugUnitTest    # the data-logic unit tests
```

57 unit tests (JUnit, `testDebugUnitTest`) cover zone grouping and legacy-alias mapping, search filtering (country and Chinese city aliases included), anchored ordering, accent colors, locale and layout-direction resolution, and the Activity Result contract. Quality gates hang off `build`: Spotless formatting, Android Lint and the public-API golden check (see below). The API reference publishes to GitHub Pages from CI: <https://kamiiroawase.github.io/zonepicker/>.

The demo lives in the `app` module: all three entries, with the four color channels (light / dark × band / mark) pickable per group (`./gradlew :app:installDebug`).

### Releasing

Pushing a `v*` git tag triggers the Release workflow: after the full build gate passes, the library artifacts (aar / pom / sources / javadoc) are signed, published to Maven Central and auto-released; a **signed demo APK** (`zonepicker-demo-<tag>.apk`) is built and attached to the matching GitHub Release. The README version coordinates must match the tag — the release gate verifies.

### The public-API golden check

`zonepicker/api/zonepicker.api` is the golden dump of the published surface; `apiCheck` (part of `build`) fails on any drift. If you intentionally change the public API: run `./gradlew :zonepicker:apiDump`, review the diff and commit it alongside the change. A new public type also needs an entry in `publicApiClasses` in `zonepicker/build.gradle.kts`.

### Adding a translation

Zone display names need no translation — they localize through ICU in whatever locale the picker runs — so a translation is exactly one file:

1. Copy `zonepicker/src/main/res/values/strings.xml` to `zonepicker/src/main/res/values-<locale>/strings.xml` (e.g. `values-ja`).
2. Translate the eight UI strings (`zp_title` … `zp_back`).
3. Leave `zp_taipei_name`, `zp_hong_kong_name` and `zp_macau_name` **empty** — they are the greater-China display-name overrides, meaningful only in Chinese variants.
4. Run `./gradlew build`. Android Lint fails on a bucket missing keys (`MissingTranslation`), so a partial file cannot slip through.

### Adding a city or country search alias

Aliases live in `COUNTRY_ZONES` and `CITY_ZONE_ALIASES` in `zonepicker/src/main/kotlin/io/github/kamiiroawase/zonepicker/ZoneData.kt`. Include both Chinese scripts where they differ (东京 / 東京); English names need no entry when they already appear inside the zone ID. Add the cases to `ZoneDataTest` — search behavior is unit-tested, and the tests document the matching rules.

Run `./gradlew spotlessApply` before pushing — a formatting violation fails the build. Comments in this codebase explain *why* something is shaped the way it is, not *what* the next line does; matching that style helps a PR land quickly.

## License

[The Unlicense](LICENSE) — public domain.
