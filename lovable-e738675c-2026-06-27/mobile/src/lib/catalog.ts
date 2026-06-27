// Mirror of web src/lib/catalog.ts — keep field names and exports in sync.
export type TitleKind = "film" | "series";

export type Title = {
  id: string;
  name: string;
  year: number;
  rating: string;
  duration: string;
  genres: string[];
  description: string;
  poster: string;
  /** Large 16:9 backdrop — use for hero / modal / player only. */
  backdrop: string;
  /** Small 16:9 tile — use in rows. ~25× smaller payload than backdrop. */
  tile: string;
  logo?: string;
  kind: TitleKind;
  seasons?: number;
  episodes?: number;
};

const img = (seed: string, w: number, h: number) =>
  `https://picsum.photos/seed/${seed}/${w}/${h}`;

export const FEATURED: Title = {
  id: "skyline-protocol",
  name: "Skyline Protocol",
  year: 2026,
  rating: "TV-MA",
  duration: "1h 58m",
  genres: ["Thriller", "Sci-Fi", "Action"],
  description:
    "When a rogue satellite array falls silent, a disgraced operative is pulled out of exile to chase a signal that shouldn't exist — across cities that are no longer on any map.",
  poster: img("skyline-poster", 600, 900),
  backdrop: img("skyline-back", 1920, 1080),
  tile: img("skyline-back", 640, 360),
  kind: "series",
  seasons: 2,
  episodes: 16,
};

const make = (
  id: string,
  name: string,
  genres: string[],
  description: string,
  kind: TitleKind = "film",
  extras: Partial<Title> = {},
): Title => ({
  id,
  name,
  year: 2020 + (id.length % 6),
  rating: ["TV-MA", "TV-14", "PG-13", "R"][id.length % 4],
  duration:
    kind === "series"
      ? `${1 + (id.length % 4)} Season${1 + (id.length % 4) > 1 ? "s" : ""}`
      : `${1 + (id.length % 2)}h ${10 + (id.length % 50)}m`,
  genres,
  description,
  poster: img(`${id}-p`, 600, 900),
  backdrop: img(`${id}-b`, 1920, 1080),
  tile: img(`${id}-b`, 640, 360),
  kind,
  ...(kind === "series"
    ? { seasons: 1 + (id.length % 4), episodes: 6 + (id.length % 18) }
    : {}),
  ...extras,
});

/* ---------------- Films ---------------- */
export const FILMS: Title[] = [
  make("ironveil", "Ironveil", ["Action"], "A retired courier is forced back for one impossible delivery."),
  make("paper-kings", "Paper Kings", ["Crime"], "Two brothers build an empire on counterfeit certainty."),
  make("low-tide", "Low Tide", ["Thriller"], "When the ocean retreats, what it leaves behind starts to move."),
  make("vermillion", "Vermillion", ["Drama"], "A painter, a forger, and the canvas that wouldn't dry."),
  make("northbound", "Northbound", ["Adventure"], "Six strangers chase the last working compass on earth."),
  make("blue-hour", "Blue Hour", ["Romance"], "Two photographers, one rooftop, a city about to vanish."),
  make("ember-road", "Ember Road", ["Western"], "A sheriff, a fire, and a promise older than the town."),
  make("velvet-engine", "Velvet Engine", ["Crime"], "The fastest car in the city belongs to the quietest woman."),
  make("dovetail", "Dovetail", ["Drama"], "A carpenter inherits a house built entirely without nails."),
  make("paperback", "Paperback", ["Comedy"], "A failed novelist accidentally writes a bestseller about her landlord."),
  make("undertow", "Undertow", ["Thriller"], "Diving instructor. Missing tourists. Saltwater amnesia."),
  make("ascent", "Ascent", ["Documentary"], "Climbing the building that was never finished — from the inside."),
  make("citrine", "Citrine", ["Romance"], "He sells stolen gems. She authenticates them. They've never met."),
  make("redshift", "Redshift", ["Sci-Fi"], "The stars are moving. Only one observatory noticed."),
  make("the-cartographer", "The Cartographer", ["Drama"], "He drew maps for a country that no longer existed."),
];

export const FILMS_FEATURED: Title = FILMS[0];

export const FILM_ROWS: { title: string; items: Title[] }[] = [
  { title: "New on CalmSource", items: FILMS.slice(0, 6) },
  { title: "Award Winners", items: FILMS.slice(3, 9) },
  { title: "Action & Thrillers", items: FILMS.filter((f) => ["Action", "Thriller", "Crime"].some((g) => f.genres.includes(g))) },
  { title: "Drama & Romance", items: FILMS.filter((f) => ["Drama", "Romance"].some((g) => f.genres.includes(g))) },
  { title: "Sci-Fi & Adventure", items: FILMS.filter((f) => ["Sci-Fi", "Adventure", "Documentary"].some((g) => f.genres.includes(g))) },
];

