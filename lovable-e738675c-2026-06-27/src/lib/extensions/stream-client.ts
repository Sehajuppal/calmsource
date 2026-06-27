// HTTP client for Stremio-compatible /stream endpoints.
// Spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md
//
// A stream response is `{ streams: AddonStream[] }`. Streams may carry a
// direct `url` (playable as-is), a torrent `infoHash` (needs a debrid
// resolver — not playable in-browser today), an `externalUrl`, or a
// `ytId`. We surface every variant; the resolver tags playability.

import type { InstalledExtension } from "./types";

export interface AddonStreamBehaviorHints {
  bingeGroup?: string;
  countryWhitelist?: string[];
  notWebReady?: boolean;
  proxyHeaders?: { request?: Record<string, string>; response?: Record<string, string> };
}

export interface AddonStream {
  // One of url / infoHash / externalUrl / ytId is required by the spec.
  url?: string;
  infoHash?: string;
  fileIdx?: number;
  externalUrl?: string;
  ytId?: string;

  name?: string;
  title?: string;
  description?: string;
  subtitles?: unknown[];
  sources?: string[]; // tracker list for torrent streams
  behaviorHints?: AddonStreamBehaviorHints;
}

const REQ_TIMEOUT = 15000;

async function fetchJsonWithTimeout(url: string, signal?: AbortSignal, ms = REQ_TIMEOUT): Promise<unknown> {
  const local = new AbortController();
  const onAbort = () => local.abort();
  if (signal) {
    if (signal.aborted) local.abort();
    else signal.addEventListener("abort", onAbort, { once: true });
  }
  const timer = setTimeout(() => local.abort(), ms);
  try {
    const res = await fetch(url, { signal: local.signal, headers: { Accept: "application/json" } });
    if (!res.ok) throw new Error(`Request failed (${res.status})`);
    return await res.json();
  } catch (e) {
    if ((e as { name?: string })?.name === "AbortError") throw new Error("Request timed out");
    throw e;
  } finally {
    clearTimeout(timer);
    if (signal) signal.removeEventListener("abort", onAbort);
  }
}

function isStream(x: unknown): x is AddonStream {
  if (!x || typeof x !== "object") return false;
  const s = x as Record<string, unknown>;
  return (
    typeof s.url === "string" ||
    typeof s.infoHash === "string" ||
    typeof s.externalUrl === "string" ||
    typeof s.ytId === "string"
  );
}

export async function fetchStreams(
  ext: InstalledExtension,
  type: string,
  id: string,
  signal?: AbortSignal,
): Promise<AddonStream[]> {
  const url = `${ext.baseUrl}/stream/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`;
  const data = (await fetchJsonWithTimeout(url, signal)) as { streams?: unknown };
  if (!data || !Array.isArray(data.streams)) return [];
  return data.streams.filter(isStream);
}
