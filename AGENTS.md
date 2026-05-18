# AGENTS.md

## Project

Parropeato is an Android and Wear OS Kotlin app that listens to speech using speech-to-text and repeats it back using text-to-speech.

## Modules

- `mobile`: Android app entry point.
- `wear`: Wear OS app entry point.
- `common`: shared app logic, settings, view model, and Compose support.
- `smartfoo`: (Optional) local Android utility library; defaults to Maven Central artifact.
- `docs`: static GitHub Pages site.

## Build And Test

- Use Java 17.
- Build PR-equivalent debug artifacts with `./gradlew :wear:assembleDebug :mobile:assembleDebug`.
- Compile Kotlin for mobile with `./gradlew :mobile:compileDebugKotlin`.
- Run local unit tests with `./gradlew test`.
- Prefer targeted Gradle tasks while iterating, then run the relevant assemble or test task before finishing.

## Code Style

- Kotlin source should follow the surrounding Android/Kotlin style.
- Prefer small, direct changes over broad refactors.
- Prefer `common` for app behavior, state, settings, and utilities that should stay consistent across mobile and wear.
- Keep platform-specific UI and lifecycle inside `mobile` or `wear`.
- Avoid adding new dependencies unless they materially simplify the change.

## Android Notes

- Compose is enabled in `mobile`, `wear`, and `common`.
- `smartfoo` is a plain Android library.
- Do not commit keystores, service account JSON, generated APKs/AABs, or local signing material.

## Localization

- UI string translations live in `common/src/main/res/values-<tag>/strings.xml` (e.g. `values-fr/`, `values-ja/`).
- Every **translatable** string in `common/src/main/res/values/strings.xml` must have a matching entry in each language directory. Strings marked `translatable="false"` (proper nouns, acronyms like `app_name` and `voice_quality_hd`) must NOT be duplicated in locale files — Android falls back to the base value automatically.
- Strings whose correct translation happens to match English (e.g. `accent_color_orange` in German) should still be present in the locale file as an explicit, confirmed translation.
- Wear-specific string overrides (shorter labels for small round screens) go in `wear/src/main/res/values-<tag>/strings.xml`.
- `smartfoo` time-unit plurals and audio-stream labels go in `smartfoo/src/main/res/values-<tag>/smartfoo.xml`.
- After adding a new translation directory, add the matching `<locale>` entry to `common/src/main/res/xml/locales_config.xml` — both steps are required or Android's per-app language picker will be wrong.
- Log-only strings do not need translation; only user-visible strings do.
- `tts_voice_preview` should use a language-appropriate pangram or equivalent phrase.
- The `tts_greeting` and `tts_voice_preview` strings are spoken aloud by TTS — use natural phrasing for the target language.
- Currently supported UI languages: `en`, `ar`, `de`, `es`, `fr`, `hi`, `id`, `ja`, `ko`, `pt-BR`, `ru`, `zh-TW`, `zh-CN`.
- The GitHub Pages site (`www/`) uses `www/i18n.js` for web localization; update it and the `LANGS` array when adding a new language.
- Run `/locale-audit` after adding or changing strings to catch missing translations and spurious `translatable="false"` omissions.

## Slash Commands

Project-level Claude Code slash commands live in `.claude/commands/`. Type `/<name>` to invoke.

| Command | Purpose |
|---------|---------|
| `/locale-audit` | Audit all locale `strings.xml` files: flag missing translations and strings that incorrectly duplicate a `translatable="false"` base entry. |
| `/device-sweep` | Screenshot `www/index.html` across all Chrome DevTools device presets and report layout issues. |
| `/docs-audit` | Review all docs, agent instruction files, slash command definitions, and inline code/script comments for accuracy and staleness; fix every issue found. |
| `/release-check` | Full pre-release gate: clean tree, no committed secrets, build + tests, SDK consistency, locale audit, language-list consistency, docs audit, website device sweep (if `www/` changed), and release mechanics checklist. |

## Firebase

