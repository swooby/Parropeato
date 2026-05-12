# M. Parropeato – docs site

The files in this folder are published as a GitHub Pages site from the `main` branch:

**Production site:** https://swooby.github.io/Ropeato/

## Previewing docs on a pull-request branch

The `docs-build` CI workflow automatically deploys a preview of the docs for every PR
that touches the `docs/` folder. Once the workflow completes, it posts a comment on the
PR with a direct link:

```
https://swooby.github.io/Ropeato/pr-preview/pr-<PR number>/
```

> **Note:** The preview subfolder persists on `gh-pages` until it is manually cleaned up.
> Merging the PR does not automatically remove the preview.

If you need to preview before CI runs (or without opening a PR), use one of the manual
options below.

### Option 1 – local HTTP server (recommended for local work)

Serve the `docs/` folder with any static HTTP server.  Python's built-in server is the
quickest option:

```bash
cd docs
python3 -m http.server 8000
```

Then open http://localhost:8000 in your browser.

### Option 2 – local file preview

1. Check out the branch.
2. Open `docs/index.html` directly in a browser (e.g. `File > Open` or drag the file into the browser).

> **Note:** Some browsers restrict local file access for resources loaded by the page.
> If images or styles are missing, use a local HTTP server instead (see Option 1).

### Option 3 – htmlpreview.github.io (no local setup)

Replace `<branch>` with your branch name and open the URL in a browser:

```
https://htmlpreview.github.io/?https://github.com/swooby/Ropeato/blob/<branch>/docs/index.html
```

Example for a branch named `my-docs-change`:

```
https://htmlpreview.github.io/?https://github.com/swooby/Ropeato/blob/my-docs-change/docs/index.html
```

> **Note:** htmlpreview.github.io renders a snapshot and may not reflect the latest push
> immediately. The automated CI preview (above) is more reliable for reviewing final output.
