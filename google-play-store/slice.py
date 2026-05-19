#!/usr/bin/env python3
"""
Creates a rainbow composite by slicing a vertical strip from each
*_cute_icons_*.png screenshot found in the given directory and stitching
them left-to-right in filename order.

Usage:
    python google-play-store/slice.py <uploads-dir>

Examples:
    python google-play-store/slice.py google-play-store/3.0.0/wear
    python google-play-store/slice.py google-play-store/3.0.0/mobile

Output:
    <uploads-dir>/cute_icons_rainbow.png

Requirements:
    brew install pillow   # macOS
    pip install pillow    # other
"""

import sys
from pathlib import Path
from PIL import Image


def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <uploads-dir>")
        sys.exit(1)

    uploads = Path(sys.argv[1])
    if not uploads.is_dir():
        print(f"Error: '{uploads}' is not a directory")
        sys.exit(1)

    source_paths = sorted(uploads.glob("*_cute_icons_*.png"))
    if not source_paths:
        print(f"No *_cute_icons_*.png files found in '{uploads}'")
        sys.exit(1)

    sources = [Image.open(p) for p in source_paths]
    W, H = sources[0].size
    n = len(sources)
    slice_w = W // n

    result = Image.new("RGB", (W, H))
    for i, img in enumerate(sources):
        x0 = i * slice_w
        strip = img.crop((x0, 0, x0 + slice_w, H))
        result.paste(strip, (x0, 0))

    output = uploads / "cute_icons_rainbow.png"
    result.save(output)
    print(f"Saved {output}  ({n} slices, {slice_w}px each)")


if __name__ == "__main__":
    main()
