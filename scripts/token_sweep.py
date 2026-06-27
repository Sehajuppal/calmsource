#!/usr/bin/env python3
"""Phase 10: boundary-safe token literal replacement in Screen/Section files."""
from __future__ import annotations

import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Longest-first numeric dp -> token (whole-token regex only)
DP_TOKEN: dict[str, str] = {
    "999": "LumenTokens.Radius.pill",
    "450": "LumenTokens.Layout.heroHeightMobile",
    "400": "LumenTokens.Layout.sheetMaxWidth",
    "380": "LumenTokens.Layout.panelWidthTv",
    "270": "LumenTokens.Layout.posterHeightTv",
    "260": "LumenTokens.Layout.detailsSkeletonHero",
    "210": "LumenTokens.Layout.posterTileHeightTv",
    "200": "LumenTokens.Layout.channelPanelWidth",
    "180": "LumenTokens.Layout.heroStripHeight",
    "160": "LumenTokens.Layout.posterTileWidth",
    "150": "LumenTokens.Layout.skeletonTitleWidth",
    "140": "LumenTokens.Layout.epgMinBlockWidthTv",
    "120": "LumenTokens.Layout.epgMinBlockWidth",
    "110": "LumenTokens.Layout.clearButtonWidthTv",
    "100": "LumenTokens.Layout.inputWidthSm",
    "90": "LumenTokens.Layout.inputWidthXs",
    "80": "LumenTokens.Layout.bottomNavPadding",
    "70": "LumenTokens.Layout.skeletonChipWidth",
    "64": "LumenTokens.Layout.avatarLg",
    "50": "LumenTokens.Layout.buttonHeight",
    "48": "LumenTokens.Layout.iconXl",
    "40": "LumenTokens.Space.xxxxl",
    "36": "LumenTokens.Layout.offsetLg",
    "32": "LumenTokens.Space.xxxl",
    "30": "LumenTokens.Layout.spacerMd",
    "28": "LumenTokens.Radius.xl",
    "24": "LumenTokens.Space.xxl",
    "20": "LumenTokens.Space.xl",
    "18": "LumenTokens.Layout.iconMd",
    "16": "LumenTokens.Space.lg",
    "14": "LumenTokens.Radius.md",
    "12": "LumenTokens.Space.md",
    "10": "LumenTokens.Radius.sm",
    "8": "LumenTokens.Space.sm2",
    "6": "LumenTokens.Space.sm",
    "5": "LumenTokens.Layout.progressHeight",
    "4": "LumenTokens.Space.xs",
    "3": "LumenTokens.Color.focusRingWidth",
    "2": "LumenTokens.Space.xxs",
    "1": "1.dp",  # allowlisted
    "0.5": "LumenTokens.Layout.hairline",
}

SHAPE_REPLACEMENTS = [
    (re.compile(r"RoundedCornerShape\(999\.dp\)"), "LumenTokens.Shape.pill"),
    (re.compile(r"RoundedCornerShape\(28\.dp\)"), "LumenTokens.Shape.xl"),
    (re.compile(r"RoundedCornerShape\(20\.dp\)"), "LumenTokens.Shape.lg"),
    (re.compile(r"RoundedCornerShape\(16\.dp\)"), "LumenTokens.Shape.lg"),
    (re.compile(r"RoundedCornerShape\(14\.dp\)"), "LumenTokens.Shape.md"),
    (re.compile(r"RoundedCornerShape\(12\.dp\)"), "LumenTokens.Shape.md"),
    (re.compile(r"RoundedCornerShape\(10\.dp\)"), "LumenTokens.Shape.sm"),
    (re.compile(r"RoundedCornerShape\(8\.dp\)"), "LumenTokens.Shape.sm"),
    (re.compile(r"RoundedCornerShape\(6\.dp\)"), "LumenTokens.Shape.xs"),
    (re.compile(r"RoundedCornerShape\(3\.dp\)"), "LumenTokens.Shape.xs"),
    (re.compile(r"RoundedCornerShape\(50\)"), "LumenTokens.Shape.pill"),
    (re.compile(r"RoundedCornerShape\(percent\s*=\s*50\)"), "LumenTokens.Shape.pill"),
    (re.compile(r"RoundedCornerShape\(LumenTokens\.Space\.[a-zA-Z0-9]+\)"), "LumenTokens.Shape.md"),
]

