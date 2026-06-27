// EPG facade. Prefers real XMLTV data (epg-source) when loaded, falls
// back to a deterministic mock so the UI always has something to show.

import type { IPTVChannel } from "./iptv";
import { programmeAt, programmeAfter } from "./epg-source";

export type Program = {
  title: string;
  start: number; // epoch ms
  end: number;
  desc?: string;
};

const TITLES = [
  "Morning Wire", "World Today", "The Matinee", "Field Notes",
  "Long Form", "After Hours", "Late Cut", "Encore",
  "The Briefing", "Off the Record", "Tonight", "Sundown",
];

function hash(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = (h * 16777619) >>> 0;
  }
  return h;
}

function slotMs(id: string): number {
  const r = hash(id) % 4;
  return (45 + r * 15) * 60_000;
}

function mockNowNext(id: string, ref: number): { now: Program; next: Program } {
  const slot = slotMs(id);
  const seed = hash(id);
  const anchor = Math.floor(ref / slot) * slot;
  const idx = Math.floor(ref / slot);
  const pick = (k: number) => TITLES[(seed + k) % TITLES.length];
  return {
    now: { title: pick(idx), start: anchor, end: anchor + slot },
    next: { title: pick(idx + 1), start: anchor + slot, end: anchor + slot * 2 },
  };
}

/** Resolve an EPG lookup key from a channel: tvgId, else id, else name. */
function epgKey(c: Pick<IPTVChannel, "id" | "tvgId" | "name">): string {
  return (c.tvgId || c.id || c.name || "").toLowerCase();
}

export function nowNext(
  channel: Pick<IPTVChannel, "id" | "tvgId" | "name"> | string,
  ref: number = Date.now(),
): { now: Program; next: Program } {
  if (typeof channel === "string") return mockNowNext(channel, ref);
  const key = epgKey(channel);
  const real = key ? programmeAt(key, ref) : null;
  if (real) {
    const after = programmeAfter(key, ref);
    return {
      now: { title: real.title, start: real.start, end: real.end, desc: real.desc },
      next: after
        ? { title: after.title, start: after.start, end: after.end, desc: after.desc }
        : mockNowNext(channel.id, real.end).next,
    };
  }
  return mockNowNext(channel.id, ref);
}

export function fmtTime(ms: number): string {
  const d = new Date(ms);
  return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}
