// Stremio-compatible extension types.
// Spec reference: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md
//
// We keep the shape loose — many community addons add extra fields we don't
// want to validate strictly. Only the fields the catalog/meta/stream pipeline
// needs are required at install time.

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

// Resources can be plain strings or detailed objects per the SDK.
export type ExtensionResource =
  | ExtensionResourceName
  | {
      name: ExtensionResourceName;
      types?: string[];
      idPrefixes?: string[];
    };

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
  /** Canonical base URL — no trailing slash, no /manifest.json suffix. */
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

// Curated catalog add-on presets. Only includes metadata/catalog providers —
// stream/torrent aggregators are intentionally excluded (legal/portfolio).
// Users can still paste any compatible manifest URL manually.
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
