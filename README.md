# M. Parropeato – website (`www`)

The files in this folder are published to GitHub Pages.

- **Production site:** https://swooby.github.io/Parropeato/
- **PR preview pattern:** `https://swooby.github.io/Parropeato/pr-preview/pr-<PR number>/`

## Automatic deploy behavior

- Pushes to `main` that change `www/**` deploy production to the site root.
- Pull requests that change `www/**` deploy a preview to `pr-preview/pr-<PR number>/`.
- The workflow comments the preview URL on the PR.

## Required GitHub Pages settings

In `Settings` → `Pages`, configure:

- **Source:** `Deploy from a branch`
- **Branch:** `gh-pages`
- **Folder:** `/(root)`

## Local preview

Use any static HTTP server from this folder:

```bash
cd www
python3 -m http.server 8000
```

Then open http://localhost:8000 in your browser.

> **Note:** The `/device-sweep` Claude Code slash command starts its own server on port 8765.
> That port is unrelated to this manual preview — use whichever port matches the server you started.
