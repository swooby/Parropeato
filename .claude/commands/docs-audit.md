# Docs Audit

Review all documentation, agent instruction files, slash command definitions, and non-trivial inline code/script comments for accuracy and staleness. Fix every issue found.

## Files to read

Read all of these before drawing conclusions:

- `AGENTS.md` / `CLAUDE.md` / `.github/copilot-instructions.md`
- `README.md`
- `www/README.md`
- `docs/` — every `.md` file
- `.claude/commands/` — every `.md` file (the slash command definitions themselves)

## Things to cross-check against the actual repo state

**Module list** — does every module named in docs (`mobile`, `wear`, `common`, `smartfoo`, `docs`) still exist as a directory with a `build.gradle.kts`?

**Build commands** — are the Gradle tasks in AGENTS.md and copilot-instructions.md still valid? Spot-check with `./gradlew tasks --all | grep -E "<task>"`.

**Supported languages** — the language table in `README.md` and the list in `AGENTS.md` must exactly match:
- The `<locale>` entries in `common/src/main/res/xml/locales_config.xml`
- The `values-*/` directories under `common/src/main/res/`
- The locale JSON files under `www/i18n/`
- The `LANGS` array in `www/i18n.js`

**Slash commands table** — the table in `AGENTS.md § Slash Commands` must list every file in `.claude/commands/` and no files that don't exist.

**Localization rules** — verify the docs correctly state:
- Only translatable strings need locale entries (strings with `translatable="false"` must not be duplicated in locale files)
- Coincidental matches (locale translation = English value) are fine and should be kept as confirmed translations

**www/README.md local preview port** — verify the port shown matches `device-sweep.md` and any other references.

**Inline comments** — scan Kotlin source files touched in recent commits (`git log --name-only -20`) for comments that reference removed features, wrong class/method names, or stale TODOs. Only flag comments that are clearly wrong, not just imprecise.

**Script comments** — check any shell scripts or Python snippets in `.claude/commands/` for commands or paths that no longer exist.

## Report format

For each issue: file path + line number (if applicable), what's wrong, and the fix. Then apply every fix.
