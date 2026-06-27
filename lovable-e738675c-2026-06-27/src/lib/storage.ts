// Tiny typed localStorage helpers with per-profile namespacing.
// Keys look like: lumen.<profileId|global>.<key>.v1

import { toast } from "sonner";

const PREFIX = "lumen";

export function nsKey(profileId: string | null | undefined, key: string) {
  return `${PREFIX}.${profileId ?? "global"}.${key}.v1`;
}

export function readJSON<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch (e) {
    // Corrupt entry — log once and self-heal by removing it.
    console.warn("[storage] readJSON failed for", key, e);
    try { localStorage.removeItem(key); } catch { /* ignore */ }
    return fallback;
  }
}

// One-shot quota warning so noisy progress writes don't spam.
let quotaWarned = false;

export function writeJSON(key: string, value: unknown): boolean {
  if (typeof window === "undefined") return false;
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch (e) {
    const isQuota =
      e instanceof DOMException &&
      (e.name === "QuotaExceededError" ||
        e.name === "NS_ERROR_DOM_QUOTA_REACHED");
    if (isQuota && !quotaWarned) {
      quotaWarned = true;
      console.warn("[storage] quota exceeded for", key, e);
      try {
        toast.warning("Storage full — some preferences won't be saved.", {
          description: "Clear browser data or export and reset from Settings.",
        });
      } catch { /* toast unavailable during SSR */ }
    } else if (!isQuota) {
      console.warn("[storage] writeJSON failed for", key, e);
    }
    return false;
  }
}

export function removeKey(key: string) {
  if (typeof window === "undefined") return;
  try {
    localStorage.removeItem(key);
  } catch (e) {
    console.warn("[storage] removeKey failed for", key, e);
  }
}

