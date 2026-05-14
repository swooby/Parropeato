# AGENTS.md

## Project

Parropeato is an Android and Wear OS Kotlin app that listens to speech using speech-to-text and repeats it back using text-to-speech.

## Modules

- `mobile`: Android app entry point.
- `wear`: Wear OS app, tile, and complication services.
- `common`: shared app logic, settings, view model, and Compose support.
- `smartfoo`: local Android utility library used by app modules.
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
- Keep platform-specific UI, lifecycle, tile, complication, and service behavior inside `mobile` or `wear`.
- Avoid adding new dependencies unless they materially simplify the change.

## Android Notes

- Compose is enabled in `mobile`, `wear`, and `common`.
- `smartfoo` is a plain Android library and should stay reusable.
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

## Release Notes

- Release signing is driven by environment variables in Gradle and GitHub Actions.
- Tag pushes build release bundles and upload draft internal Google Play releases.
- Do not change package names, signing behavior, or Play tracks without calling it out explicitly.
