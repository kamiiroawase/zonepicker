# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamiiroawase/zonepicker.svg)](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue.svg)](LICENSE)

An Android time zone picker library: **one Activity completes the pick** — a curated grouped list of common zones, full search and a "follow system" option, built with Jetpack Compose, Material styling, automatic dark-mode and edge-to-edge adaptation. Compose hosts can alternatively embed the same picker with **`ZonePickerDialog` / `ZonePickerBottomSheet`** — a dialog or bottom sheet straight inside their own UI.

[中文版](README.md)

## Usage

Published on [Maven Central](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker); versions follow `v*` git tags. An Android library (minSdk 26, no desugaring); `mavenCentral()` is the default repository in Android Studio templates — no extra setup. The UI is rendered with Jetpack Compose and its dependencies flow in through Gradle, so hosts integrate it with or without Compose of their own. As of 3.0 the UI is fully Compose: the public API is unchanged and the AppCompat dependency is gone.

```kotlin
dependencies {
    implementation("io.github.kamiiroawase:zonepicker:3.0.1")
}
```

The type-safe `ZonePickerContract` launch is recommended — results distinguish selected / follow-system / canceled:

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
        selectedZoneId = currentZoneId,              // checkmark on the current item; null means follow-system
        accentColor = Color.parseColor("#3F51B5"),   // optional, default #F05E1C; paints the header band, bottom band and selection marks
        title = "Choose time zone",                  // optional; the built-in default is used when omitted
        locale = inAppLocale,                        // optional; the in-app language — strings, zone names and RTL follow it
        defaultZoneIds = marketZones,                // optional; a custom default list (e.g. the app's key markets)
    )
)
```

Without `locale` the picker follows the system language (and the system per-app locale from API 33+); an in-app language switcher has no system channel to read on API 26-32, so pass it explicitly as above. The Compose entries (dialog / bottom sheet) render inside the host's UI and inherit its language on their own — no parameter needed.

The default list orders itself circularly from the device's own time zone (its offset group leads); passing `defaultZoneIds` swaps in the host's curated zones, with every GMT offset the list leaves uncovered still getting one representative zone. The two Compose entries take the same-named parameter.

When cancel and follow-system need no distinguishing, the basic form suffices:

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
        selectedZoneId = currentZoneId,              // null means follow-system
        accentColor = Color.parseColor("#3F51B5"),   // optional, default #F05E1C; paints the header band, bottom band and selection marks
        title = "Choose time zone"                   // optional; the built-in default is used when omitted
    )
)
```

### Compose entries: dialog and bottom sheet

Hosts already using Compose can skip the Activity launch and drop the same picker straight into their own composition (as of 3.1). The list, search and follow-system option are identical to the full-screen entry; results reuse `ZonePickerResult` and only ever deliver selected / follow-system — cancellation never arrives through `onResult`, it is expressed by `onDismissRequest` alone. The caller owns visibility; close in both callbacks:

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
        accentColor = Color(0xFF3F51B5),           // optional, default #F05E1C; selection mark and search cursor
        title = "Choose time zone",                // optional; the built-in default is used when omitted
    )
}
```

`ZonePickerBottomSheet(...)` takes the same parameters and renders as a Material bottom sheet; a tap first plays the sheet's hide animation and only then fires `onResult`.

Highlights:

- The UI renders with Jetpack Compose; hosts need no Compose setup of their own
- Beyond the full-screen Activity, `ZonePickerDialog` / `ZonePickerBottomSheet` compose entries embed the picker into any Compose hierarchy — no Activity result wiring
- English (default) plus Simplified/Traditional Chinese, Japanese and Korean UI built in, picked by the system or in-app language; zone display names come from ICU in the same language, other languages fall back to English chrome with zone names still in the system language
- The list ships 27 curated zones covering every GMT offset, circularly ordered from the device's own offset group (a GMT+8 device starts at UTC+8); a host-supplied `defaultZoneIds` list replaces the curated picks while uncovered offsets still get a representative zone; "follow system" pinned to the top, the selected item checkmarked
- Full instant search over display names / zone IDs / GMT offsets (`gmt+8`, `utc-5` both work) / country names in Chinese and English (searching "中国" surfaces the mainland, Hong Kong, Macau, Taiwan and Singapore zones) / common Chinese city names (上海, 纽约, 東京 — simplified or traditional)
- All legacy aliases pruned per the IANA `backward` list (`US/Pacific`, `Japan`, …); a legacy alias passed in maps automatically onto its display entry
- Automatic dark-mode and edge-to-edge adaptation (status bar / navigation bar / keyboard), independent of the host's targetSdk; content caps its width and centers on tablets and landscape
- The accent runs through the header band and the bottom navigation-bar area; titles, icons and selection marks pair black/white by contrast
- The light/dark palettes and the `zp_*` strings are overridable via same-name resources
- Ships a baseline profile inside the AAR — AGP 8+ merges it into the consuming build, Profileinstaller (already on the classpath through Compose) AOT-compiles it at install or first launch, and every entry's first open benefits
- TalkBack announces the selected state; the back arrow mirrors automatically under RTL
- The result returns via Activity Result; persistence stays with the host

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

55 unit tests (JUnit, `testDebugUnitTest`) cover zone grouping and legacy-alias mapping, search filtering (country and Chinese city aliases included), anchored ordering, accent colors, locale and layout-direction resolution, and the Activity Result contract. Quality gates hang off `build`: Spotless formatting, Android Lint and the public-API golden check (`api/zonepicker.api` — see [CONTRIBUTING.md](CONTRIBUTING.md)). The API reference publishes to GitHub Pages from CI: <https://kamiiroawase.github.io/zonepicker/>. The demo lives in the `app` module and shows all three entries (`./gradlew :app:installDebug`); pushing a `v*` tag triggers the Release workflow publishing to Maven Central.

## License

[The Unlicense](LICENSE) — public domain.
