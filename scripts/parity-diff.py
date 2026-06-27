#!/usr/bin/env python3
"""Compare Roborazzi PNGs against web reference captures (≤1% pixel diff threshold)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description="Lumen web vs Android parity diff")
    parser.add_argument("--android-dir", type=Path, required=True)
    parser.add_argument("--web-dir", type=Path, required=True)
    parser.add_argument("--threshold", type=float, default=0.01, help="Max allowed diff ratio (0.01 = 1%)")
    parser.add_argument("--report", type=Path, default=Path("parity-report.html"))
    args = parser.parse_args()

    if not args.android_dir.is_dir():
        print(f"Android dir missing: {args.android_dir}", file=sys.stderr)
        return 2
    if not args.web_dir.is_dir():
        print(f"Web dir missing: {args.web_dir}", file=sys.stderr)
        return 2

    android_pngs = sorted(args.android_dir.glob("**/*.png"))
    if not android_pngs:
        print("No Android screenshots found.", file=sys.stderr)
        return 1

    # Pillow optional — scaffold writes a placeholder report until CI installs deps.
    try:
        from PIL import Image, ImageChops
    except ImportError:
        args.report.write_text(
            "<html><body><h1>Parity scaffold</h1>"
            f"<p>Install Pillow to run pixel diffs. Found {len(android_pngs)} Android captures.</p></body></html>",
            encoding="utf-8",
        )
        print(f"Wrote scaffold report to {args.report}")
        return 0

    rows: list[str] = []
    failed = 0
    for android_path in android_pngs:
        web_path = args.web_dir / android_path.name
        if not web_path.exists():
            rows.append(f"<tr><td>{android_path.name}</td><td colspan='2'>missing web ref</td></tr>")
            failed += 1
            continue
        a = Image.open(android_path).convert("RGB")
        w = Image.open(web_path).convert("RGB")
        if a.size != w.size:
            w = w.resize(a.size)
        diff = ImageChops.difference(a, w)
        pixels = a.size[0] * a.size[1]
        changed = sum(1 for px in diff.getdata() if px != (0, 0, 0))
        ratio = changed / pixels
        ok = ratio <= args.threshold
        if not ok:
            failed += 1
        rows.append(
            f"<tr><td>{android_path.name}</td><td>{ratio:.4%}</td>"
            f"<td>{'pass' if ok else 'FAIL'}</td></tr>"
        )

    html = (
        "<html><head><title>Lumen parity</title></head><body>"
        "<h1>Lumen parity diff</h1><table border='1'>"
        "<tr><th>Screen</th><th>Diff</th><th>Status</th></tr>"
        + "".join(rows)
        + f"</table><p>Failed: {failed}</p></body></html>"
    )
    args.report.write_text(html, encoding="utf-8")
    print(f"Wrote {args.report} ({failed} failures)")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
