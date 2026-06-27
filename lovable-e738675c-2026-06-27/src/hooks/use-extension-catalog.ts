// React hook: aggregates Stremio-style catalog rows across all enabled
// extensions. Reactive to install/uninstall/enable/disable. Dedupes items
// within each row by stable namespaced id.
//
// States:
//   - "empty":   no enabled extension provides a `catalog` resource
//   - "loading": fetches in flight
//   - "ready":   at least one row with at least one item
//   - "error":   every catalog call failed
//
// The hook owns its own AbortController; remounting / changing enabled
// extensions cancels in-flight requests.

import { useCallback, useEffect, useState } from "react";
import { extensionRepository } from "../lib/extensions/repository";
import { hasResource, type InstalledExtension } from "../lib/extensions/types";
import { fetchCatalog } from "../lib/extensions/catalog-client";
import { metaPreviewToTitle } from "../lib/extensions/adapter";
import type { Title } from "../lib/catalog";

export type ExtensionCatalogStatus = "loading" | "ready" | "error" | "empty";

export interface ExtensionCatalogRow {
  key: string;
  title: string;
  addonName: string;
  items: Title[];
}

export interface ExtensionCatalogState {
  rows: ExtensionCatalogRow[];
  status: ExtensionCatalogStatus;
  error?: string;
  extensionsInstalled: number;
  extensionsEnabled: number;
  catalogsAvailable: number;
  retry: () => void;
}

function cap(s: string): string {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

function dedupeRow(items: Title[]): Title[] {
  const seen = new Set<string>();
  const out: Title[] = [];
  for (const t of items) {
    if (seen.has(t.id)) continue;
    seen.add(t.id);
    out.push(t);
  }
  return out;
}

export function useExtensionCatalog(): ExtensionCatalogState {
  // SSR-safe: start empty on both server and first client render to avoid
  // hydration mismatch (extensions live in localStorage). Real state is
  // pulled in inside the subscribe effect below.
  const [enabled, setEnabled] = useState<InstalledExtension[]>([]);
  const [installedCount, setInstalledCount] = useState<number>(0);
  const [rows, setRows] = useState<ExtensionCatalogRow[]>([]);
  const [status, setStatus] = useState<ExtensionCatalogStatus>("empty");
  const [error, setError] = useState<string | undefined>(undefined);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    const sync = () => {
      setEnabled(extensionRepository.getEnabled());
      setInstalledCount(extensionRepository.getAll().length);
    };
    sync();
    return extensionRepository.subscribe(sync);
  }, []);


  const catalogOwners = enabled.filter(
    (e) => hasResource(e.manifest, "catalog") && e.manifest.catalogs.length > 0,
  );
  const catalogsAvailable = catalogOwners.reduce(
    (acc, e) => acc + e.manifest.catalogs.length,
    0,
  );

  useEffect(() => {
    if (catalogOwners.length === 0) {
      setRows([]);
      setStatus("empty");
      setError(undefined);
      return;
    }

    const ctrl = new AbortController();
    setStatus("loading");
    setError(undefined);

    (async () => {
      const tasks: Promise<ExtensionCatalogRow | null>[] = [];
      for (const ext of catalogOwners) {
        for (const cat of ext.manifest.catalogs) {
          tasks.push(
            (async () => {
              try {
                const metas = await fetchCatalog(ext, cat, ctrl.signal);
                if (!metas.length) return null;
                const items = dedupeRow(metas.map((m) => metaPreviewToTitle(m, ext.baseUrl)));
                if (!items.length) return null;
                return {
                  key: `${ext.baseUrl}::${cat.type}::${cat.id}`,
                  title: cat.name || `${cap(cat.type)} • ${ext.manifest.name}`,
                  addonName: ext.manifest.name,
                  items,
                };
              } catch {
                return null;
              }
            })(),
          );
        }
      }

      const settled = await Promise.all(tasks);
      if (ctrl.signal.aborted) return;
      const ok = settled.filter((r): r is ExtensionCatalogRow => !!r);
      if (ok.length === 0) {
        setRows([]);
        setStatus("error");
        setError("No catalogs responded. Try another extension or refresh.");
      } else {
        setRows(ok);
        setStatus("ready");
      }
    })();

    return () => ctrl.abort();
    // catalogOwners is derived; key by enabled identity + retry
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, retryToken]);

  const retry = useCallback(() => setRetryToken((n) => n + 1), []);

  return {
    rows,
    status,
    error,
    extensionsInstalled: installedCount,
    extensionsEnabled: enabled.length,
    catalogsAvailable,
    retry,
  };
}
