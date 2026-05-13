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

## Release Notes

- Release signing is driven by environment variables in Gradle and GitHub Actions.
- Tag pushes build release bundles and upload draft internal Google Play releases.
- Do not change package names, signing behavior, or Play tracks without calling it out explicitly.
