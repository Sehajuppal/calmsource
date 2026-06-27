// Picker overlay state. Keeps the resolve-then-play handshake out of
// every Play button: any caller hits `requestPlay(title)`, the picker
// resolves, and either auto-plays or opens the dialog.

import { create } from "zustand";
import type { Title } from "./catalog";
import { resolveStreamsForTitle, type ResolvedStream } from "./extensions/stream-resolver";
import { parseTitleId } from "./extensions/adapter";
import { usePlayer, streamUrlForTitle } from "./player-store";

type PickerStatus = "idle" | "loading" | "ready" | "error" | "empty";

interface PickerState {
  open: boolean;
  status: PickerStatus;
  title: Title | null;
  streams: ResolvedStream[];
  error?: string;
  queried: number;
  succeeded: number;
  /** Resolve streams for a title and either auto-play or open picker. */
  requestPlay: (title: Title) => Promise<void>;
  pick: (stream: ResolvedStream) => void;
  close: () => void;
}

let activeCtrl: AbortController | null = null;

export const useStreamPicker = create<PickerState>((set, get) => ({
  open: false,
  status: "idle",
  title: null,
  streams: [],
  queried: 0,
  succeeded: 0,
  requestPlay: async (title) => {
    // Non-extension titles fall straight through to the existing demo URL.
    // We don't surface an empty picker in that case — keeps the click-to-play
    // feel for the seeded catalog while extensions own the real path.
    const parsed = parseTitleId(title.id);
    if (!parsed) {
      usePlayer.getState().open({ kind: "title", title, streamUrl: streamUrlForTitle(title) });
      return;
    }

    activeCtrl?.abort();
    const ctrl = new AbortController();
    activeCtrl = ctrl;

    set({ open: true, status: "loading", title, streams: [], error: undefined, queried: 0, succeeded: 0 });

    try {
      const res = await resolveStreamsForTitle(title, ctrl.signal);
      if (ctrl.signal.aborted) return;
      if (res.queried === 0) {
        set({ status: "empty", streams: [], queried: 0, succeeded: 0 });
        return;
      }
      if (res.streams.length === 0) {
        const firstErr = Object.values(res.errors)[0];
        set({
          status: res.succeeded === 0 ? "error" : "empty",
          streams: [],
          queried: res.queried,
          succeeded: res.succeeded,
          error: firstErr,
        });
        return;
      }

      // Auto-play if the only playable option is unambiguous AND there's
      // exactly one entry total — otherwise let the user choose.
      const playables = res.streams.filter((s) => s.playable);
      if (res.streams.length === 1 && playables.length === 1) {
        get().pick(playables[0]);
        return;
      }

      set({
        status: "ready",
        streams: res.streams,
        queried: res.queried,
        succeeded: res.succeeded,
      });
    } catch (e) {
      if (ctrl.signal.aborted) return;
      set({ status: "error", error: e instanceof Error ? e.message : "Failed to resolve streams" });
    }
  },
  pick: (stream) => {
    const t = get().title;
    if (!t || !stream.playable || !stream.url) return;
    usePlayer.getState().open({ kind: "title", title: t, streamUrl: stream.url });
    set({ open: false, status: "idle", title: null, streams: [] });
  },
  close: () => {
    activeCtrl?.abort();
    activeCtrl = null;
    set({ open: false, status: "idle", title: null, streams: [], error: undefined });
  },
}));
