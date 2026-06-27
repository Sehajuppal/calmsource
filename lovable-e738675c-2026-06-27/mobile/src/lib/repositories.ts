// Mirror of web src/lib/repositories.ts — same interfaces, same defaults.
// Mobile screens import from here instead of catalog/iptv directly for values.
import {
  FEATURED,
  ROWS,
  FILMS,
  SERIES,
  FILM_ROWS,
  SERIES_ROWS,
  TOP_TEN,
  LEAVING_SOON,
  MOODS,
  titlesForMood,
  type Title,
} from "./catalog";
import { SAMPLE_CHANNELS, type IPTVChannel } from "./iptv";
import { streamUrlForTitle } from "./player-store";
import { providerRegistry } from "./providers/registry";

export type CatalogRow = { title: string; reason?: string; items: Title[] };

export interface CatalogRepository {
  getFeatured(): Title;
  getHomeRows(): CatalogRow[];
  getFilmRows(): CatalogRow[];
  getSeriesRows(): CatalogRow[];
  getFilms(): Title[];
  getSeries(): Title[];
  getTopTen(): Title[];
  getLeavingSoon(): Title[];
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

const allTitlesIndex = (() => {
  const m = new Map<string, Title>();
  for (const t of [FEATURED, ...FILMS, ...SERIES, ...TOP_TEN]) m.set(t.id, t);
  return m;
})();

export const catalogRepository: CatalogRepository = {
  getFeatured: () => FEATURED,
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
    subscribe: (l) => { listeners.add(l); return () => listeners.delete(l); },
  };
}

export const channelRepository: ChannelRepository =
  createChannelRepository(SAMPLE_CHANNELS);

export const playbackResolver: PlaybackResolver = {
  resolveTitle: (t) => providerRegistry.resolveUrl(streamUrlForTitle(t)),
  resolveLive: (c) => providerRegistry.resolveUrl(c.url),
};
