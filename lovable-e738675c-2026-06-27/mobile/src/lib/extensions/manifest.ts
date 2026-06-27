// Mirror of src/lib/extensions/manifest.ts — keep in sync.
import type { ExtensionManifest } from "./types";

export function normalizeBaseUrl(input: string): string {
  let url = (input ?? "").trim();
  if (!url) throw new Error("Manifest URL is required");
  if (!/^https?:\/\//i.test(url)) url = "https://" + url;
  url = url.replace(/\/+manifest\.json\/?$/i, "");
  url = url.replace(/\/+$/, "");
  try {
    // RN supports URL with a polyfill or modern Hermes; fall back to a regex test if needed.
    // eslint-disable-next-line no-new
    new URL(url);
  } catch {
    if (!/^https?:\/\/[^\s]+/i.test(url)) throw new Error("Invalid manifest URL");
  }
  return url;
}

export function validateManifest(data: unknown): ExtensionManifest {
  if (!data || typeof data !== "object") throw new Error("Manifest is not an object");
  const m = data as Record<string, unknown>;
  for (const k of ["id", "version", "name", "resources", "types", "catalogs"] as const) {
    if (!(k in m)) throw new Error(`Manifest missing field "${k}"`);
  }
  if (typeof m.id !== "string" || !m.id) throw new Error('Manifest "id" must be a string');
  if (typeof m.name !== "string" || !m.name) throw new Error('Manifest "name" must be a string');
  if (typeof m.version !== "string" || !m.version) throw new Error('Manifest "version" must be a string');
  if (!Array.isArray(m.resources)) throw new Error('Manifest "resources" must be an array');
  if (!Array.isArray(m.types)) throw new Error('Manifest "types" must be an array');
  if (!Array.isArray(m.catalogs)) throw new Error('Manifest "catalogs" must be an array');
  return m as unknown as ExtensionManifest;
}

export async function fetchManifestWithTimeout(baseUrl: string, ms = 15000): Promise<ExtensionManifest> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), ms);
  try {
    const res = await fetch(`${baseUrl}/manifest.json`, {
      signal: ctrl.signal,
      headers: { Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`Manifest request failed (${res.status})`);
    let data: unknown;
    try { data = await res.json(); } catch { throw new Error("Manifest is not valid JSON"); }
    return validateManifest(data);
  } catch (e) {
    if ((e as { name?: string })?.name === "AbortError") throw new Error("Manifest request timed out");
    throw e;
  } finally {
    clearTimeout(t);
  }
}
