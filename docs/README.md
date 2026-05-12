# M. Parropeato – docs site

The files in this folder are published as a GitHub Pages site from the `main` branch:

**Production site:** https://swooby.github.io/Ropeato/

## Previewing docs on a pull-request branch

GitHub Pages only publishes from `main`, so a PR branch's docs are not automatically
deployed. Use one of the options below to preview changes before merging.

### Option 1 – htmlpreview.github.io (no local setup)

Replace `<branch>` with your branch name and open the URL in a browser:

```
https://htmlpreview.github.io/?https://github.com/swooby/Ropeato/blob/<branch>/docs/index.html
```

Example for a branch named `my-docs-change`:

```
https://htmlpreview.github.io/?https://github.com/swooby/Ropeato/blob/my-docs-change/docs/index.html
```

### Option 2 – local file preview

1. Check out the PR branch.
2. Open `docs/index.html` directly in a browser (e.g. `File > Open` or drag the file into the browser).

> **Note:** Some browsers restrict local file access for resources loaded by the page.
> If images or styles are missing, use a local HTTP server instead (see Option 3).

### Option 3 – local HTTP server

Serve the `docs/` folder with any static HTTP server.  Python's built-in server is the
quickest option:

```bash
cd docs
python3 -m http.server 8000
```

Then open http://localhost:8000 in your browser.
