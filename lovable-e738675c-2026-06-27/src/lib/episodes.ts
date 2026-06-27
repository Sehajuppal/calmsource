// Deterministic mock episode list for a series.
import type { Title } from "./catalog";

export type Episode = {
  id: string;
  number: number;
  season: number;
  title: string;
  description: string;
  duration: string;
  still: string;
};

const NAMES = [
  "Cold Open",
  "Signal Lost",
  "Paper Trail",
  "Low Hum",
  "Eastbound",
  "The Quiet Room",
  "Salt & Iron",
  "First Light",
  "Hairline",
  "After the Storm",
  "Long Way Home",
  "Static",
  "Stowaway",
  "Black Box",
  "Last Call",
];

function tag(seriesId: string, season: number, num: number) {
  return `${seriesId}-s${season}e${num}`;
}

export function getEpisodes(series: Title): Record<number, Episode[]> {
  const seasons = Math.max(1, series.seasons ?? 1);
  const perSeason = Math.max(3, Math.round((series.episodes ?? 8) / seasons));
  const out: Record<number, Episode[]> = {};
  let counter = 0;
  for (let s = 1; s <= seasons; s++) {
    out[s] = [];
    for (let n = 1; n <= perSeason; n++) {
      counter++;
      const name = NAMES[(series.id.length + counter) % NAMES.length];
      out[s].push({
        id: tag(series.id, s, n),
        number: n,
        season: s,
        title: name,
        description: `Episode ${n} of season ${s}. ${series.description}`,
        duration: `${38 + ((counter * 7) % 18)}m`,
        still: `https://picsum.photos/seed/${series.id}-${s}-${n}/640/360`,
      });
    }
  }
  return out;
}
