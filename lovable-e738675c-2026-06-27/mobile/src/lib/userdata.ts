// Mirror of web src/lib/userdata.ts using zustand + AsyncStorage.
// Writes are fire-and-forget (Promise) — same call sites as web.
import { create } from "zustand";
import { nsKey, readJSON, writeJSON } from "./storage";

export type ContinueEntry = {
  id: string;
  position: number;
  duration: number;
  updatedAt: number;
};

export type Preferences = {
  autoplayNext: boolean;
  reduceMotion: boolean;
  defaultAudioLang: string;
  defaultSubLang: string;
  theme: "system" | "midnight" | "oled" | "graphite" | "light" | "high-contrast";
  matureLock: boolean;
  dataSaver: boolean;
  dyslexiaFont: boolean;
};

export type Rating = 1 | -1;

type UserDataState = {
  profileId: string | null;
  hydrated: boolean;
  watchlist: string[];
  continueWatching: Record<string, ContinueEntry>;
  history: string[];
  ratings: Record<string, Rating>;
  preferences: Preferences;
  hydrate: (profileId: string) => Promise<void>;
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
  // Fire-and-forget; surface errors in console only.
  writeJSON(nsKey(pid, key), value).catch((e) =>
    console.warn("[userdata] persist failed", key, e),
  );
}

export const useUserData = create<UserDataState>((set, get) => ({
  profileId: null,
  hydrated: false,
  watchlist: [],
  continueWatching: {},
  history: [],
  ratings: {},
  preferences: DEFAULT_PREFS,

  hydrate: async (profileId) => {
    const [watchlist, cw, history, ratings, prefs] = await Promise.all([
      readJSON<string[]>(nsKey(profileId, K.watchlist), []),
      readJSON<Record<string, ContinueEntry>>(nsKey(profileId, K.continue), {}),
      readJSON<string[]>(nsKey(profileId, K.history), []),
      readJSON<Record<string, Rating>>(nsKey(profileId, K.ratings), {}),
      readJSON<Partial<Preferences>>(nsKey(profileId, K.prefs), {}),
    ]);
    set({
      profileId,
      hydrated: true,
      watchlist,
      continueWatching: cw,
      history,
      ratings,
      preferences: { ...DEFAULT_PREFS, ...prefs },
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

export function selectContinue(state: UserDataState): ContinueEntry[] {
  return Object.values(state.continueWatching).sort(
    (a, b) => b.updatedAt - a.updatedAt,
  );
}
