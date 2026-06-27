// ExtensionRepository — installs, persists, and exposes Stremio-compatible
// addons. UI subscribes via `subscribe()` and reads via `getAll()` /
// `getEnabled()`. Persistence is plain JSON in localStorage; manifests are
// small enough that re-validating on the fly is unnecessary.
//
// This is the single source of truth Priority 8 (catalog pipeline) will read
// from. Do not bypass it — every consumer should go through this module.

import { fetchManifestWithTimeout, normalizeBaseUrl } from "./manifest";
import type { InstalledExtension } from "./types";

const LS_KEY = "lumen.extensions.v1";
type Listener = () => void;

function isInstalled(e: unknown): e is InstalledExtension {
  if (!e || typeof e !== "object") return false;
  const x = e as Record<string, unknown>;
  return typeof x.baseUrl === "string" && !!x.manifest && typeof x.enabled === "boolean";
}

class ExtensionRepository {
  private list: InstalledExtension[] = [];
  private listeners = new Set<Listener>();

  constructor() {
    if (typeof localStorage === "undefined") return;
    try {
      const raw = localStorage.getItem(LS_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) this.list = parsed.filter(isInstalled);
    } catch { /* ignore */ }
  }

  private persist() {
    if (typeof localStorage === "undefined") return;
    try { localStorage.setItem(LS_KEY, JSON.stringify(this.list)); } catch { /* ignore */ }
  }
  private emit() {
    for (const l of Array.from(this.listeners)) {
      try { l(); } catch { /* ignore */ }
    }
  }

  subscribe(l: Listener): () => void {
    this.listeners.add(l);
    return () => { this.listeners.delete(l); };
  }

  getAll(): InstalledExtension[] { return [...this.list]; }
  getEnabled(): InstalledExtension[] { return this.list.filter((e) => e.enabled); }
  get(baseUrl: string): InstalledExtension | undefined {
    return this.list.find((e) => e.baseUrl === baseUrl);
  }

  async install(rawUrl: string): Promise<InstalledExtension> {
    const baseUrl = normalizeBaseUrl(rawUrl);
    if (this.list.some((e) => e.baseUrl === baseUrl)) {
      throw new Error("Extension already installed");
    }
    const manifest = await fetchManifestWithTimeout(baseUrl);
    const ext: InstalledExtension = {
      baseUrl,
      manifest,
      enabled: true,
      installedAt: Date.now(),
      lastCheck: Date.now(),
      health: "ok",
    };
    this.list = [...this.list, ext];
    this.persist();
    this.emit();
    return ext;
  }

  uninstall(baseUrl: string): void {
    const next = this.list.filter((e) => e.baseUrl !== baseUrl);
    if (next.length === this.list.length) return;
    this.list = next;
    this.persist();
    this.emit();
  }

  setEnabled(baseUrl: string, enabled: boolean): void {
    let changed = false;
    this.list = this.list.map((e) => {
      if (e.baseUrl !== baseUrl || e.enabled === enabled) return e;
      changed = true;
      return { ...e, enabled };
    });
    if (changed) { this.persist(); this.emit(); }
  }

  async refresh(baseUrl: string): Promise<InstalledExtension> {
    const existing = this.list.find((e) => e.baseUrl === baseUrl);
    if (!existing) throw new Error("Not installed");
    try {
      const manifest = await fetchManifestWithTimeout(baseUrl);
      const updated: InstalledExtension = {
        ...existing,
        manifest,
        lastCheck: Date.now(),
        health: "ok",
        error: undefined,
      };
      this.list = this.list.map((e) => (e.baseUrl === baseUrl ? updated : e));
      this.persist();
      this.emit();
      return updated;
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Unknown error";
      this.list = this.list.map((e) =>
        e.baseUrl === baseUrl
          ? { ...e, lastCheck: Date.now(), health: "error", error: msg }
          : e,
      );
      this.persist();
      this.emit();
      throw err;
    }
  }
}

export const extensionRepository = new ExtensionRepository();
export type { InstalledExtension } from "./types";
