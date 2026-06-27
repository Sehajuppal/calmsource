// Recommendations + cast helpers — deterministic, derived from catalog & user data.
// No network, no randomness; same inputs always produce the same output so SSR
// and client renders agree.
import { FEATURED, FILMS, SERIES, type Title } from "./catalog";

const ALL_TITLES: Title[] = [FEATURED, ...FILMS, ...SERIES].filter(
  (t, i, a) => a.findIndex((x) => x.id === t.id) === i,
);

/* ---------------- Cast (fake but stable) ---------------- */

const ACTOR_POOL = [
  "Mara Solano", "Idris Kade", "Noa Whitfield", "Theo Marchetti", "Ines Park",
  "Caleb Onyx", "Yui Tanaka", "Renata Aksoy", "Dimitri Vossen", "Sage Okafor",
  "Aurora Liang", "Mateo Reyes", "Hana Bergstrom", "Jonas Vela", "Imani Cross",
  "Soraya Halim", "Felix Knight", "Marcelo Drake", "Naomi Sato", "Erik Vance",
  "Lila Cortez", "Quinn Asari", "Bea Lindqvist", "Roman Tate",
];

const ROLE_POOL = [
  "Lead", "Co-lead", "Antagonist", "Mentor", "Confidant",
  "Rival", "Narrator", "Recurring", "Detective", "Strategist",
];

// Tiny deterministic hash → pulls a stable slice from ACTOR_POOL per title.
function hash(s: string): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}

export type CastMember = { name: string; role: string };

export function castFor(title: Title, count = 5): CastMember[] {
  const h = hash(title.id);
  const out: CastMember[] = [];
  for (let i = 0; i < count; i++) {
    const a = ACTOR_POOL[(h + i * 31) % ACTOR_POOL.length];
    const r = ROLE_POOL[(h + i * 17) % ROLE_POOL.length];
    out.push({ name: a, role: r });
  }
  return out;
}

/* ---------------- Similar titles ---------------- */

function similarityScore(a: Title, b: Title): number {
  if (a.id === b.id) return -1;
  let s = 0;
  for (const g of a.genres) if (b.genres.includes(g)) s += 3;
  if (a.kind === b.kind) s += 1;
  s -= Math.min(2, Math.abs(a.year - b.year) / 4);
  return s;
}

export function similarTo(title: Title, limit = 10): Title[] {
  return ALL_TITLES
    .map((t) => ({ t, s: similarityScore(title, t) }))
    .filter((x) => x.s > 0)
    .sort((a, b) => b.s - a.s)
    .slice(0, limit)
    .map((x) => x.t);
}

/* ---------------- Personalized picks ---------------- */

export type PicksInput = {
  history: string[];
  watchlist: string[];
  ratings: Record<string, 1 | -1>;
  continueIds: string[];
};

export function personalizedPicks(input: PicksInput, limit = 12): Title[] {
  const signal: string[] = [
    ...input.continueIds,
    ...input.history,
    ...input.watchlist,
    ...Object.entries(input.ratings).filter(([, v]) => v === 1).map(([k]) => k),
  ];
  if (signal.length === 0) return [];

  // Build a genre-affinity vector from signal titles.
  const affinity = new Map<string, number>();
  const seed: Title[] = [];
  for (const id of signal) {
    const t = ALL_TITLES.find((x) => x.id === id);
    if (!t) continue;
    seed.push(t);
    for (const g of t.genres) affinity.set(g, (affinity.get(g) ?? 0) + 1);
  }
  if (affinity.size === 0) return [];

  // Negative signal: thumbs-down genres get downweighted.
  const downIds = Object.entries(input.ratings).filter(([, v]) => v === -1).map(([k]) => k);
  for (const id of downIds) {
    const t = ALL_TITLES.find((x) => x.id === id);
    if (!t) continue;
    for (const g of t.genres) affinity.set(g, (affinity.get(g) ?? 0) - 1.5);
  }

  const seen = new Set(signal);

  return ALL_TITLES
    .filter((t) => !seen.has(t.id))
    .map((t) => {
      const score = t.genres.reduce((sum, g) => sum + (affinity.get(g) ?? 0), 0);
      return { t, score };
    })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((x) => x.t);
}
