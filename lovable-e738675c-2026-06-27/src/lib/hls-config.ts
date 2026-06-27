// Perf-aware HLS.js configuration.
//
// Goal: smooth playback on low-end Android TVs and mid-tier phones where
// the default config (large buffers, no level cap) wastes memory and CPU
// decoding 1080p on a 540p surface.
//
// Heuristics mirror src/lib/perf-mode.ts so the same `data-perf="low"`
// signal drives both CSS and player tuning.

export type HlsTier = "low" | "normal";

export function detectHlsTier(): HlsTier {
  if (typeof document !== "undefined" && document.documentElement.getAttribute("data-perf") === "low") {
    return "low";
  }
  if (typeof navigator === "undefined") return "normal";
  const nav = navigator as Navigator & {
    deviceMemory?: number;
    connection?: { saveData?: boolean; effectiveType?: string };
  };
  if (nav.connection?.saveData) return "low";
  if (typeof nav.deviceMemory === "number" && nav.deviceMemory <= 2) return "low";
  if (typeof nav.hardwareConcurrency === "number" && nav.hardwareConcurrency <= 2) return "low";
  const et = nav.connection?.effectiveType;
  if (et === "2g" || et === "slow-2g" || et === "3g") return "low";
  return "normal";
}

/** Config passed to `new Hls(config)`. Always include `enableWorker: true`. */
export function hlsConfig(tier: HlsTier = detectHlsTier()) {
  if (tier === "low") {
    return {
      enableWorker: true,
      lowLatencyMode: false,
      backBufferLength: 15,           // free decoded GOPs aggressively
      maxBufferLength: 12,            // seconds ahead
      maxMaxBufferLength: 30,
      maxBufferSize: 30 * 1024 * 1024, // 30 MB cap
      capLevelToPlayerSize: true,     // don't decode 1080p into a 540p tile
      startLevel: -1,                 // ABR picks; combined with cap it stays small
      abrEwmaDefaultEstimate: 500_000,
      testBandwidth: false,
    } as const;
  }
  return {
    enableWorker: true,
    lowLatencyMode: false,
    backBufferLength: 60,
    maxBufferLength: 30,
    capLevelToPlayerSize: true,
  } as const;
}
