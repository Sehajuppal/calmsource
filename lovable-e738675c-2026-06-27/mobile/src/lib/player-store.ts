// Mirror of web src/lib/player-store.ts.
import { create } from "zustand";
import type { Title } from "./catalog";

export type PlayerSource =
  | { kind: "title"; title: Title; streamUrl: string }
  | { kind: "live"; channelId: string; name: string; streamUrl: string };

type PlayerState = {
  source: PlayerSource | null;
  mode: "full" | "mini" | "closed";
  open: (s: PlayerSource) => void;
  minimize: () => void;
  restore: () => void;
  close: () => void;
};

export const usePlayer = create<PlayerState>((set) => ({
  source: null,
  mode: "closed",
  open: (source) => set({ source, mode: "full" }),
  minimize: () => set((s) => (s.source ? { mode: "mini" } : s)),
  restore: () => set((s) => (s.source ? { mode: "full" } : s)),
  close: () => set({ source: null, mode: "closed" }),
}));

/** Public demo HLS (Mux Big Buck Bunny). Same fallback web uses. */
export const DEMO_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

export function streamUrlForTitle(title: Title): string {
  const maybe = (title as unknown as { streamUrl?: string }).streamUrl;
  return maybe && /^https?:\/\//.test(maybe) ? maybe : DEMO_HLS;
}
