// Mirror of src/lib/extensions/types.ts — keep in sync.
export type ExtensionResourceName =
  | "catalog"
  | "meta"
  | "stream"
  | "subtitles"
  | "addon_catalog"
  | string;

export type ExtensionType = "movie" | "series" | "channel" | "tv" | "anime" | string;

export interface ExtensionCatalog {
  type: ExtensionType;
  id: string;
  name?: string;
  extra?: Array<{ name: string; isRequired?: boolean; options?: string[]; optionsLimit?: number }>;
}

export type ExtensionResource =
  | ExtensionResourceName
  | { name: ExtensionResourceName; types?: string[]; idPrefixes?: string[] };

export interface ExtensionManifest {
  id: string;
  version: string;
  name: string;
  description?: string;
  logo?: string;
  background?: string;
  contactEmail?: string;
  resources: ExtensionResource[];
  types: ExtensionType[];
  catalogs: ExtensionCatalog[];
  idPrefixes?: string[];
  behaviorHints?: Record<string, unknown>;
}

export type ExtensionHealth = "unknown" | "ok" | "error";

export interface InstalledExtension {
  baseUrl: string;
  manifest: ExtensionManifest;
  enabled: boolean;
  installedAt: number;
  lastCheck?: number;
  health: ExtensionHealth;
  error?: string;
}

export interface ExtensionPreset {
  name: string;
  description: string;
  manifestUrl: string;
}

// Catalog add-on presets only (no stream/torrent aggregators — legal/portfolio).
export const EXTENSION_PRESETS: ExtensionPreset[] = [
  {
    name: "Cinemeta",
    description: "Official catalog + metadata (movies & series). Required for browse rows.",
    manifestUrl: "https://v3-cinemeta.strem.io/manifest.json",
  },
];

export function hasResource(m: ExtensionManifest, name: ExtensionResourceName): boolean {
  return m.resources.some((r) => (typeof r === "string" ? r === name : r.name === name));
}
