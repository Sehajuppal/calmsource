/**
 * Low-end device detection. Sets `data-perf="low"` on <html> so CSS can
 * cheaply disable expensive effects (backdrop-filter, large blurred shadows,
 * Ken Burns, shimmer, hover scaling) without per-component branching.
 *
 * Heuristics (any one trips low mode):
 *   - prefers-reduced-motion
 *   - Network Information Save-Data on
 *   - navigator.deviceMemory <= 2
 *   - navigator.hardwareConcurrency <= 2
 *   - 2g / slow-2g effectiveType
 */
export function installPerfMode(): void {
  if (typeof window === "undefined" || typeof document === "undefined") return;

  const root = document.documentElement;
  const nav = navigator as Navigator & {
    deviceMemory?: number;
    connection?: {
      saveData?: boolean;
      effectiveType?: string;
      addEventListener?: (t: string, cb: () => void) => void;
    };
  };

  const evaluate = () => {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const saveData = !!nav.connection?.saveData;
    const lowMem = typeof nav.deviceMemory === "number" && nav.deviceMemory <= 2;
    const lowCpu =
      typeof nav.hardwareConcurrency === "number" && nav.hardwareConcurrency <= 2;
    const slowNet =
      nav.connection?.effectiveType === "2g" || nav.connection?.effectiveType === "slow-2g";

    const low = reduceMotion || saveData || lowMem || lowCpu || slowNet;
    if (low) root.setAttribute("data-perf", "low");
    else root.removeAttribute("data-perf");
  };

  evaluate();

  try {
    window
      .matchMedia("(prefers-reduced-motion: reduce)")
      .addEventListener?.("change", evaluate);
    nav.connection?.addEventListener?.("change", evaluate);
  } catch {
    /* older browsers */
  }
}
