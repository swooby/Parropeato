# Ropeato

Ropeato is a novelty Android and Wear OS app that listens to what you say and parrots it back in a robotic text-to-speech voice.

## Release process

GitHub Actions builds the mobile and Wear OS apps on pull requests, pushes to `main`, and release tag pushes. Tag pushes also build signed Android App Bundles and upload them to Google Play.

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
base64 -i ropeato-release.jks | pbcopy
```

`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is the full JSON key for the Google Cloud service account that has app-level release access in Google Play Console.

Do not commit keystores or service account JSON files. They are ignored by `.gitignore`.

### Google Play setup

1. Enable the Google Play Android Developer API in the Google Cloud project that owns the release service account.
2. Create a service account and JSON key.
3. Add the service account email in Google Play Console under `Users and permissions`.
4. Grant app-level release permissions for both Ropeato package names: `com.swooby.ropeato.mobile` and `com.swooby.ropeato.wear`.
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
packageName: com.swooby.ropeato.mobile
tracks: internal
status: draft

packageName: com.swooby.ropeato.wear
tracks: wear:internal
status: draft
```

This creates draft mobile and Wear OS internal-track releases in Play Console. Review them manually before rollout.

Before the first Wear OS upload, enable the dedicated Wear OS release track in Play Console:

1. Open Play Console and select the app.
2. Go to `Setup > Advanced settings`.
3. Open the `Form factors` tab.
4. Add or enable `Wear OS`.
5. Create the dedicated Wear OS testing/release track.

Wear OS bundles must be published to form-factor tracks such as `wear:internal`, `wear:beta`, or `wear:production`. Mobile bundles use the normal tracks such as `internal`, `beta`, or `production`.

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
