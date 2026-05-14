# Device Sweep

Sweep the `www/` landing page across all Chrome DevTools device presets and report any layout issues (overlaps, bad wraps, nav breakage).

## Steps

1. **Ensure a local server is running.** The page uses `fetch()` for i18n so it must be served over HTTP, not `file://`. If nothing is listening on port 8765, start one:
   ```
   python3 -m http.server 8765 --directory www
   ```
   Navigate Chrome to `http://localhost:8765/index.html`.

2. **Take a screenshot at each preset** using the Chrome DevTools MCP (`emulate` → `take_screenshot`). Save each file to `$TMPDIR/pp-test/<nn>-<width>-<label>.png`. Standard preset list:

   | # | Label | Width | Height | deviceScaleFactor | mobile |
   |---|-------|-------|--------|-------------------|--------|
   | 01 | Galaxy Z Fold 5 | 344 | 882 | 2.625 | true |
   | 02 | Galaxy S8+ | 360 | 740 | 4 | true |
   | 03 | iPhone SE | 375 | 667 | 2 | true |
   | 04 | iPhone 12 Pro | 390 | 844 | 3 | true |
   | 05 | Pixel 7 | 412 | 915 | 2.625 | true |
   | 06 | iPhone 14 Pro Max | 430 | 932 | 3 | true |
   | 07 | Surface Duo | 540 | 720 | 2.5 | true |
   | 08 | iPad Mini | 768 | 1024 | 2 | true |
   | 09 | iPad Air | 820 | 1180 | 2 | true |
   | 10 | Surface Pro 7 | 912 | 1368 | 2 | true |
   | 11 | Nest Hub | 1024 | 600 | 2 | false |
   | 12 | iPad Pro | 1024 | 1366 | 2 | true |
   | 13 | Nest Hub Max | 1280 | 800 | 2 | false |
   | 14 | Desktop (1440p) | 1440 | 900 | 1 | false |

3. **Read and analyze each screenshot.** Check for:
   - Nav bar: brand visible on left, links/picker on right (desktop) or hamburger (mobile) — no overlap, no wrapping onto two rows
   - Hero text: headline and lede not clipped or wrapping to single-word lines
   - CTA buttons: Play Store + GitHub buttons visible and not stacked oddly
   - General: no element overlap, no horizontal scroll bar

4. **Report findings** in a compact table — one row per device, ✅ pass or ❌ fail with a one-line description of the issue. Fix any failures found.