- `google-services.json` lives at the repo root; `mobile/google-services.json` and `wear/google-services.json` are symlinks to it.
- Shared Firebase SDK usage can live in `common`, but Google Services and Crashlytics Gradle plugins must be applied to the Android application modules (`mobile` and `wear`), not the Android library module (`common`).
- Both mobile and wear settings screens have a **Test Crash** button (debug builds only) for verifying Crashlytics end-to-end.
- Analytics DebugView requires running `adb -s <device_id> shell setprop debug.firebase.analytics.app com.swooby.parropeato` targeting each device specifically; the property does not survive reboots. See `docs/firebase testing.md` for full instructions.
- The current diagnostics disclosure and analytics action/outcome audit lives in `ParropeatoAnalytics.kt` KDoc.
- Every new or changed user-visible feature must include privacy-respecting analytics for its important actions and outcomes, unless the change explicitly documents why analytics would be inappropriate.
- Analytics must be implemented behind a global user preference. Treat collection as disabled by default until the user opts in, and let the user opt out later from Settings. Wire this through Firebase's collection controls instead of only suppressing individual log calls.
- Do not log raw speech, recognized text, TTS text, custom user-entered content, stable device identifiers, or highly specific values that could identify a person. Prefer coarse enums such as source, success/failure, selected category, and error class.
- Disable advertising ID collection and ad-personalization signals unless a future release deliberately adds ads/marketing features and updates consent, privacy policy, and Play Data safety disclosures.
- When analytics behavior changes, update the in-app disclosure/privacy policy, Play Data safety answers, and `docs/firebase testing.md` if verification steps change.

## Google Play Review Requirements

Requirements enforced by Play Store reviewers; violations cause rejection. Check these when adding UI or new screens.

### Layout & Text
- **No text clipped by screen edges.** On round Wear OS screens, `fillMaxWidth()` fills the full scene diameter, but the physical bezel clips content past the inscribed chord at that y-position. Compute the safe chord width mathematically (see `greetingWidthFraction` in `BaseMainActivity`) or use conservative horizontal padding. Applies to any text or view near the sides or bottom of the circle.
- **Text must not overflow its container silently.** Use `verticalScroll`, `maxLines` + `TextOverflow.Ellipsis`, or a properly sized container. Cut-off text is grounds for rejection even if the overflow is technically scrollable but invisible.

### Scrollbars
- **Every scrollable view must show a scrollbar while the user interacts with it.** This includes `LazyColumn`, `LazyRow`, `verticalScroll`/`horizontalScroll`, and Wear `ScalingLazyColumn`.
  - Wear: use `PositionIndicator(scalingLazyListState = state)` overlaid in a `Box` — the platform-standard arc indicator.
  - Mobile/common: apply `Modifier.verticalScrollbar(state)` from `ScrollbarUtils.kt` in `common`.
  - When adding a new scrollable list, always include the appropriate indicator — do not wait for a rejection to add it.

### Accessibility
- **Every interactive element needs a `contentDescription`.** Icons used as buttons, image-only controls, and decorative-but-functional elements must have non-empty content descriptions so TalkBack can announce them. Already-present examples: `cd_listening`, `cd_hold_to_talk`, `cd_preview_voice`.
- **Touch targets must be at least 48×48 dp.** Wrap small icons in a `Box` or use `Modifier.minimumInteractiveComponentSize()` if the visual size is smaller.

### Wear OS Specifics
- **Round-screen geometry:** the usable chord width shrinks as y-offset from center increases. Always verify new text or controls near the bottom of the watch face on a round emulator before submitting.
- Diagnostics consent locale screenshot test instructions and screenshot retrieval paths live in `docs/android testing.md`.
- **Settings screens** must use `SwipeDismissableNavHost` (already in place) so swipe-to-dismiss works as expected on Wear OS.

## Release Notes

- Release signing is driven by environment variables in Gradle and GitHub Actions.
- Tag pushes build release bundles and upload draft internal Google Play releases.
- Do not change package names, signing behavior, or Play tracks without calling it out explicitly.
