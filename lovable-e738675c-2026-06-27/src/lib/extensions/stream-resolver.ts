// Aggregates Stremio streams across all enabled stream-providing addons.
//
// Input: a Title (must carry a namespaced id produced by the catalog adapter).
// Output: ordered list of ResolvedStream — playable entries first, then
// torrent / external entries flagged so the UI can disable them.

import type { Title } from "../catalog";
import { parseTitleId } from "./adapter";
import { extensionRepository } from "./repository";
import { fetchStreams, type AddonStream } from "./stream-client";
import { hasResource } from "./types";

export type StreamKind = "direct" | "torrent" | "external" | "youtube";

export interface ResolvedStream {
  /** Stable id for React keys + selection state. */
  id: string;
  /** Display label e.g. "Torrentio · 1080p · 4.3 GB". */
  label: string;
  /** Sub-label with file/tracker info when available. */
  detail?: string;
  /** Quality bucket, best-effort parsed from `name`/`title`. */
  quality?: string;
  /** Which addon produced this stream. */
  source: string;
  kind: StreamKind;
  /** True if this app can actually play it right now (HTTP url, no debrid). */
  playable: boolean;
  /** Resolved URL when playable. */
  url?: string;
  /** Raw stream payload for advanced consumers (debrid hand-off, etc). */
  raw: AddonStream;
}

export interface StreamResolution {
  streams: ResolvedStream[];
  /** Number of stream-providing addons we queried. */
  queried: number;
  /** Number that responded successfully (may be 0 streams each). */
  succeeded: number;
  /** Errors, keyed by addon baseUrl. */
  errors: Record<string, string>;
}

const QUALITY_RE = /\b(2160p|1440p|1080p|720p|480p|360p|4k|uhd|hdr|hdrip|webrip|web-?dl|bluray|brrip|hdtv|cam)\b/i;

function detectQuality(s: AddonStream): string | undefined {
  const hay = `${s.name ?? ""} ${s.title ?? ""} ${s.description ?? ""}`;
  const m = QUALITY_RE.exec(hay);
  if (!m) return undefined;
  const q = m[1].toLowerCase();
  if (q === "4k" || q === "uhd") return "2160p";
  return q.toLowerCase();
}

const QUALITY_RANK: Record<string, number> = {
  "2160p": 6,
  "1440p": 5,
  "1080p": 4,
  "720p": 3,
  "480p": 2,
  "360p": 1,
};

function classify(s: AddonStream): StreamKind {
  if (s.url) return "direct";
  if (s.ytId) return "youtube";
  if (s.infoHash) return "torrent";
  return "external";
}

function buildLabel(addonName: string, s: AddonStream): string {
  const head = s.name?.trim() || addonName;
  const quality = detectQuality(s);
  return quality ? `${head} · ${quality.toUpperCase()}` : head;
}

function buildDetail(s: AddonStream): string | undefined {
  // Stremio addons commonly stuff filename + size into `title`.
  return s.title?.trim() || s.description?.trim() || undefined;
}

function dedupeKey(s: AddonStream, addon: string): string {
  if (s.url) return `u:${s.url}`;
  if (s.infoHash) return `t:${s.infoHash}:${s.fileIdx ?? "_"}`;
  if (s.ytId) return `y:${s.ytId}`;
  if (s.externalUrl) return `x:${s.externalUrl}`;
  return `${addon}:${s.title ?? s.name ?? Math.random()}`;
}

export async function resolveStreamsForTitle(
  title: Title,
  signal?: AbortSignal,
): Promise<StreamResolution> {
  const parsed = parseTitleId(title.id);
  if (!parsed) {
    return { streams: [], queried: 0, succeeded: 0, errors: {} };
  }

  const enabled = extensionRepository
    .getEnabled()
    .filter((e) => hasResource(e.manifest, "stream"));

  if (enabled.length === 0) {
    return { streams: [], queried: 0, succeeded: 0, errors: {} };
  }

  const errors: Record<string, string> = {};
  const tasks = enabled.map(async (ext) => {
    try {
      const list = await fetchStreams(ext, parsed.type, parsed.id, signal);
      return { ext, list, ok: true };
    } catch (e) {
      errors[ext.baseUrl] = e instanceof Error ? e.message : "Request failed";
      return { ext, list: [] as AddonStream[], ok: false };
    }
  });

  const settled = await Promise.all(tasks);
  if (signal?.aborted) {
    return { streams: [], queried: enabled.length, succeeded: 0, errors };
  }

  const seen = new Set<string>();
  const out: ResolvedStream[] = [];
  let succeeded = 0;
  for (const { ext, list, ok } of settled) {
    // Only count addons that actually answered. Previously this always
    // incremented, which meant `succeeded === queried` even when every
    // addon errored — making the picker unable to surface an "error" state.
    if (ok) succeeded++;
    for (const s of list) {
      const key = dedupeKey(s, ext.baseUrl);
      if (seen.has(key)) continue;
      seen.add(key);
      const kind = classify(s);
      const playable = kind === "direct" && !!s.url;
      out.push({
        id: `${ext.baseUrl}::${key}`,
        label: buildLabel(ext.manifest.name, s),
        detail: buildDetail(s),
        quality: detectQuality(s),
        source: ext.manifest.name,
        kind,
        playable,
        url: s.url,
        raw: s,
      });
    }
  }

  // Sort: playable first, then by quality desc, then by source name.
  out.sort((a, b) => {
    if (a.playable !== b.playable) return a.playable ? -1 : 1;
    const qa = a.quality ? QUALITY_RANK[a.quality] ?? 0 : 0;
    const qb = b.quality ? QUALITY_RANK[b.quality] ?? 0 : 0;
    if (qa !== qb) return qb - qa;
    return a.source.localeCompare(b.source);
  });

  return { streams: out, queried: enabled.length, succeeded, errors };
}
