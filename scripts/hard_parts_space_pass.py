#!/usr/bin/env python3
"""Rename legacy LumenTokens.Space.* to LumenLegacySpace.* in app modules."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGETS = [ROOT / "app-mobile", ROOT / "app-tv"]
SKIP_PARTS = ("LumenTokenExtensions", "LumenLegacyBridge", "LumenTokens.generated")

for base in TARGETS:
    for path in base.rglob("*.kt"):
        if any(s in path.name for s in SKIP_PARTS):
            continue
        text = path.read_text(encoding="utf-8")
        if "LumenTokens.Space." not in text:
            continue
        orig = text
        for name in ("xxs", "xs", "sm", "sm2", "md", "lg", "xl", "xxl", "xxxl", "xxxxl", "xxxxxl", "xxxxxxl"):
            text = text.replace(f"LumenTokens.Space.{name}", f"LumenLegacySpace.{name}")
        if text != orig:
            if "import com.example.calmsource.core.ui.theme.LumenLegacySpace" not in text:
                text = text.replace(
                    "import com.example.calmsource.core.ui.theme.LumenTokens",
                    "import com.example.calmsource.core.ui.theme.LumenLegacySpace\nimport com.example.calmsource.core.ui.theme.LumenTokens",
                    1,
                )
            path.write_text(text, encoding="utf-8")
            print("updated", path.relative_to(ROOT))
