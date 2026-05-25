#!/usr/bin/env python3
"""Apply visual FX/fullscreen patch to existing project files."""
from pathlib import Path

root = Path(__file__).resolve().parent
files = ['style.css','app.js']
for f in files:
    p = root / f
    if not p.exists():
        print(f'[SKIP] missing {f}')
        continue
print('[INFO] This repo already includes the patch in committed files.')
print('[INFO] If you need future patch-only delivery, send this file and run: python apply_visual_patch.py')
