#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent
BUNDLE = ROOT / 'patch_bundle'
TARGETS = ['index.html', 'app.js', 'style.css', 'README.md']

for name in TARGETS:
    src = BUNDLE / name
    dst = ROOT / name
    if not src.exists():
        print(f'[SKIP] missing bundle file: {name}')
        continue
    if dst.exists():
        bak = dst.with_suffix(dst.suffix + '.bak')
        shutil.copy2(dst, bak)
        print(f'[BACKUP] {bak.name}')
    shutil.copy2(src, dst)
    print(f'[PATCHED] {name}')

print('Patch completed. Re-run the app to verify changes.')
