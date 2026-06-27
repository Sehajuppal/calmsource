// Process-wide registry of active content providers. Single source of
// truth for resolving `provider://...` pseudo URLs at play time.

import type { Provider, ProviderKind } from "./types";
import { parseProviderUrl } from "./types";

const providers = new Map<string, Provider>();
const listeners = new Set<() => void>();

function emit() {
  for (const l of listeners) l();
}

export const providerRegistry = {
  register(p: Provider): void {
    providers.set(p.id, p);
    emit();
  },
  unregister(id: string): void {
    if (providers.delete(id)) emit();
  },
  get(id: string): Provider | null {
    return providers.get(id) ?? null;
  },
  list(): Provider[] {
    return Array.from(providers.values());
  },
  subscribe(l: () => void): () => void {
    listeners.add(l);
    return () => listeners.delete(l);
  },
  /**
   * Resolve a stored URL. If it's a `provider://` pseudo URL, ask the
   * matching provider; otherwise return as-is so plain HLS / MP4 URLs
   * keep working.
   */
  resolveUrl(url: string): string {
    const parsed = parseProviderUrl(url);
    if (!parsed) return url;
    const p = providers.get(parsed.providerId);
    if (!p) return url; // provider gone — caller will surface a load error
    return p.resolve(parsed.kind as ProviderKind, parsed.streamId);
  },
};

export type { Provider, ProviderKind };
