// Visual tokens — kept in lockstep with web src/styles.css :root vars.
// Web cobalt brand: oklch(0.62 0.20 264) ≈ #2E5BFF
export const colors = {
  bg: "#0d0d14",            // matches web --background midnight
  card: "rgba(255,255,255,0.04)",
  border: "rgba(255,255,255,0.10)",
  borderStrong: "rgba(255,255,255,0.18)",
  text: "#ffffff",
  textMuted: "rgba(255,255,255,0.6)",
  textDim: "rgba(255,255,255,0.45)",
  /** Electric Cobalt — the iconic brand color shared with web. */
  accent: "#2E5BFF",
  accentForeground: "#ffffff",
  accentGlow: "rgba(46,91,255,0.45)",
};

export const TILE_W = 150;
export const TILE_H = 220;
export const LANDSCAPE_W = 260;
export const LANDSCAPE_H = 150;

// Typography scale — mirrors web display tracking.
export const type = {
  display: { size: 40, weight: "700" as const, letterSpacing: -0.8 },
  h1: { size: 34, weight: "700" as const, letterSpacing: -0.6 },
  h2: { size: 22, weight: "700" as const, letterSpacing: -0.3 },
  rowTitle: { size: 17, weight: "700" as const, letterSpacing: -0.2 },
  body: { size: 14, weight: "400" as const, letterSpacing: 0 },
  meta: { size: 12, weight: "500" as const, letterSpacing: 0.2 },
  eyebrow: { size: 11, weight: "700" as const, letterSpacing: 1.5 },
};
