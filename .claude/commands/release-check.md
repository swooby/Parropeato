# Release Check

Full pre-release gate. Run every check below in order, fix every issue found, and produce a final pass/fail summary. Do not declare success until all checks pass.

---

## 1. Working tree

```bash
git status
git stash list
```

- Working tree must be clean (no uncommitted changes, no untracked files that matter).
- No stashed work that belongs in this release.

---

## 2. No committed secrets

```bash
git log --all --full-history -- "*.jks" "*.keystore" "*service_account*.json" "*google-services*.json"
find . -name "*.jks" -o -name "*.keystore" | grep -v ".git"
```

Fail if any keystore or service-account JSON has ever been committed, or if one exists in the working tree outside `.gitignore` coverage.

---

## 3. Build and tests

```bash
./gradlew test
./gradlew :wear:assembleDebug :mobile:assembleDebug
```

Both must succeed with zero errors.

---

## 4. SDK version consistency

Read `mobile/build.gradle.kts` and `wear/build.gradle.kts`. Verify `compileSdk`, `minSdk`, and `targetSdk` are identical in both. Flag any mismatch.

---

## 5. Locale audit

Perform the full audit defined in `.claude/commands/locale-audit.md`:
- Every translatable base string has an entry in every locale file.
- No locale file contains a string marked `translatable="false"` in the base.

---

## 6. Language list consistency

The supported-language set must be identical across all four sources. Compare:

```bash
# Android locale directories
ls common/src/main/res/ | grep values- | sed 's/values-//'

# locales_config.xml
grep 'android:name' common/src/main/res/xml/locales_config.xml

# www i18n JSON files
ls www/i18n/*.json | xargs -I{} basename {} .json

# www/i18n.js LANGS array
grep "code:" www/i18n.js
```

Report any language present in one source but missing from another.

---

## 7. Docs audit

Perform the full audit defined in `.claude/commands/docs-audit.md`:
- Module list, build commands, supported-language tables, slash-commands table, localization rules, and inline code comments.

---

## 8. Website device sweep

Check whether any `www/` files were modified since the last release tag:

```bash
git diff $(git describe --tags --abbrev=0) HEAD -- www/
```

If `www/` has changes, perform the full device sweep defined in `.claude/commands/device-sweep.md` and confirm no layout regressions.

---

## 9. Release mechanics reminder

Verify these items and note their status (cannot be automated):

- [ ] All desired commits are on `main` and the branch is up-to-date with remote.
- [ ] GitHub repository secrets are configured: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
- [ ] Play Console has the Wear OS form-factor track enabled (`Setup → Advanced settings → Form factors`).
- [ ] The intended tag follows the `vMAJOR.MINOR.PATCH` format — CI strips the leading `v` for `versionName`.
- [ ] `versionCode` is driven by `GITHUB_RUN_NUMBER * 10` (mobile) and `GITHUB_RUN_NUMBER * 10 + 1` (wear) — confirm no previous manual Play Console upload used a higher code that would block the upload.

---

## Final summary

Produce a table:

| Check | Result |
|-------|--------|
| Working tree clean | ✅ / ❌ |
| No committed secrets | ✅ / ❌ |
| Build + tests | ✅ / ❌ |
| SDK version consistency | ✅ / ❌ |
| Locale audit | ✅ / ❌ |
| Language list consistency | ✅ / ❌ |
| Docs audit | ✅ / ❌ |
| Website device sweep | ✅ / ❌ / ⏭ skipped (no www changes) |
| Release mechanics | ✅ / ⚠ manual verification needed |

Do not push the release tag until every automated check shows ✅.