COLOR_MAP = [
    ("Color(0xFF3D6BFF)", "LumenTokens.Color.brand"),
    ("Color(0xFF5C86FF)", "LumenTokens.Color.brandHi"),
    ("Color(0xFF06070B)", "LumenTokens.Color.bg"),
    ("Color(0xFF0E1117)", "LumenTokens.Color.surface"),
    ("Color(0xFF151A22)", "LumenTokens.Color.surfaceHi"),
    ("Color(0xFF1F2630)", "LumenTokens.Color.border"),
    ("Color(0xFFF5F7FA)", "LumenTokens.Color.textPrimary"),
    ("Color(0xFFA6ADBB)", "LumenTokens.Color.textSecondary"),
    ("Color(0xFF6B7280)", "LumenTokens.Color.textMuted"),
    ("Color(0xFFFF4D5E)", "LumenTokens.Color.danger"),
    ("Color(0xFF34D399)", "LumenTokens.Color.success"),
    ("Color(0xFF10B981)", "LumenTokens.Color.statusHealthy"),
    ("Color(0xFFEF4444)", "LumenTokens.Color.errorBright"),
    ("Color(0xFFF59E0B)", "LumenTokens.Color.warning"),
    ("Color(0xFFFBBF24)", "LumenTokens.Color.ratingGold"),
    ("Color(0xFF3B82F6)", "LumenTokens.Color.info"),
    ("Color(0xFF22D3EE)", "LumenTokens.Color.cyan"),
    ("Color(0xFFA78BFA)", "LumenTokens.Color.violet"),
    ("Color(0x1AFFFFFF)", "LumenTokens.Color.glassOverlay"),
    ("Color(0x0DFFFFFF)", "LumenTokens.Color.glassOverlayFaint"),
    ("Color(0xCC0B0B10)", "LumenTokens.Color.controlScrimDark"),
    ("Color(0xCCFAFAFA)", "LumenTokens.Color.controlScrimLight"),
    ("Color(0x3D10B981)", "LumenTokens.Color.debridTint"),
    ("Color(0x3DFBBF24)", "LumenTokens.Color.ratingGold.copy(alpha = 0.24f)"),
    ("Color(0x3D22D3EE)", "LumenTokens.Color.cyan.copy(alpha = 0.24f)"),
    ("Color(0x3DA78BFA)", "LumenTokens.Color.violet.copy(alpha = 0.24f)"),
    ("Color(0xFFFEF3C7)", "LumenTokens.Color.warningSurface"),
    ("Color(0xFFD97706)", "LumenTokens.Color.warningText"),
    ("Color(0xFF1C1B2A)", "LumenTokens.Color.debridPanel"),
    ("Color(0xFFE57373)", "LumenTokens.Color.errorSoft"),
    ("Color(0xFF0B0B10)", "LumenTokens.Color.bg"),
    ("Color(0xFFFAFAFA)", "LumenTokens.Color.textPrimary"),
    ("Color(0xFF000000)", "LumenTokens.Color.bg"),
    ("Color.White", "LumenTokens.Color.textPrimary"),
    ("Color.Black", "LumenTokens.Color.bg"),
    ("Color.Transparent", "Color.Transparent"),
]

SHAPE_FIXES = [
    (re.compile(r"RoundedCornerShape\(LumenTokens\.Space\.xxl\)"), "LumenTokens.Shape.lg"),
    (re.compile(r"RoundedCornerShape\(LumenTokens\.Space\.xl\)"), "LumenTokens.Shape.lg"),
    (re.compile(r"RoundedCornerShape\(LumenTokens\.Space\.xs\)"), "LumenTokens.Shape.xs"),
    (re.compile(r"RoundedCornerShape\(LumenTokens\.Space\.xxs\)"), "LumenTokens.Shape.xs"),
    (re.compile(r"RoundedCornerShape\(t\.radii\.lg\)"), "LumenTokens.Shape.lg"),
    (re.compile(r"RoundedCornerShape\(t\.radii\.md\)"), "LumenTokens.Shape.md"),
]

