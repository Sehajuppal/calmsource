#!/usr/bin/env node
/**
 * Lumen token codegen — reads tokens/lumen.json and emits Kotlin Compose sources.
 * Invoked by: npm run tokens:build (from tokens/ or repo root via package.json script).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const JSON_PATH = path.join(__dirname, 'lumen.json');
const OUT_TOKENS = path.join(
  ROOT,
  'core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenTokens.Generated.kt',
);
const OUT_TYPE = path.join(
  ROOT,
  'core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenTypeScale.Generated.kt',
);

const tokens = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));

function px(value) {
  if (typeof value === 'number') return value;
  const m = String(value).match(/^(-?[\d.]+)px$/);
  if (!m) throw new Error(`Not px: ${value}`);
  return Number(m[1]);
}

function ms(value) {
  const m = String(value).match(/^([\d.]+)ms$/);
  return m ? Number(m[1]) : Number(value);
}

function parseColor(value) {
  const v = String(value).trim();
  if (v.startsWith('#')) {
    const hex = v.slice(1);
    if (hex.length === 6) return `Color(0xFF${hex.toUpperCase()})`;
    if (hex.length === 8) return `Color(0x${hex.toUpperCase()})`;
  }
  const rgba = v.match(/^rgba?\(([^)]+)\)$/i);
  if (rgba) {
    const parts = rgba[1].split(',').map((s) => s.trim());
    const r = Math.round(Number(parts[0]));
    const g = Math.round(Number(parts[1]));
    const b = Math.round(Number(parts[2]));
    const a = parts.length > 3 ? Math.round(Number(parts[3]) * 255) : 255;
    const hex = [a, r, g, b].map((n) => n.toString(16).padStart(2, '0')).join('').toUpperCase();
    return `Color(0x${hex})`;
  }
  throw new Error(`Unsupported color: ${v}`);
}

function parseBezier(value) {
  const m = String(value).match(/cubic-bezier\(([^)]+)\)/i);
  if (!m) throw new Error(`Not bezier: ${value}`);
  const [x1, y1, x2, y2] = m[1].split(',').map((s) => s.trim());
  return `CubicBezierEasing(${x1}f, ${y1}f, ${x2}f, ${y2}f)`;
}

function aspectRatio(str) {
  const [w, h] = String(str).split(':').map(Number);
  return `${w}f / ${h}f`;
}

function val(token) {
  return token?.$value ?? token;
}

const c = tokens.color;
const focusAlpha = tokens.focusRing.alpha.$value ?? tokens.focusRing.alpha;
const focusHaloBase = parseColor(val(c.focus.halo));
const focusHalo = focusHaloBase.replace('Color(0xFF', 'Color(0x99'); // 60% alpha on #3D6BFF

const space = tokens.space;
const radius = tokens.radius;
const motion = tokens.motion;

let tokensKt = `// AUTO-GENERATED — DO NOT EDIT. Source: tokens/lumen.json
// Regenerate: npm run tokens:build
package com.example.calmsource.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Generated Lumen token values from tokens/lumen.json (web source of truth).
 * Semantic aliases and screen extensions live in LumenTokens.kt.
 */
internal object LumenTokensGenerated {

    object Color {
        val bgBase = ${parseColor(val(c.bg.base))}
        val bgOled = ${parseColor(val(c.bg.oled))}
        val bgGraphite = ${parseColor(val(c.bg.graphite))}
        val surfaceCard = ${parseColor(val(c.surface.card))}
        val surfaceMuted = ${parseColor(val(c.surface.muted))}
        val surfaceAccent = ${parseColor(val(c.surface.accent))}
        val surfaceGlass = ${parseColor(val(c.surface.glass))}
        val surfaceGlassStrong = ${parseColor(val(c.surface.glassStrong))}
        val textPrimary = ${parseColor(val(c.text.primary))}
        val textSecondary = ${parseColor(val(c.text.secondary))}
        val textMuted = ${parseColor(val(c.text.muted))}
        val textOnBrand = ${parseColor(val(c.text.onBrand))}
        val brandBase = ${parseColor(val(c.brand.base))}
        val brandGlow = ${parseColor(val(c.brand.glow))}
        val brandForeground = ${parseColor(val(c.brand.foreground))}
        val borderSubtle = ${parseColor(val(c.border.subtle))}
        val borderDefault = ${parseColor(val(c.border.default))}
        val borderStrong = ${parseColor(val(c.border.strong))}
        val focusHalo = ${focusHalo}
        val danger = ${parseColor(val(c.feedback.danger))}
        val success = ${parseColor(val(c.feedback.success))}
        val warning = ${parseColor(val(c.feedback.warning))}
    }

