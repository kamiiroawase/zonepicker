# Contributing to ZonePicker

Thanks for considering a contribution! The library is small on purpose — keeping it that
way is part of the review.

## Getting set up

```bash
git clone https://github.com/kamiiroawase/zonepicker.git
cd zonepicker
./gradlew build                            # build, tests, lint, Spotless and the API check
./gradlew :zonepicker:testDebugUnitTest    # just the unit tests
./gradlew :app:installDebug                # the demo app on a device/emulator
```

Everything hangs off `build`: if your change passes it, CI will pass it too.

- **Formatting** is enforced by Spotless (ktlint, IntelliJ IDEA style, 4-space indent).
  Run `./gradlew spotlessApply` before pushing; a formatting violation fails the build.
- **The public API is guarded.** `zonepicker/api/zonepicker.api` is the golden dump of the
  published surface; `apiCheck` (part of `build`) fails on drift. If you intentionally
  change the public API, run `./gradlew :zonepicker:apiDump`, review the diff and commit it
  alongside the change. A new public type also needs an entry in `publicApiClasses` in
  `zonepicker/build.gradle.kts`.
- **Comments explain constraints, not mechanics** — the codebase leans on comments that say
  *why* something is shaped the way it is. Matching that style helps a PR land quickly.

## Adding a translation

The UI ships English (the default `values/` bucket) plus a set of Chinese buckets. Zone
display names need no translation — they localize through ICU in whatever locale the picker
runs — so a translation is exactly one file:

1. Copy `zonepicker/src/main/res/values/strings.xml` to
   `zonepicker/src/main/res/values-<locale>/strings.xml` (e.g. `values-ja`).
2. Translate the eight UI strings (`zp_title` … `zp_back`).
3. Leave `zp_taipei_name`, `zp_hong_kong_name` and `zp_macau_name` **empty** — they are the
   greater-China display-name overrides, meaningful only in Chinese variants. ICU supplies
   the names in every other language.
4. Run `./gradlew build`. Android Lint fails on a bucket missing keys (`MissingTranslation`),
   so a partial file cannot slip through.

## Adding a city or country search alias

Search aliases live in `COUNTRY_ZONES` and `CITY_ZONE_ALIASES` in
`zonepicker/src/main/kotlin/io/github/kamiiroawase/zonepicker/ZoneData.kt`. Include both
Chinese scripts (东京 / 東京) where they differ; English names need no entry when they already
appear inside the zone ID. Add the cases to `ZoneDataTest` — search behavior is
unit-tested, and the tests document the matching rules.

## Releasing

Releases are tag-driven: pushing a `v*` tag runs the Release workflow and publishes to
Maven Central. Maintainers only; no action needed for regular contributions.
