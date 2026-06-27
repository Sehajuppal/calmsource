import type { ExtensionManifest } from "./types";

/**
 * Normalize a user-pasted addon URL into a canonical base URL.
 * Accepts: "torrentio.strem.fun", "https://x/manifest.json", "https://x/".
 * Returns: "https://torrentio.strem.fun" (no trailing slash, no /manifest.json).
 */
export function normalizeBaseUrl(input: string): string {
  let url = (input ?? "").trim();
  if (!url) throw new Error("Manifest URL is required");
  if (!/^https?:\/\//i.test(url)) url = "https://" + url;
  url = url.replace(/\/+manifest\.json\/?$/i, "");
  url = url.replace(/\/+$/, "");
  try {
    const u = new URL(url);
    if (!/^https?:$/.test(u.protocol)) throw new Error("Only http(s) URLs are supported");
  } catch {
    throw new Error("Invalid manifest URL");
  }
  return url;
}

export async function fetchManifest(baseUrl: string, signal?: AbortSignal): Promise<ExtensionManifest> {
  const url = `${baseUrl}/manifest.json`;
  let res: Response;
  try {
    res = await fetch(url, { signal, headers: { Accept: "application/json" } });
  } catch (e) {
    if ((e as { name?: string })?.name === "AbortError") throw e;
    throw new Error("Network error reaching addon");
  }
  if (!res.ok) throw new Error(`Manifest request failed (${res.status})`);
  let data: unknown;
  try {
    data = await res.json();
  } catch {
    throw new Error("Manifest is not valid JSON");
  }
  return validateManifest(data);
}

export function validateManifest(data: unknown): ExtensionManifest {
  if (!data || typeof data !== "object") throw new Error("Manifest is not an object");
  const m = data as Record<string, unknown>;
  const required: Array<keyof ExtensionManifest> = ["id", "version", "name", "resources", "types", "catalogs"];
  for (const k of required) {
    if (!(k in m)) throw new Error(`Manifest missing field "${String(k)}"`);
  }
  if (typeof m.id !== "string" || !m.id) throw new Error('Manifest "id" must be a non-empty string');
  if (typeof m.name !== "string" || !m.name) throw new Error('Manifest "name" must be a non-empty string');
  if (typeof m.version !== "string" || !m.version) throw new Error('Manifest "version" must be a non-empty string');
  if (!Array.isArray(m.resources)) throw new Error('Manifest "resources" must be an array');
  if (!Array.isArray(m.types)) throw new Error('Manifest "types" must be an array');
  if (!Array.isArray(m.catalogs)) throw new Error('Manifest "catalogs" must be an array');
  return m as unknown as ExtensionManifest;
}

export async function fetchManifestWithTimeout(baseUrl: string, ms = 15000): Promise<ExtensionManifest> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), ms);
  try {
    return await fetchManifest(baseUrl, ctrl.signal);
  } catch (e) {
    if ((e as { name?: string })?.name === "AbortError") throw new Error("Manifest request timed out");
    throw e;
  } finally {
    clearTimeout(t);
  }
}