    object Radius {
        val xs: Dp = ${px(val(radius.xs))}.dp
        val sm: Dp = ${px(val(radius.sm))}.dp
        val md: Dp = ${px(val(radius.md))}.dp
        val lg: Dp = ${px(val(radius.lg))}.dp
        val xl: Dp = ${px(val(radius.xl))}.dp
        val xxl: Dp = ${px(val(radius.xxl))}.dp
        val pill: Dp = ${px(val(radius.pill))}.dp
    }

    object Shape {
        val xs = RoundedCornerShape(Radius.xs)
        val sm = RoundedCornerShape(Radius.sm)
        val md = RoundedCornerShape(Radius.md)
        val lg = RoundedCornerShape(Radius.lg)
        val xl = RoundedCornerShape(Radius.xl)
        val xxl = RoundedCornerShape(Radius.xxl)
        val pill = RoundedCornerShape(Radius.pill)
        val poster = lg
        val hero = xxl
        val chip = pill
        val button = md
    }

    object Space {
        val s0: Dp = ${px(val(space['0']))}.dp
        val s1: Dp = ${px(val(space['1']))}.dp
        val s2: Dp = ${px(val(space['2']))}.dp
        val s3: Dp = ${px(val(space['3']))}.dp
        val s4: Dp = ${px(val(space['4']))}.dp
        val s5: Dp = ${px(val(space['5']))}.dp
        val s6: Dp = ${px(val(space['6']))}.dp
        val s7: Dp = ${px(val(space['7']))}.dp
        val s8: Dp = ${px(val(space['8']))}.dp
        val s9: Dp = ${px(val(space['9']))}.dp
        val s10: Dp = ${px(val(space['10']))}.dp
        val s11: Dp = ${px(val(space['11']))}.dp
        val s12: Dp = ${px(val(space['12']))}.dp
        val xxs: Dp = s1
        val xs: Dp = s2
        val sm: Dp = s3
        val sm2: Dp = s4
        val md: Dp = s5
        val lg: Dp = s6
        val xl: Dp = s7
        val xxl: Dp = s8
        val xxxl: Dp = s9
        val xxxxl: Dp = s10
        val xxxxxl: Dp = s11
        val xxxxxxl: Dp = s12
        val rowGutterMobile: Dp = ${px(val(tokens.rowGutter.mobile))}.dp
        val rowGutterTv: Dp = ${px(val(tokens.rowGutter.tv))}.dp
        val sectionGapMobile: Dp = ${px(val(tokens.sectionGap.mobile))}.dp
        val sectionGapTv: Dp = ${px(val(tokens.sectionGap.tv))}.dp
        val sidePaddingMobile: Dp = ${px(val(tokens.sidePadding.mobile))}.dp
        val sidePaddingTv: Dp = ${px(val(tokens.sidePadding.tv))}.dp
        fun rowGutter(isTv: Boolean): Dp = if (isTv) rowGutterTv else rowGutterMobile
        fun sectionVertical(isTv: Boolean): Dp = if (isTv) sectionGapTv else sectionGapMobile
    }

    object Motion {
        const val fastMs: Int = ${ms(val(motion.duration.fast))}
        const val standardMs: Int = ${ms(val(motion.duration.standard))}
        const val baseMs: Int = ${ms(val(motion.duration.base))}
        const val emphasizedMs: Int = ${ms(val(motion.duration.emphasized))}
        const val focusMs: Int = ${ms(val(motion.duration.focus))}
        const val tileMs: Int = ${ms(val(motion.duration.tile))}
        const val cinematicMs: Int = ${ms(val(motion.duration.cinematic))}
        const val springStiffness: Float = ${motion.spring.emphasized.stiffness}f
        const val springDamping: Float = ${motion.spring.emphasized.dampingRatio}f
        const val focusScale: Float = ${motion.focus.scale.$value ?? motion.focus.scale}f
        const val hoverScale: Float = ${motion.focus.hoverScale.$value ?? motion.focus.hoverScale}f
    }