/* ---------------- Series ---------------- */
export const SERIES: Title[] = [
  FEATURED,
  make("nightfall", "Nightfall", ["Drama"], "A small town keeps a dangerous secret buried beneath the lake.", "series"),
  make("orbit-9", "Orbit Nine", ["Sci-Fi"], "Nine astronauts. One ship. Zero memory of how they got there.", "series"),
  make("the-glasshouse", "The Glasshouse", ["Mystery"], "Every wall is transparent. Every secret, eventually, is not.", "series"),
  make("hush-county", "Hush County", ["Horror"], "Nobody speaks above a whisper. The reason is listening.", "series"),
  make("the-archivist", "The Archivist", ["Mystery"], "She catalogs forbidden books. One of them starts writing back.", "series"),
  make("solar-wake", "Solar Wake", ["Sci-Fi"], "A blackout lasted eleven minutes. Nobody remembers what they did.", "series"),
  make("static", "Static", ["Thriller"], "A late-night radio host receives calls from listeners not yet born.", "series"),
  make("loop", "Loop", ["Sci-Fi"], "Every Tuesday, the same minute, with a different witness.", "series"),
  make("midnight-bus", "Midnight Bus", ["Drama"], "Route 7 only runs after the city is asleep. It always has.", "series"),
  make("harbor-lights", "Harbor Lights", ["Crime"], "A coastal detective hunts a killer who only strikes at high tide.", "series"),
  make("after-eden", "After Eden", ["Sci-Fi"], "The last greenhouse on Earth has a gardener with a secret.", "series"),
  make("the-quiet-set", "The Quiet Set", ["Mystery"], "Behind the scenes of a hit show, the crew keeps disappearing.", "series"),
  make("kestrel", "Kestrel", ["Action"], "A spy with no memory hunts the agency that built her.", "series"),
];

export const SERIES_FEATURED: Title = FEATURED;

export const SERIES_ROWS: { title: string; items: Title[] }[] = [
  { title: "CalmSource Originals", items: SERIES.slice(0, 6) },
  { title: "Binge-worthy", items: SERIES.slice(3, 10) },
  { title: "Mystery & Thriller", items: SERIES.filter((s) => ["Mystery", "Thriller", "Horror", "Crime"].some((g) => s.genres.includes(g))) },
  { title: "Sci-Fi Universe", items: SERIES.filter((s) => s.genres.includes("Sci-Fi")) },
  { title: "Drama Spotlight", items: SERIES.filter((s) => ["Drama", "Action"].some((g) => s.genres.includes(g))) },
];

export type EditorialRow = { title: string; reason?: string; items: Title[] };

export const TOP_TEN: Title[] = [
  ...SERIES.slice(1, 5),
  ...FILMS.slice(0, 4),
  SERIES[6],
  FILMS[7],
];

export type LeavingTitle = Title & { leavesInDays: number };

export const LEAVING_SOON: LeavingTitle[] = [
  { ...FILMS[5], leavesInDays: 3 },
  { ...SERIES[8], leavesInDays: 5 },
  { ...FILMS[10], leavesInDays: 6 },
  { ...SERIES[11], leavesInDays: 9 },
  { ...FILMS[12], leavesInDays: 12 },
  { ...FILMS[2], leavesInDays: 14 },
];

export type Mood = {
  id: string;
  label: string;
  blurb: string;
  swatch: string;
  tint: string;
  match: (t: Title) => boolean;
};

export const MOODS: Mood[] = [
  {
    id: "cozy",
    label: "Cozy night in",
    blurb: "Warm, slow-burn, easy on the heart.",
    swatch: "linear-gradient(135deg,#ffb37a,#c5556d)",
    tint: "#e2826f",
    match: (t) => t.genres.some((g) => ["Romance", "Drama", "Comedy"].includes(g)),
  },
  {
    id: "mind",
    label: "Mind-bending",
    blurb: "Plots that turn inside out.",
    swatch: "linear-gradient(135deg,#7c5cff,#1ea7c5)",
    tint: "#6e8bff",
    match: (t) => t.genres.some((g) => ["Sci-Fi", "Mystery"].includes(g)),
  },
  {
    id: "edge",
    label: "Edge-of-seat",
    blurb: "Pulse-raising, breath-holding.",
    swatch: "linear-gradient(135deg,#ff5b6b,#702830)",
    tint: "#ff5b6b",
    match: (t) => t.genres.some((g) => ["Thriller", "Horror", "Action"].includes(g)),
  },
  {
    id: "feel",
    label: "Feel-good",
    blurb: "Smile-on-your-face material.",
    swatch: "linear-gradient(135deg,#ffd166,#06d6a0)",
    tint: "#7fd99a",
    match: (t) => t.genres.some((g) => ["Comedy", "Adventure", "Romance"].includes(g)),
  },
  {
    id: "gems",
    label: "Hidden gems",
    blurb: "Loved by critics, missed by most.",
    swatch: "linear-gradient(135deg,#9ad7ff,#4040ff)",
    tint: "#7aa8ff",
    match: (t) => ["Documentary", "Western", "Drama"].some((g) => t.genres.includes(g)),
  },
];

export function titlesForMood(mood: Mood, pool: Title[] = [...FILMS, ...SERIES]): Title[] {
  return pool.filter(mood.match).slice(0, 12);
}

export const ROWS: EditorialRow[] = [
  {
    title: "Because you binged Skyline Protocol",
    reason: "Pulled from your watch history — more like it.",
    items: [...SERIES.slice(2, 6), ...FILMS.slice(2, 6)],
  },
  {
    title: "CalmSource Originals",
    reason: "Made for CalmSource. Streamed nowhere else.",
    items: SERIES.slice(0, 6),
  },
  {
    title: "Hidden gems",
    reason: "Critically loved, under-the-radar.",
    items: [...FILMS.slice(6, 11), ...SERIES.slice(9, 13)],
  },
  {
    title: "New this week",
    reason: "Fresh from the editors — premiered in the last 7 days.",
    items: [...FILMS.slice(9, 14), ...SERIES.slice(8, 12)],
  },
  {
    title: "Under 90 minutes",
    reason: "Short and sharp — pick one and you're home by 10.",
    items: FILMS.slice(0, 8),
  },
];
