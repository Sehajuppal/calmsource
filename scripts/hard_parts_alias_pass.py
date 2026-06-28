#!/usr/bin/env python3
"""One-off API alias renames for LumenTokens hard-parts drop-in."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP = ("lovable-e738675c", "scratch", "merge-kit/kotlin")

REPLACEMENTS = [
    ("LumenTokens.Layout.", "LumenLayout."),
    ("LumenTokens.Motion.", "LumenMotion."),
    ("LumenTokens.Elevation.", "LumenElevation."),
    ("LumenTokens.Color.surfaceHi", "LumenTokens.Color.surfaceMuted"),
    ("LumenTokens.Color.brandHi", "LumenTokens.Color.brandGlow"),
    ("LumenTokens.Color.glassOverlayFaint", "LumenTokens.Color.borderSubtle"),
    ("LumenTokens.Color.glassOverlay", "LumenTokens.Color.glass"),
    ("LumenTokens.Color.dangerContainer", "LumenExtendedColors.dangerContainer"),
    ("LumenTokens.Color.successContainer", "LumenExtendedColors.successContainer"),
    ("LumenTokens.Color.errorBright", "LumenExtendedColors.errorBright"),
    ("LumenTokens.Color.statusHealthy", "LumenExtendedColors.statusHealthy"),
    ("LumenTokens.Color.focusRingWidth", "LumenExtendedColors.focusRingWidth"),
    ("LumenTokens.Color.profilePink", "LumenProfileColors.pink"),
    ("LumenTokens.Color.profileBlue", "LumenProfileColors.blue"),
    ("LumenTokens.Color.profileGreen", "LumenProfileColors.green"),
    ("LumenTokens.Color.profileAmber", "LumenProfileColors.amber"),
    ("LumenTokens.Color.profilePurple", "LumenProfileColors.purple"),
    ("LumenTokens.Color.profileRed", "LumenProfileColors.red"),
    ("LumenTokens.Color.profileIndigo", "LumenProfileColors.indigo"),
    ("LumenTokens.Color.profileFuchsia", "LumenProfileColors.fuchsia"),
    ("LumenTokens.Color.profileYellow", "LumenProfileColors.yellow"),
    ("LumenTokens.Color.profileRose", "LumenProfileColors.rose"),
    ("LumenTokens.Color.profileEmerald", "LumenProfileColors.emerald"),
    ("LumenTokens.Color.profileCyan", "LumenProfileColors.cyan"),
    ("LumenTokens.Color.profileSky", "LumenProfileColors.sky"),
    ("LumenTokens.Color.profileViolet", "LumenProfileColors.violet"),
    ("LumenTokens.Color.profilePeach", "LumenProfileColors.peach"),
    ("LumenTokens.Color.profileOrange", "LumenProfileColors.orange"),
    ("LumenTokens.Color.profileLilac", "LumenProfileColors.lilac"),
    ("LumenTokens.Color.profileMagenta", "LumenProfileColors.magenta"),
]

# info must run after profile* replacements; handle separately with word boundary
INFO_OLD = "LumenTokens.Color.info"
INFO_NEW = "LumenExtendedColors.info"


def should_skip(path: Path) -> bool:
    s = str(path)
    return any(x in s for x in SKIP) or "LumenTokens.generated.kt" in s


def main() -> None:
    for path in ROOT.rglob("*.kt"):
        if should_skip(path):
            continue
        text = path.read_text(encoding="utf-8")
        orig = text
        for old, new in REPLACEMENTS:
            text = text.replace(old, new)
        text = text.replace(INFO_OLD, INFO_NEW)
        if text != orig:
            path.write_text(text, encoding="utf-8")
            print("updated", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
