// Mobile mirror of src/lib/extensions/repository.ts.
// AsyncStorage is async, so initial load is fire-and-forget. UI components
// can call `whenReady()` or subscribe — initial state may be empty for one
// tick after app boot until persisted extensions hydrate.

import AsyncStorage from "@react-native-async-storage/async-storage";
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
  private readyPromise: Promise<void>;

  constructor() {
    this.readyPromise = this.load();
  }

  private async load() {
    try {
      const raw = await AsyncStorage.getItem(LS_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        this.list = parsed.filter(isInstalled);
        this.emit();
      }
    } catch { /* ignore */ }
  }

  private persist() {
    AsyncStorage.setItem(LS_KEY, JSON.stringify(this.list)).catch(() => {});
  }
  private emit() {
    for (const l of Array.from(this.listeners)) {
      try { l(); } catch { /* ignore */ }
    }
  }

  whenReady(): Promise<void> { return this.readyPromise; }

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
      baseUrl, manifest, enabled: true,
      installedAt: Date.now(), lastCheck: Date.now(), health: "ok",
    };
    this.list = [...this.list, ext];
    this.persist(); this.emit();
    return ext;
  }

  uninstall(baseUrl: string): void {
    const next = this.list.filter((e) => e.baseUrl !== baseUrl);
    if (next.length === this.list.length) return;
    this.list = next; this.persist(); this.emit();
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
        ...existing, manifest, lastCheck: Date.now(), health: "ok", error: undefined,
      };
      this.list = this.list.map((e) => (e.baseUrl === baseUrl ? updated : e));
      this.persist(); this.emit();
      return updated;
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Unknown error";
      this.list = this.list.map((e) =>
        e.baseUrl === baseUrl
          ? { ...e, lastCheck: Date.now(), health: "error", error: msg }
          : e,
      );
      this.persist(); this.emit();
      throw err;
    }
  }
}

export const extensionRepository = new ExtensionRepository();
export type { InstalledExtension } from "./types";
