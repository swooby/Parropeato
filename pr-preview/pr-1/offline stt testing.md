# How to test offline STT

## Setup
1. Open **Settings → System → Languages & input → Voice input** (or tap "Download offline speech model" inside Ropeato's settings).
2. Download an offline pack for your target language (e.g. English).
3. Confirm the locale shows **"offline ready"** in Ropeato's Speech To Text settings screen.

## Happy path — offline with model
1. Enable airplane mode (or disable Wi-Fi + mobile data).
2. Hold the mic button and speak.
3. Expected: speech is recognized and repeated back normally. The locale chip still shows "offline ready."

## Fail-fast — offline, no model
1. Select a locale that does **not** have an offline model installed (shows no "offline ready" badge).
2. Enable airplane mode.
3. Hold the mic button.
4. Expected: app immediately shows *"No offline model available. Connect to network or download an offline pack."* — no recognition attempt is made.

## Recovery — come back online
1. While still on the locale with no offline model, re-enable network.
2. Hold the mic button.
3. Expected: recognition succeeds using the network model.

## Download prompt
1. In the offline+no-model state, tap **"Download offline speech model"** in the STT settings screen.
2. Expected: device Settings opens to the voice input / speech download page.

## Regression — online with model
1. Re-enable network and keep the locale with an installed offline model selected.
2. Speak normally.
3. Expected: recognition works; `EXTRA_PREFER_OFFLINE` is `false` (network preferred when available).
