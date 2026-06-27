// Stream picker dialog. Renders resolved streams from extensions and
// hands the chosen URL to the global player. Non-playable entries (torrent
// magnet streams that need a debrid resolver, external URLs, etc.) render
// as disabled rows with a clear reason — no silent failures.

import { memo, useEffect } from "react";
import { Play, ExternalLink, Magnet, Film, X, AlertTriangle, Loader2 } from "lucide-react";
import { useStreamPicker } from "../lib/stream-picker-store";
import type { ResolvedStream } from "../lib/extensions/stream-resolver";

function kindIcon(kind: ResolvedStream["kind"]) {
  switch (kind) {
    case "direct": return <Play className="h-4 w-4" />;
    case "torrent": return <Magnet className="h-4 w-4" />;
    case "youtube": return <Film className="h-4 w-4" />;
    case "external": return <ExternalLink className="h-4 w-4" />;
  }
}

function reasonFor(s: ResolvedStream): string | null {
  if (s.playable) return null;
  if (s.kind === "torrent") return "Torrent — needs a debrid provider";
  if (s.kind === "external") return "External link — open in a browser";
  if (s.kind === "youtube") return "YouTube embed not supported here";
  return "Unplayable in this build";
}

export const StreamPicker = memo(function StreamPicker() {
  const { open, status, title, streams, error, queried, close, pick } = useStreamPicker();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") close(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, close]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Choose a stream"
      className="fixed inset-0 z-[60] flex items-end justify-center bg-black/70 backdrop-blur-sm sm:items-center"
      onClick={close}
    >
      <div
        className="relative w-full max-w-2xl rounded-t-2xl border border-white/10 bg-[#0b0d12] p-5 shadow-2xl sm:rounded-2xl sm:p-7"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={close}
          aria-label="Close stream picker"
          className="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-white/5 text-white/80 transition hover:bg-white/10"
        >
          <X className="h-4 w-4" />
        </button>

        <div className="pr-10">
          <h2 className="font-display text-lg font-semibold tracking-[-0.01em] sm:text-xl">
            {title?.name ? `Streams for "${title.name}"` : "Streams"}
          </h2>
          <p className="mt-1 text-[12px] text-white/60 sm:text-[13px]">
            {status === "loading" && `Querying ${queried || "extensions"}…`}
            {status === "ready" && `${streams.length} stream${streams.length === 1 ? "" : "s"} from ${queried} extension${queried === 1 ? "" : "s"}`}
            {status === "empty" && "No streams returned for this title."}
            {status === "error" && (error ?? "Failed to query extensions.")}
          </p>
        </div>

        <div className="mt-5 max-h-[55vh] overflow-y-auto pr-1">
          {status === "loading" && (
            <div className="flex items-center justify-center gap-2 py-12 text-white/60">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span className="text-[13px]">Resolving streams…</span>
            </div>
          )}

          {status === "empty" && (
            <div className="rounded-xl border border-white/10 bg-white/5 p-5 text-center">
              <AlertTriangle className="mx-auto mb-2 h-5 w-5 text-amber-400/90" />
              <p className="text-[13px] text-white/80">
                {queried === 0
                  ? "No stream-providing add-ons are enabled. Install one in Settings."
                  : "Add-ons responded but had no streams for this title."}
              </p>
            </div>
          )}

          {status === "error" && (
            <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-5 text-center">
              <AlertTriangle className="mx-auto mb-2 h-5 w-5 text-rose-300" />
              <p className="text-[13px] text-rose-100">{error ?? "Unknown error"}</p>
            </div>
          )}

          {status === "ready" && (
            <ul className="space-y-2">
              {streams.map((s) => {
                const reason = reasonFor(s);
                return (
                  <li key={s.id}>
                    <button
                      type="button"
                      onClick={() => s.playable && pick(s)}
                      disabled={!s.playable}
                      className={[
                        "group flex w-full items-start gap-3 rounded-xl border p-3 text-left transition",
                        s.playable
                          ? "border-white/10 bg-white/[0.04] hover:border-white/25 hover:bg-white/[0.08]"
                          : "cursor-not-allowed border-white/5 bg-white/[0.02] opacity-60",
                      ].join(" ")}
                    >
                      <span
                        className={[
                          "mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-full",
                          s.playable ? "bg-white text-black" : "bg-white/10 text-white/70",
                        ].join(" ")}
                      >
                        {kindIcon(s.kind)}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="truncate text-[13px] font-semibold text-white sm:text-[14px]">
                            {s.label}
                          </span>
                          <span className="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[10px] uppercase tracking-wider text-white/70">
                            {s.source}
                          </span>
                          {s.quality && (
                            <span className="rounded-full bg-cobalt-500/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-cobalt-200">
                              {s.quality}
                            </span>
                          )}
                        </span>
                        {s.detail && (
                          <span className="mt-1 line-clamp-2 block whitespace-pre-line text-[12px] text-white/60">
                            {s.detail}
                          </span>
                        )}
                        {reason && (
                          <span className="mt-1 block text-[11px] text-amber-300/80">
                            {reason}
                          </span>
                        )}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
});
