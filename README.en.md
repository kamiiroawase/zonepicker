# ZonePicker

[![Build](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml/badge.svg)](https://github.com/kamiiroawase/zonepicker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kamiiroawase/zonepicker.svg)](https://central.sonatype.com/artifact/io.github.kamiiroawase/zonepicker)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue.svg)](LICENSE)

An Android time zone picker library: **one Activity completes the pick** — a curated grouped list of common zones, full search and a "follow system" option, built with Jetpack Compose, Material styling, automatic dark-mode and edge-to-edge adaptation.

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
    )
)
```

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

Highlights:

- The UI renders with Jetpack Compose; hosts need no Compose setup of their own
- The list ships 27 curated zones covering every GMT offset, UTC+8 first; "follow system" pinned to the top, the selected item checkmarked
- Full instant search over display names / zone IDs / GMT offsets (`gmt+8`, `utc-5` both work) / country names in Chinese and English (searching "中国" surfaces the mainland, Hong Kong, Macau, Taiwan and Singapore zones)
- All legacy aliases pruned per the IANA `backward` list (`US/Pacific`, `Japan`, …); a legacy alias passed in maps automatically onto its display entry
- Automatic dark-mode and edge-to-edge adaptation (status bar / navigation bar / keyboard), independent of the host's targetSdk
- The accent runs through the header band and the bottom navigation-bar area; titles, icons and selection marks pair black/white by contrast
- The light/dark palettes and the `zp_*` strings are overridable via same-name resources
- TalkBack announces the selected state; the back arrow mirrors automatically under RTL
- The result returns via Activity Result; persistence stays with the host

## Known limitations

- Chinese city names (e.g. 「上海」) are not searchable yet — use the English city name (`shanghai`) or the country name instead; likewise for countries missing from the country table
- Display names default to Simplified Chinese. Localizing requires providing both the default `values/` (keeping Chinese) and the target language's `values-<locale>/` — overriding only the default `values/` leaves the zone names Chinese
- A `selectedZoneId` that still resolves to nothing in the list (e.g. `EST`, `SystemV/*`, or a typo) shows no selection mark
- On API 26-32 the picker follows the system language; an in-app language override does not apply (system per-app locales apply from API 33+)
- On Android 15+, a few system skins draw their own three-button navigation-bar background over the bottom accent band

## Development

```bash
git clone https://github.com/kamiiroawase/zonepicker.git
cd zonepicker
./gradlew build                            # build, tests, lint and Spotless checks
./gradlew :zonepicker:testDebugUnitTest    # the data-logic unit tests
```

45 unit tests (JUnit, `testDebugUnitTest`) cover zone grouping and legacy-alias mapping, search filtering, accent colors and the Activity Result contract. Quality gates hang off `build`: Spotless formatting and Android Lint. The demo lives in the `app` module (`./gradlew :app:installDebug`); pushing a `v*` tag triggers the Release workflow publishing to Maven Central.

## License

[The Unlicense](LICENSE) — public domain.
