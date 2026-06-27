// Xtream Codes provider — talks to `player_api.php` for catalogs and
// builds direct stream URLs on resolve. No tokens are baked into stored
// pseudo URLs, so creds can rotate freely.

import type { Provider, ProviderKind, ProviderStream } from "./types";
import { makeProviderUrl } from "./types";

export type XtreamCreds = {
  host: string; // origin + optional path, no trailing slash
  username: string;
  password: string;
  /** Container hint for VOD (`mp4`, `mkv`, `ts`). Defaults to `mp4`. */
  vodExt?: string;
};

const TIMEOUT_MS = 20_000;

async function getJSON<T>(url: string): Promise<T> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return (await res.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

function buildApiUrl(c: XtreamCreds, action?: string): string {
  const base = c.host.replace(/\/+$/, "");
  const u = `${base}/player_api.php?username=${encodeURIComponent(c.username)}&password=${encodeURIComponent(c.password)}`;
  return action ? `${u}&action=${action}` : u;
}

function buildStreamUrl(c: XtreamCreds, kind: ProviderKind, streamId: string): string {
  const base = c.host.replace(/\/+$/, "");
  const u = encodeURIComponent(c.username);
  const p = encodeURIComponent(c.password);
  switch (kind) {
    case "live":
      // .m3u8 widely supported; falls back server-side to .ts.
      return `${base}/live/${u}/${p}/${streamId}.m3u8`;
    case "vod": {
      const ext = c.vodExt?.replace(/^\./, "") || "mp4";
      return `${base}/movie/${u}/${p}/${streamId}.${ext}`;
    }
    case "series":
      return `${base}/series/${u}/${p}/${streamId}.mp4`;
  }
}

/* Xtream raw response shapes (subset). */
type RawLive = { stream_id: number | string; name: string; stream_icon?: string; category_id?: string };
type RawVod = { stream_id: number | string; name: string; stream_icon?: string; category_id?: string; container_extension?: string };
type RawSeries = { series_id: number | string; name: string; cover?: string; category_id?: string };
type RawCategory = { category_id: string; category_name: string };

async function withCategoryMap(c: XtreamCreds, action: string): Promise<Map<string, string>> {
  try {
    const cats = await getJSON<RawCategory[]>(buildApiUrl(c, action));
    const m = new Map<string, string>();
    for (const k of cats) m.set(String(k.category_id), k.category_name);
    return m;
  } catch {
    return new Map();
  }
}

export function createXtreamProvider(id: string, label: string, creds: XtreamCreds): Provider {
  const c: XtreamCreds = { ...creds, host: creds.host.replace(/\/+$/, "") };
  return {
    id,
    label,
    kind: "xtream",
    async listLive(): Promise<ProviderStream[]> {
      const [cats, items] = await Promise.all([
        withCategoryMap(c, "get_live_categories"),
        getJSON<RawLive[]>(buildApiUrl(c, "get_live_streams")),
      ]);
      return items.map((it) => ({
        id: String(it.stream_id),
        name: it.name,
        logo: it.stream_icon,
        group: it.category_id ? cats.get(String(it.category_id)) : undefined,
        url: makeProviderUrl(id, "live", String(it.stream_id)),
      }));
    },
    async listVod(): Promise<ProviderStream[]> {
      const [cats, items] = await Promise.all([
        withCategoryMap(c, "get_vod_categories"),
        getJSON<RawVod[]>(buildApiUrl(c, "get_vod_streams")),
      ]);
      return items.map((it) => ({
        id: String(it.stream_id),
        name: it.name,
        logo: it.stream_icon,
        group: it.category_id ? cats.get(String(it.category_id)) : undefined,
        url: makeProviderUrl(id, "vod", String(it.stream_id)),
      }));
    },
    async listSeries(): Promise<ProviderStream[]> {
      const [cats, items] = await Promise.all([
        withCategoryMap(c, "get_series_categories"),
        getJSON<RawSeries[]>(buildApiUrl(c, "get_series")),
      ]);
      return items.map((it) => ({
        id: String(it.series_id),
        name: it.name,
        logo: it.cover,
        group: it.category_id ? cats.get(String(it.category_id)) : undefined,
        url: makeProviderUrl(id, "series", String(it.series_id)),
      }));
    },
    resolve(kind, streamId) {
      return buildStreamUrl(c, kind, streamId);
    },
  };
}
