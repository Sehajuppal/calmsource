import { lazy, type ComponentType } from "react";

/**
 * lazyWithRetry: wraps React.lazy with a retry + hard-reload fallback for
 * stale chunk hashes after redeploys ("Failed to fetch dynamically imported module").
 */
export function lazyWithRetry<T extends ComponentType<any>>(
  factory: () => Promise<{ default: T }>,
  key?: string,
) {
  const storageKey = `lumen.chunk-retry.${key ?? factory.toString().slice(0, 64)}`;
  return lazy(async () => {
    try {
      const mod = await factory();
      if (typeof window !== "undefined") {
        try {
          window.sessionStorage.removeItem(storageKey);
        } catch {}
      }
      return mod;
    } catch (err) {
      const msg = String((err as Error)?.message ?? err);
      const isChunkError =
        /Failed to fetch dynamically imported module|Importing a module script failed|ChunkLoadError|Loading chunk|error loading dynamically imported module/i.test(
          msg,
        );
      if (!isChunkError) throw err;

      // Retry once after a brief delay (handles transient network issues).
      try {
        await new Promise((r) => setTimeout(r, 350));
        return await factory();
      } catch (retryErr) {
        // Hard-reload once to pick up new chunk hashes after a redeploy.
        if (typeof window !== "undefined") {
          let alreadyReloaded = false;
          try {
            alreadyReloaded = window.sessionStorage.getItem(storageKey) === "1";
            window.sessionStorage.setItem(storageKey, "1");
          } catch {}
          if (!alreadyReloaded) {
            window.location.reload();
            // Return a never-resolving promise so React keeps the Suspense fallback
            // while the page reloads.
            return new Promise<{ default: T }>(() => {});
          }
        }
        throw retryErr;
      }
    }
  });
}
