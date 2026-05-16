This is an Android/Kotlin Gradle repo for Parropeato.

Use Java 17. The main modules are `mobile`, `wear`, and `common`. `smartfoo` is an external dependency that can be overridden locally.
Build PR-equivalent debug artifacts with `./gradlew :wear:assembleDebug :mobile:assembleDebug`.
Prefer `common` for app behavior, state, settings, and utilities that should stay consistent across mobile and wear.
Do not commit signing keys, service account JSON, APKs, AABs, or local release artifacts.
