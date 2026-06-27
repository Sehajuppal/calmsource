// Enriches a Title via the Stremio `meta` resource when the title id
// is namespaced (i.e. came from an extension catalog). For static
// `Title` objects from src/lib/catalog.ts this is a no-op.

import { useEffect, useState } from "react";
import type { Title } from "../lib/catalog";
import { enrichTitleWithMeta, parseTitleId } from "../lib/extensions/adapter";
import { fetchMeta } from "../lib/extensions/catalog-client";

export interface EnrichedTitleState {
  title: Title;
  loading: boolean;
  error?: string;
  isExtensionTitle: boolean;
}

export function useEnrichedTitle(base: Title): EnrichedTitleState {
  const [title, setTitle] = useState<Title>(base);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const parsed = parseTitleId(base.id);

  useEffect(() => {
    setTitle(base);
    setError(undefined);
    if (!parsed) {
      setLoading(false);
      return;
    }
    const ctrl = new AbortController();
    setLoading(true);
    (async () => {
      try {
        const meta = await fetchMeta(parsed.baseUrl, parsed.type, parsed.id, ctrl.signal);
        if (ctrl.signal.aborted) return;
        if (meta) setTitle(enrichTitleWithMeta(base, meta));
      } catch (e) {
        if (ctrl.signal.aborted) return;
        setError(e instanceof Error ? e.message : "Failed to load details");
      } finally {
        if (!ctrl.signal.aborted) setLoading(false);
      }
    })();
    return () => ctrl.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [base.id]);

  return { title, loading, error, isExtensionTitle: !!parsed };
}
