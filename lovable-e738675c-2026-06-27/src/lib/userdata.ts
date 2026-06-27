import { create } from "zustand";
import { nsKey, readJSON, writeJSON } from "./storage";

export type ContinueEntry = {
  id: string;
  position: number; // seconds
  duration: number; // seconds (0 if unknown)
  updatedAt: number;
};

export type Preferences = {
  autoplayNext: boolean;
  reduceMotion: boolean;
  defaultAudioLang: string;
  defaultSubLang: string;
  theme: "system" | "midnight" | "oled" | "graphite" | "light" | "high-contrast";
  matureLock: boolean;
  /** Skip heavy backdrops, prefer tile-resolution art everywhere. */
  dataSaver: boolean;
  /** Toggle a dyslexia-friendly font stack via <html data-font="dyslexia">. */
  dyslexiaFont: boolean;
};

export type Rating = 1 | -1; // thumbs up / down

type UserDataState = {
  profileId: string | null;
  watchlist: string[];
  continueWatching: Record<string, ContinueEntry>;
  history: string[];
  ratings: Record<string, Rating>;
  preferences: Preferences;
  hydrate: (profileId: string) => void;
  toggleWatchlist: (id: string) => void;
  inWatchlist: (id: string) => boolean;
  recordProgress: (id: string, position: number, duration: number) => void;
  clearProgress: (id: string) => void;
  pushHistory: (id: string) => void;
  setRating: (id: string, r: Rating | 0) => void;
  setPreference: <K extends keyof Preferences>(k: K, v: Preferences[K]) => void;
};

const DEFAULT_PREFS: Preferences = {
  autoplayNext: true,
  reduceMotion: false,
  defaultAudioLang: "en",
  defaultSubLang: "off",
  theme: "midnight",
  matureLock: false,
  dataSaver: false,
  dyslexiaFont: false,
};

const K = {
  watchlist: "watchlist",
  continue: "continue",
  history: "history",
  ratings: "ratings",
  prefs: "preferences",
};

function persist(pid: string | null, key: string, value: unknown) {
  writeJSON(nsKey(pid, key), value);
}

export const useUserData = create<UserDataState>((set, get) => ({
  profileId: null,
  watchlist: [],
  continueWatching: {},
  history: [],
  ratings: {},
  preferences: DEFAULT_PREFS,

  hydrate: (profileId) => {
    set({
      profileId,
      watchlist: readJSON<string[]>(nsKey(profileId, K.watchlist), []),
      continueWatching: readJSON<Record<string, ContinueEntry>>(
        nsKey(profileId, K.continue),
        {},
      ),
      history: readJSON<string[]>(nsKey(profileId, K.history), []),
      ratings: readJSON<Record<string, Rating>>(nsKey(profileId, K.ratings), {}),
      preferences: {
        ...DEFAULT_PREFS,
        ...readJSON<Partial<Preferences>>(nsKey(profileId, K.prefs), {}),
      },
    });
  },

  toggleWatchlist: (id) => {
    const { watchlist, profileId } = get();
    const next = watchlist.includes(id)
      ? watchlist.filter((x) => x !== id)
      : [id, ...watchlist].slice(0, 500);
    set({ watchlist: next });
    persist(profileId, K.watchlist, next);
  },

  inWatchlist: (id) => get().watchlist.includes(id),

  recordProgress: (id, position, duration) => {
    if (!Number.isFinite(position) || position < 5) return;
    const { continueWatching, profileId } = get();
    // mark complete once within last 60s — drop from continue
    if (duration > 0 && position >= duration - 60) {
      if (!(id in continueWatching)) return;
      const next = { ...continueWatching };
      delete next[id];
      set({ continueWatching: next });
      persist(profileId, K.continue, next);
      return;
    }
    const next: Record<string, ContinueEntry> = {
      ...continueWatching,
      [id]: { id, position, duration, updatedAt: Date.now() },
    };
    set({ continueWatching: next });
    persist(profileId, K.continue, next);
  },

  clearProgress: (id) => {
    const { continueWatching, profileId } = get();
    if (!(id in continueWatching)) return;
    const next = { ...continueWatching };
    delete next[id];
    set({ continueWatching: next });
    persist(profileId, K.continue, next);
  },

  pushHistory: (id) => {
    const { history, profileId } = get();
    const next = [id, ...history.filter((x) => x !== id)].slice(0, 200);
    set({ history: next });
    persist(profileId, K.history, next);
  },

  setRating: (id, r) => {
    const { ratings, profileId } = get();
    const next = { ...ratings };
    if (r === 0) delete next[id];
    else next[id] = r;
    set({ ratings: next });
    persist(profileId, K.ratings, next);
  },

  setPreference: (k, v) => {
    const { preferences, profileId } = get();
    const next = { ...preferences, [k]: v };
    set({ preferences: next });
    persist(profileId, K.prefs, next);
  },
}));

/** Sorted continue-watching entries, newest first. Filters out stale items:
 *  - entries older than 60 days (likely abandoned)
 *  - entries whose progress has reached 95% (effectively finished but not
 *    yet GC'd by recordProgress's last-60s rule, e.g. user closed early). */
const STALE_MS = 60 * 24 * 60 * 60 * 1000;
export function selectContinue(state: UserDataState): ContinueEntry[] {
  const now = Date.now();
  return Object.values(state.continueWatching)
    .filter((e) => {
      if (now - e.updatedAt > STALE_MS) return false;
      if (e.duration > 0 && e.position / e.duration >= 0.95) return false;
      return true;
    })
    .sort((a, b) => b.updatedAt - a.updatedAt);
}

