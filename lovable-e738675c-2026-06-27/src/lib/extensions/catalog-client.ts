// HTTP client for Stremio-compatible catalog + meta endpoints.
// Spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.md
//
// Every request is bounded by `REQ_TIMEOUT` via AbortController. Callers
// can pass their own `signal` to participate in upstream cancellation
// (React effect cleanup, useExtensionCatalog re-fetch, …).

import type { ExtensionCatalog, InstalledExtension } from "./types";

export interface AddonMetaPreview {
  id: string;
  type: string;
  name: string;
  poster?: string;
  posterShape?: "square" | "poster" | "landscape" | string;
  background?: string;
  logo?: string;
  description?: string;
  releaseInfo?: string;
  imdbRating?: string;
  genres?: string[];
}

export interface AddonVideo {
  id: string;
  title: string;
  released?: string;
  thumbnail?: string;
  season?: number;
  episode?: number;
  overview?: string;
}

export interface AddonMeta extends AddonMetaPreview {
  cast?: string[];
  director?: string[];
  runtime?: string;
  videos?: AddonVideo[];
  trailers?: { source: string; type: string }[];
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

function isMetaPreview(x: unknown): x is AddonMetaPreview {
  if (!x || typeof x !== "object") return false;
  const m = x as Record<string, unknown>;
  return typeof m.id === "string" && typeof m.type === "string" && typeof m.name === "string";
}

export async function fetchCatalog(
  ext: InstalledExtension,
  catalog: ExtensionCatalog,
  signal?: AbortSignal,
): Promise<AddonMetaPreview[]> {
  const url = `${ext.baseUrl}/catalog/${encodeURIComponent(catalog.type)}/${encodeURIComponent(catalog.id)}.json`;
  const data = (await fetchJsonWithTimeout(url, signal)) as { metas?: unknown };
  if (!data || !Array.isArray(data.metas)) return [];
  return data.metas.filter(isMetaPreview);
}

export async function fetchMeta(
  baseUrl: string,
  type: string,
  id: string,
  signal?: AbortSignal,
): Promise<AddonMeta | null> {
  const url = `${baseUrl}/meta/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`;
  const data = (await fetchJsonWithTimeout(url, signal)) as { meta?: AddonMeta };
  return data?.meta && typeof data.meta === "object" ? data.meta : null;
}
