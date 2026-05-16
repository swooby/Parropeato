# Contributing to Parropeato

## Development Setup

### Local SmartFoo Override

By default, the project uses the `smartfoo` artifact from Maven Central.
To use a local version of the library for development, add `PATH_SMARTFOO` to your `local.properties` file.
This file is ignored by Git, preventing the local path from being accidentally committed.

Example `local.properties`:
```properties
PATH_SMARTFOO=../../SmartFoo/smartfoo/android/smartfoo-android-lib-core/
```

When this property is defined, the build system uses a **Gradle Composite Build** (`includeBuild`) to automatically substitute the Maven dependency with your local source.
This allows you to work on both projects simultaneously without modifying `libs.versions.toml`.

## Adding a UI language

`common/src/main/res/xml/locales_config.xml` declares the languages for which the app has complete UI string translations.
Android uses this list to populate the per-app language selector in system Settings → Apps → M. Parropeato → Language (API 33+, safe here because `minSdk = 34`).
Both `mobile` and `wear` manifests reference it via `android:localeConfig="@xml/locales_config"`.

> **This file is not related to TTS voices or speech-recognition locales.** Those are runtime capabilities of the device's TTS engine and speech recognizer and are managed separately inside the app's Settings screen.

### Steps to add a new language

1. **Translate the strings.** Create `common/src/main/res/values-<tag>/strings.xml` (e.g. `values-fr/`, `values-ja/`) and provide translations for every **translatable** string in `common/src/main/res/values/strings.xml`.
   Strings marked `translatable="false"` (proper nouns, acronyms) must not be included — Android falls back to the base value automatically.

2. **Register the locale.** Add a matching `<locale>` entry to `locales_config.xml`:
   ```xml
   <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
       <locale android:name="en"/>
       <locale android:name="fr"/>  <!-- example: French -->
   </locale-config>
   ```

   The `android:name` value must be a valid BCP 47 language tag and must match the `values-<tag>` folder name exactly.

**Both steps are required and must stay in sync.**
Adding a locale to `locales_config.xml` without a translation causes the system language picker to offer a language the app cannot display.
Adding a translation folder without updating `locales_config.xml` means the system will never offer that language to users.

### Current supported UI languages

| Tag | Language |
|-----|----------|
| `en` | English |
| `ar` | Arabic (العربية) |
| `de` | German (Deutsch) |
| `es` | Spanish (Español) |
| `fr` | French (Français) |
| `hi` | Hindi (हिन्दी) |
| `id` | Indonesian (Bahasa Indonesia) |
| `ja` | Japanese (日本語) |
| `ko` | Korean (한국어) |
| `pt-BR` | Portuguese — Brazil (Português Brasil) |
| `ru` | Russian (Русский) |
| `zh-TW` | Chinese — Traditional (繁體中文) |
| `zh-CN` | Chinese — Simplified (简体中文) |

## Developer tools

If you use Claude Code, slash commands for locale auditing, website device sweeps, docs review, and pre-release checks are documented in [`AGENTS.md`](AGENTS.md).

## Release process

GitHub Actions builds the mobile and Wear OS apps on pull requests, pushes to `main`, and release tag pushes.
Tag pushes also build signed Android App Bundles and upload them to Google Play.

### GitHub secrets

Configure these repository secrets in `Settings > Secrets and variables > Actions`:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
```

`ANDROID_KEYSTORE_BASE64` is the base64-encoded release keystore:

```sh
base64 -i parropeato-release.jks | pbcopy
```

`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is the full JSON key for the Google Cloud service account
that has app-level release access in Google Play Console.

Do not commit keystores or service account JSON files. They are ignored by `.gitignore`.

### Google Play setup

1. Enable the Google Play Android Developer API in the Google Cloud project that owns the release service account.
2. Create a service account and JSON key.
3. Add the service account email in Google Play Console under `Users and permissions`.
4. Grant app-level release permissions for the M. Parropeato package: `com.swooby.parropeato`.
5. Add the JSON key contents to GitHub as `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.

### Creating a release

Create and push a version tag:

```sh
git tag v1.0.0
git push origin v1.0.0
```

On tag pushes, the workflow:

1. Decodes the release keystore from GitHub secrets.
2. Builds signed release APK artifacts for the mobile and Wear OS modules.
3. Builds signed release AABs for the mobile and Wear OS modules.
4. Uploads both AABs to Google Play.

The Google Play uploads currently target:

```text
packageName: com.swooby.parropeato
tracks: internal
status: draft

packageName: com.swooby.parropeato
tracks: wear:internal
status: draft
```

This creates draft mobile and Wear OS internal-track releases in Play Console.
Review them manually before rollout.

Before the first Wear OS upload, enable the dedicated Wear OS release track in Play Console:

1. Open Play Console and select the app.
2. Go to `Setup > Advanced settings`.
3. Open the `Form factors` tab.
4. Add or enable `Wear OS`.
5. Create the dedicated Wear OS testing/release track.

Wear OS bundles must be published to form-factor tracks such as `wear:internal`, `wear:beta`, or `wear:production`.
Mobile bundles use the normal tracks such as `internal`, `beta`, or `production`.

### Versioning

The CI workflow uses:

```text
versionCode = github.run_number
versionName = tag name without a leading "v"
```

For example, tag `v1.0.0` produces `versionName` `1.0.0`.

Google Play requires every uploaded build to have a higher `versionCode` than previous uploads.
If a manual Play Console upload used a higher version code than the GitHub Actions run number,
the upload will fail until CI produces a higher code or the workflow versioning strategy is changed.
