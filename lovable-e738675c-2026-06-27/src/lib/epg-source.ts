// Real EPG (XMLTV) source. Lightweight, dependency-free, runtime-agnostic
// (no DOMParser) so the same module works on web and React Native.
//
// Storage shape: programmes indexed by lowercased XMLTV channel id, kept
// sorted by start time. Lookups via binary search.

export type EpgProgramme = {
  start: number; // epoch ms
  end: number;
  title: string;
  desc?: string;
  channelKey: string; // lowercased xmltv channel id
};

const schedule = new Map<string, EpgProgramme[]>();
const listeners = new Set<() => void>();
let lastLoadedAt = 0;

function emit() {
  for (const l of listeners) l();
}

/** "20240615123000 +0200" → epoch ms. Returns NaN on failure. */
export function parseXmltvTime(raw: string): number {
  const m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})\s*([+-]\d{2})(\d{2})?$/.exec(raw.trim());
  if (!m) {
    // Fallback: ISO date or millis
    const t = Date.parse(raw);
    return Number.isFinite(t) ? t : NaN;
  }
  const [, y, mo, d, h, mi, s, oh, om = "00"] = m;
  const iso = `${y}-${mo}-${d}T${h}:${mi}:${s}${oh}:${om}`;
  return Date.parse(iso);
}

function decodeEntities(s: string): string {
  return s
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, "&");
}

function stripCdata(s: string): string {
  return s.replace(/^<!\[CDATA\[([\s\S]*?)\]\]>$/, "$1");
}

const PROG_RE = /<programme\b([^>]*)>([\s\S]*?)<\/programme>/gi;
const ATTR_RE = /(\w[\w:-]*)\s*=\s*"([^"]*)"/g;
const TITLE_RE = /<title\b[^>]*>([\s\S]*?)<\/title>/i;
const DESC_RE = /<desc\b[^>]*>([\s\S]*?)<\/desc>/i;

/** Parse an XMLTV document and merge programmes into the schedule. */
export function loadXmltvFromText(xml: string): number {
  if (!xml || xml.length < 32) return 0;
  const merged = new Map<string, EpgProgramme[]>();
  let count = 0;
  PROG_RE.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = PROG_RE.exec(xml))) {
    const attrs: Record<string, string> = {};
    ATTR_RE.lastIndex = 0;
    let a: RegExpExecArray | null;
    while ((a = ATTR_RE.exec(m[1]))) attrs[a[1].toLowerCase()] = a[2];
    const start = parseXmltvTime(attrs["start"] ?? "");
    const end = parseXmltvTime(attrs["stop"] ?? "");
    const channel = (attrs["channel"] ?? "").trim().toLowerCase();
    if (!channel || !Number.isFinite(start) || !Number.isFinite(end) || end <= start) continue;
    const body = m[2];
    const tMatch = TITLE_RE.exec(body);
    const dMatch = DESC_RE.exec(body);
    const title = tMatch ? decodeEntities(stripCdata(tMatch[1].trim())) : "";
    if (!title) continue;
    const desc = dMatch ? decodeEntities(stripCdata(dMatch[1].trim())) : undefined;
    const prog: EpgProgramme = { start, end, title, desc, channelKey: channel };
    const arr = merged.get(channel) ?? [];
    arr.push(prog);
    merged.set(channel, arr);
    count++;
  }
  for (const [key, arr] of merged) {
    arr.sort((x, y) => x.start - y.start);
    schedule.set(key, arr);
  }
  if (count > 0) {
    lastLoadedAt = Date.now();
    emit();
  }
  return count;
}

export async function loadXmltvFromUrl(url: string, timeoutMs = 30_000): Promise<number> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const text = await res.text();
    return loadXmltvFromText(text);
  } finally {
    clearTimeout(timer);
  }
}

export function clearEpg(): void {
  schedule.clear();
  lastLoadedAt = 0;
  emit();
}

export function epgStats() {
  return { channels: schedule.size, loadedAt: lastLoadedAt };
}

export function subscribeEpg(l: () => void): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}

/** Binary search for the programme covering `ref`. */
export function programmeAt(channelKey: string, ref: number): EpgProgramme | null {
  const arr = schedule.get(channelKey.toLowerCase());
  if (!arr || arr.length === 0) return null;
  let lo = 0, hi = arr.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const p = arr[mid];
    if (ref < p.start) hi = mid - 1;
    else if (ref >= p.end) lo = mid + 1;
    else return p;
  }
  return null;
}

export function programmeAfter(channelKey: string, ref: number): EpgProgramme | null {
  const arr = schedule.get(channelKey.toLowerCase());
  if (!arr || arr.length === 0) return null;
  for (let i = 0; i < arr.length; i++) if (arr[i].start > ref) return arr[i];
  return null;
}

export function programsForRange(channelKey: string, fromMs: number, toMs: number): EpgProgramme[] {
  const arr = schedule.get(channelKey.toLowerCase());
  if (!arr) return [];
  return arr.filter((p) => p.end > fromMs && p.start < toMs);
}
