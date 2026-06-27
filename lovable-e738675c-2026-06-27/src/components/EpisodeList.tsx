import { useMemo, useState } from "react";
import { Play } from "lucide-react";
import type { Title } from "../lib/catalog";
import { getEpisodes } from "../lib/episodes";

export function EpisodeList({ series, onPlay }: { series: Title; onPlay?: () => void }) {
  const seasons = useMemo(() => getEpisodes(series), [series]);
  const seasonKeys = useMemo(() => Object.keys(seasons).map(Number).sort((a, b) => a - b), [seasons]);
  const [active, setActive] = useState<number>(seasonKeys[0] ?? 1);
  const episodes = seasons[active] ?? [];

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h4 className="font-display text-lg font-semibold tracking-tight">Episodes</h4>
        {seasonKeys.length > 1 && (
          <select
            value={active}
            onChange={(e) => setActive(Number(e.target.value))}
            className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[12px] focus:border-white/30 focus:outline-none"
            aria-label="Season"
          >
            {seasonKeys.map((s) => (
              <option key={s} value={s}>Season {s}</option>
            ))}
          </select>
        )}
      </div>
      <ul className="space-y-2">
        {episodes.map((ep) => (
          <li key={ep.id}>
            <button
              type="button"
              onClick={onPlay}
              className="group flex w-full items-center gap-3 rounded-2xl border border-white/5 bg-white/[0.03] p-2.5 text-left transition hover:bg-white/[0.07]"
            >
              <div className="relative aspect-video w-32 shrink-0 overflow-hidden rounded-xl ring-1 ring-white/10">
                <img src={ep.still} alt="" loading="lazy" decoding="async" className="h-full w-full object-cover" />
                <div className="absolute inset-0 grid place-items-center bg-black/35 opacity-0 transition-opacity group-hover:opacity-100">
                  <span className="grid h-9 w-9 place-items-center rounded-full bg-white text-black">
                    <Play className="h-4 w-4 fill-current" />
                  </span>
                </div>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2">
                  <span className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    E{ep.number}
                  </span>
                  <span className="truncate text-[14px] font-medium tracking-[-0.01em]">{ep.title}</span>
                  <span className="ml-auto shrink-0 text-[11px] text-muted-foreground">{ep.duration}</span>
                </div>
                <p className="mt-1 line-clamp-2 text-[12.5px] font-light leading-snug text-muted-foreground">
                  {ep.description}
                </p>
              </div>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
