This is an Android/Kotlin Gradle repo for Ropeato.

Use Java 17. The main modules are `mobile`, `wear`, `common`, and `smartfoo`.
Build PR-equivalent debug artifacts with `./gradlew :wear:assembleDebug :mobile:assembleDebug`.
Prefer `common` for app behavior, state, settings, and utilities that should stay consistent across mobile and wear.
Do not commit signing keys, service account JSON, APKs, AABs, or local release artifacts.
