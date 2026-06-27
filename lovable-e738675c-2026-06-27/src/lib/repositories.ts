// Repository interfaces — single seam between UI and data source.
//
// Today the implementations forward to the fake-data modules
// (`./catalog`, `./iptv`, `./player-store`). Tomorrow we can swap
// in a real backend (Stremio extensions, Xtream provider model,
// debrid resolver, …) by replacing ONLY this file. Screens never
// import the underlying data modules directly for *values* —
// type-only imports are fine.

import {
  FEATURED,
  ROWS,
  FILMS,
  SERIES,
  FILM_ROWS,
  SERIES_ROWS,
  FILMS_FEATURED,
  SERIES_FEATURED,
  TOP_TEN,
  LEAVING_SOON,
  MOODS,
  titlesForMood,
  type Title,
  type LeavingTitle,
} from "./catalog";
import { SAMPLE_CHANNELS, type IPTVChannel } from "./iptv";
import { streamUrlForTitle } from "./player-store";
import { providerRegistry } from "./providers/registry";

/* ─────────── Types ─────────── */

export type CatalogRow = { title: string; reason?: string; items: Title[] };

export interface CatalogRepository {
  getFeatured(): Title;
  getFilmsFeatured(): Title;
  getSeriesFeatured(): Title;
  getHomeRows(): CatalogRow[];
  getFilmRows(): CatalogRow[];
  getSeriesRows(): CatalogRow[];
  getFilms(): Title[];
  getSeries(): Title[];
  getTopTen(): Title[];
  getLeavingSoon(): LeavingTitle[];
  getMoods(): typeof MOODS;
  getTitlesForMood(moodId: string): Title[];
  getTitle(id: string): Title | null;
}

export interface ChannelRepository {
  getChannels(): IPTVChannel[];
  findChannel(id: string): IPTVChannel | null;
  setChannels(channels: IPTVChannel[]): void;
  subscribe(listener: (channels: IPTVChannel[]) => void): () => void;
}

export interface PlaybackResolver {
  resolveTitle(title: Title): string;
  resolveLive(channel: IPTVChannel): string;
}

/* ─────────── Default fake-data implementations ─────────── */

const allTitlesIndex = (() => {
  const m = new Map<string, Title>();
  for (const t of [FEATURED, FILMS_FEATURED, SERIES_FEATURED, ...FILMS, ...SERIES, ...TOP_TEN]) {
    m.set(t.id, t);
  }
  return m;
})();

export const catalogRepository: CatalogRepository = {
  getFeatured: () => FEATURED,
  getFilmsFeatured: () => FILMS_FEATURED,
  getSeriesFeatured: () => SERIES_FEATURED,
  getHomeRows: () => ROWS,
  getFilmRows: () => FILM_ROWS,
  getSeriesRows: () => SERIES_ROWS,
  getFilms: () => FILMS,
  getSeries: () => SERIES,
  getTopTen: () => TOP_TEN,
  getLeavingSoon: () => LEAVING_SOON,
  getMoods: () => MOODS,
  getTitlesForMood: (moodId) => {
    const m = MOODS.find((x) => x.id === moodId);
    return m ? titlesForMood(m) : [];
  },
  getTitle: (id) => allTitlesIndex.get(id) ?? null,
};

/** In-memory channel repository with pub/sub. Persistence is the caller's
 * concern (localStorage on web, AsyncStorage on mobile) so the repo stays
 * runtime-agnostic. Seeded with SAMPLE_CHANNELS. */
function createChannelRepository(seed: IPTVChannel[]): ChannelRepository {
  let channels = seed;
  const listeners = new Set<(c: IPTVChannel[]) => void>();
  return {
    getChannels: () => channels,
    findChannel: (id) => channels.find((c) => c.id === id) ?? null,
    setChannels: (next) => {
      channels = next;
      for (const l of listeners) l(channels);
    },
    subscribe: (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
  };
}

export const channelRepository: ChannelRepository =
  createChannelRepository(SAMPLE_CHANNELS);

export const playbackResolver: PlaybackResolver = {
  resolveTitle: (title) => providerRegistry.resolveUrl(streamUrlForTitle(title)),
  // Channel URLs may be plain HLS/MP4 or `provider://...` pseudo URLs.
  // The registry passes plain URLs through untouched.
  resolveLive: (channel) => providerRegistry.resolveUrl(channel.url),
};
