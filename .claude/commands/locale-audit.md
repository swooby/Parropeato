# Locale Audit

Audit all Android locale `strings.xml` files under `common/src/main/res/` and report problems. Fix any issues found.

## What to check

Run the following Python audit inline with the Bash tool:

```python
import xml.etree.ElementTree as ET
from pathlib import Path

base = Path('common/src/main/res')

def parse(path):
    tree = ET.parse(path)
    return {el.get('name'): (el.text, el.get('translatable')) for el in tree.getroot() if el.tag == 'string'}

en_raw = parse(base / 'values/strings.xml')
non_translatable = {k for k, (v, t) in en_raw.items() if t == 'false'}
en = {k: v for k, (v, t) in en_raw.items()}

locales = sorted(p for p in base.iterdir() if p.name.startswith('values-'))
for loc in locales:
    f = loc / 'strings.xml'
    if not f.exists():
        continue
    strings = {k: v for k, (v, t) in parse(f).items()}

    # 1. Spurious entries: locale defines a string marked translatable="false" in base
    spurious = [k for k in strings if k in non_translatable]

    # 2. Missing translations: translatable base string absent from locale
    missing = [k for k in en if k not in non_translatable and k not in strings]

    if spurious:
        print(f'{loc.name} SPURIOUS (should be removed): {spurious}')
    if missing:
        print(f'{loc.name} MISSING translations: {missing}')

print('Audit complete.')
```

## Rules

- **Spurious**: a locale file defines a string whose base entry has `translatable="false"` — remove it; Android falls back automatically.
- **Missing**: a translatable base string has no entry in a locale file — it needs a translation added.
- Strings whose correct translation coincidentally matches the English value are **not** a problem — leave them in place as confirmed translations.

## After the audit

- Remove any spurious entries.
- For missing translations, generate appropriate translations for each affected locale and add them.
- Confirm the build passes: `./gradlew :mobile:compileDebugKotlin :wear:compileDebugKotlin`