EXTRA_DP: dict[str, str] = {
    "520": "LumenTokens.Layout.heroHeightLg",
    "420": "LumenTokens.Layout.pinSheetWidth",
    "340": "LumenTokens.Layout.detailsHeroHeight",
    "320": "LumenTokens.Layout.playerMenuWidth",
    "240": "LumenTokens.Layout.detailsContentTop",
    "220": "LumenTokens.Layout.tileWidthMd",
    "96": "LumenTokens.Layout.channelRowHeight",
    "84": "LumenTokens.Layout.epgBlockHeight",
    "68": "LumenTokens.Layout.epgTimeColumnWidth",
    "56": "LumenTokens.Layout.playerControlSize",
    "54": "LumenTokens.Layout.channelLogoInner",
    "44": "LumenTokens.Layout.epgRowHeight",
    "42": "LumenTokens.Layout.avatarMd",
    "26": "LumenTokens.Layout.playerControlIcon",
    "22": "LumenTokens.Layout.iconSm",
    "90": "LumenTokens.Layout.qrSize",
}

SCHEME_MAP = [
    ("MaterialTheme.colorScheme.onSurfaceVariant", "LumenTokens.Color.textSecondary"),
    ("MaterialTheme.colorScheme.onBackground", "LumenTokens.Color.textPrimary"),
    ("MaterialTheme.colorScheme.onPrimary", "LumenTokens.Color.textPrimary"),
    ("MaterialTheme.colorScheme.onSurface", "LumenTokens.Color.textPrimary"),
    ("MaterialTheme.colorScheme.surfaceVariant", "LumenTokens.Color.surfaceHi"),
    ("MaterialTheme.colorScheme.background", "LumenTokens.Color.bg"),
    ("MaterialTheme.colorScheme.secondary", "LumenTokens.Color.surfaceHi"),
    ("MaterialTheme.colorScheme.primary", "LumenTokens.Color.brand"),
    ("MaterialTheme.colorScheme.surface", "LumenTokens.Color.surface"),
    ("MaterialTheme.colorScheme.outline", "LumenTokens.Color.border"),
    ("MaterialTheme.colorScheme.error", "LumenTokens.Color.danger"),
]

IMPORT_TOKENS = "import com.example.calmsource.core.ui.theme.LumenTokens"
IMPORT_TYPE = "import com.example.calmsource.core.ui.theme.LumenType"


def collect_files() -> list[str]:
    out: list[str] = []
    for base in ("app-mobile", "app-tv"):
        pat = os.path.join(ROOT, base, "src", "main", "java", "**", "*.kt")
        for path in glob.glob(pat, recursive=True):
            name = os.path.basename(path)
            if "Screen" in name or "Section" in name:
                out.append(path)
    return sorted(set(out))


def replace_dp_literals(text: str) -> str:
    merged = {**DP_TOKEN, **EXTRA_DP}
    for value, token in sorted(merged.items(), key=lambda x: len(x[0]), reverse=True):
        pattern = re.compile(rf"(?<![0-9]){re.escape(value)}\.dp(?![0-9])")
        text = pattern.sub(token, text)
    # Negative offsets
    text = text.replace("(-24).dp", "(-LumenTokens.Space.xxl)")
    return text


def apply_maps(text: str) -> str:
    for old, new in COLOR_MAP + SCHEME_MAP:
        text = text.replace(old, new)
    for pattern, repl in SHAPE_REPLACEMENTS + SHAPE_FIXES:
        text = pattern.sub(repl, text)
    return replace_dp_literals(text)


def ensure_imports(text: str) -> str:
    if "LumenTokens." in text and IMPORT_TOKENS not in text:
        text = re.sub(r"(package [^\n]+\n)", r"\1\n" + IMPORT_TOKENS + "\n", text, count=1)
    if "LumenType." in text and IMPORT_TYPE not in text:
        text = re.sub(r"(package [^\n]+\n)", r"\1\n" + IMPORT_TYPE + "\n", text, count=1)
    return text


def main() -> None:
    touched = 0
    for path in collect_files():
        with open(path, encoding="utf-8") as f:
            orig = f.read()
        updated = ensure_imports(apply_maps(orig))
        if updated != orig:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(updated)
            touched += 1
            print("updated", os.path.relpath(path, ROOT))
    print(f"done: {touched} files")


if __name__ == "__main__":
    main()
