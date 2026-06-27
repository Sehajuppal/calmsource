// Maps Stremio addon responses to our internal `Title` shape.
//
// Title ids are namespaced "{addonBaseUrl}::{type}::{stremioId}" so we can
// route meta/stream lookups back to the addon that produced the row without
// keeping a side-table.

import type { Title, TitleKind } from "../catalog";
import type { AddonMeta, AddonMetaPreview } from "./catalog-client";

const SEP = "::";

export function buildTitleId(addonBaseUrl: string, type: string, id: string): string {
  return `${addonBaseUrl}${SEP}${type}${SEP}${id}`;
}

export function parseTitleId(
  titleId: string,
): { baseUrl: string; type: string; id: string } | null {
  if (!titleId.includes(SEP)) return null;
  const idx = titleId.lastIndexOf(SEP);
  const head = titleId.slice(0, idx);
  const id = titleId.slice(idx + SEP.length);
  const idx2 = head.lastIndexOf(SEP);
  if (idx2 < 0) return null;
  const baseUrl = head.slice(0, idx2);
  const type = head.slice(idx2 + SEP.length);
  if (!baseUrl || !type || !id) return null;
  if (!/^https?:\/\//i.test(baseUrl)) return null;
  return { baseUrl, type, id };
}

function parseYear(s?: string): number {
  if (!s) return 0;
  const m = /(\d{4})/.exec(s);
  return m ? Number(m[1]) : 0;
}

function toKind(type: string): TitleKind {
  return type === "movie" ? "film" : "series";
}

function genresOf(x: { genres?: unknown }): string[] {
  return Array.isArray(x.genres) ? (x.genres.filter((g) => typeof g === "string") as string[]) : [];
}

export function metaPreviewToTitle(m: AddonMetaPreview, addonBaseUrl: string): Title {
  const poster = m.poster || m.background || "";
  const back = m.background || m.poster || "";
  return {
    id: buildTitleId(addonBaseUrl, m.type, m.id),
    name: m.name,
    year: parseYear(m.releaseInfo),
    rating: m.imdbRating ? `★ ${m.imdbRating}` : "",
    duration: "",
    genres: genresOf(m),
    description: m.description ?? "",
    poster,
    backdrop: back,
    tile: back || poster,
    logo: m.logo,
    kind: toKind(m.type),
  };
}

export function enrichTitleWithMeta(base: Title, meta: AddonMeta): Title {
  const seasonsSet = meta.videos
    ? new Set(
        meta.videos
          .map((v) => v.season)
          .filter((s): s is number => typeof s === "number" && s > 0),
      )
    : null;
  const seasons = seasonsSet && seasonsSet.size > 0 ? seasonsSet.size : base.seasons;
  const metaGenres = genresOf(meta);
  return {
    ...base,
    name: meta.name || base.name,
    description: meta.description || base.description,
    poster: meta.poster || base.poster,
    backdrop: meta.background || base.backdrop,
    tile: meta.background || meta.poster || base.tile,
    logo: meta.logo || base.logo,
    duration: meta.runtime || base.duration,
    genres: metaGenres.length ? metaGenres : base.genres,
    rating: meta.imdbRating ? `★ ${meta.imdbRating}` : base.rating,
    year: meta.releaseInfo ? parseYear(meta.releaseInfo) || base.year : base.year,
    seasons,
    episodes: meta.videos?.length ?? base.episodes,
  };
}