    object FocusRing {
        val stroke: Dp = ${px(val(tokens.focusRing.stroke))}.dp
        val outerGlow: Dp = ${px(val(tokens.focusRing.outerGlow))}.dp
        const val alpha: Float = ${focusAlpha}f
    }

    object AspectRatio {
        const val heroMobile: Float = ${aspectRatio(val(tokens.hero.aspect.mobile))}
        const val heroTv: Float = ${aspectRatio(val(tokens.hero.aspect.tv))}
        const val posterPortraitMobile: Float = ${px(val(tokens.tile.posterPortrait.mobile.width))}f / ${px(val(tokens.tile.posterPortrait.mobile.height))}f
        const val posterPortraitTv: Float = ${px(val(tokens.tile.posterPortrait.tv.width))}f / ${px(val(tokens.tile.posterPortrait.tv.height))}f
        const val landscapeMobile: Float = ${px(val(tokens.tile.landscape.mobile.width))}f / ${px(val(tokens.tile.landscape.mobile.height))}f
        const val landscapeTv: Float = ${px(val(tokens.tile.landscape.tv.width))}f / ${px(val(tokens.tile.landscape.tv.height))}f
    }

    object Hero {
        val minHeightPhone: Dp = ${px(val(tokens.hero.minHeight.phone))}.dp
        val minHeightPhoneDetails: Dp = ${px(val(tokens.hero.minHeight.phoneDetails))}.dp
        val minHeightTv: Dp = ${px(val(tokens.hero.minHeight.tv))}.dp
        val minHeightTvDetails: Dp = ${px(val(tokens.hero.minHeight.tvDetails))}.dp
    }
}
`;

const tvMult = tokens.type.$extensions.tv.multiplier;
const typeStyles = ['display', 'h1', 'h2', 'title', 'rowTitle', 'body', 'caption', 'meta', 'eyebrow'];

function typeEntry(name, isTv) {
  const t = tokens.type[name];
  const mult = isTv ? tvMult : 1;
  const fs = px(t.fontSize) * mult;
  const lh = px(t.lineHeight) * mult;
  const lsEm = t.letterSpacingEm;
  const lsSp = lsEm * fs;
  const weight = t.weight;
  const family = t.family === 'display' ? 'InterTightFamily' : 'InterFamily';
  const kdoc = `// web: ${t.fontSize}/${t.lineHeight}/${lsEm}em`;
  return `        ${kdoc}
        ${name} = TextStyle(
            fontFamily = ${family},
            fontWeight = FontWeight(${weight}),
            fontSize = ${fs}.sp,
            lineHeight = ${lh}.sp,
            letterSpacing = ${lsSp}.sp,
        ),`;
}

let motionKt = `// AUTO-GENERATED — DO NOT EDIT. Source: tokens/lumen.json
package com.example.calmsource.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object LumenMotionTokens {
    val standardEasing = FastOutSlowInEasing
    val focusEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val appleOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val tileEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    fun focusTween() = tween<Float>(durationMillis = LumenTokensGenerated.Motion.focusMs, easing = focusEasing)
    fun emphasizedSpring() = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = LumenTokensGenerated.Motion.springStiffness)
}
`;

const OUT_MOTION = path.join(
  ROOT,
  'core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenMotion.Generated.kt',
);

let typeKt = `// AUTO-GENERATED — DO NOT EDIT. Source: tokens/lumen.json
package com.example.calmsource.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class GeneratedLumenTypeScale(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val title: TextStyle,
    val rowTitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val meta: TextStyle,
    val eyebrow: TextStyle,
)

fun generatedLumenTypeScale(isTv: Boolean): GeneratedLumenTypeScale {
    return if (isTv) GeneratedLumenTypeScale(
${typeStyles.map((n) => typeEntry(n, true)).join('\n')}
    ) else GeneratedLumenTypeScale(
${typeStyles.map((n) => typeEntry(n, false)).join('\n')}
    )
}
`;

fs.mkdirSync(path.dirname(OUT_TOKENS), { recursive: true });
fs.writeFileSync(OUT_TOKENS, tokensKt);
fs.writeFileSync(OUT_TYPE, typeKt);
fs.writeFileSync(OUT_MOTION, motionKt);
console.log('Wrote', path.relative(ROOT, OUT_TOKENS));
console.log('Wrote', path.relative(ROOT, OUT_TYPE));
console.log('Wrote', path.relative(ROOT, OUT_MOTION));
