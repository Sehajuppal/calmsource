import { useEffect, useMemo } from "react";
import { Check, Play, Plus, ThumbsDown, ThumbsUp, X } from "lucide-react";
import { toast } from "sonner";
import type { Title } from "../lib/catalog";
import { useUserData } from "../lib/userdata";
import { castFor, similarTo } from "../lib/recommend";
import { EpisodeList } from "./EpisodeList";


export default function DetailsModal({
  title,
  onClose,
  onPlay,
  onSelect,
}: {
  title: Title;
  onClose: () => void;
  onPlay?: () => void;
  onSelect?: (t: Title) => void;
}) {
  const inList = useUserData((s) => s.watchlist.includes(title.id));
  const toggleWatchlist = useUserData((s) => s.toggleWatchlist);
  const rating = useUserData((s) => s.ratings[title.id] ?? 0);
  const setRating = useUserData((s) => s.setRating);
  const progress = useUserData((s) => s.continueWatching[title.id]);
  const cast = useMemo(() => castFor(title), [title]);
  const similar = useMemo(() => similarTo(title, 10), [title]);


  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  const pct = progress && progress.duration > 0
    ? Math.min(100, Math.round((progress.position / progress.duration) * 100))
    : 0;

  return (
    <div
      className="fixed inset-0 z-[100] grid place-items-center bg-black/70 p-4 backdrop-blur-md animate-in fade-in duration-200"
      onClick={onClose}
    >
      <div
        className="relative flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-3xl glass-strong shadow-2xl animate-in zoom-in-95 duration-300"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="details-modal-title"
      >
        <div className="relative aspect-video shrink-0">
          <img
            src={title.backdrop}
            alt=""
            width={1920}
            height={1080}
            decoding="async"
            // No fetchPriority="high" — a modal opened mid-session is never the
            // page LCP candidate, and forcing high priority would steal
            // bandwidth from any in-flight critical resources (Hero backdrop,
            // route chunks, etc.). Default "auto" is correct here.
            className="h-full w-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-card via-card/30 to-transparent" />
          <button
            onClick={onClose}
            aria-label="Close details"
            className="absolute right-4 top-4 grid h-9 w-9 place-items-center rounded-full bg-black/50 text-foreground backdrop-blur transition hover:bg-black/70"
          >
            <X className="h-4 w-4" />
          </button>
          <div className="absolute inset-x-8 bottom-8">
            <h3 id="details-modal-title" className="font-display text-4xl font-bold tracking-[-0.03em] sm:text-5xl">
              {title.name}
            </h3>
            {pct > 0 && (
              <div className="mt-3 h-1 w-40 overflow-hidden rounded-full bg-white/20">
                <div className="h-full bg-brand" style={{ width: `${pct}%` }} />
              </div>
            )}
            <div className="mt-5 flex flex-wrap items-center gap-2">
              <button
                onClick={onPlay}
                className="inline-flex items-center gap-2 rounded-full bg-brand px-6 py-2.5 text-sm font-semibold text-brand-foreground shadow-[0_10px_30px_-8px_color-mix(in_oklab,var(--color-brand)_70%,transparent)] transition hover:brightness-110"
              >
                <Play className="h-4 w-4 fill-current" /> {pct > 0 ? "Resume" : "Play"}
              </button>
              <button
                onClick={() => {
                  const wasIn = inList;
                  toggleWatchlist(title.id);
                  toast.success(wasIn ? "Removed from My List" : "Added to My List");
                }}
                aria-pressed={inList}
                aria-label={inList ? "Remove from My List" : "Add to My List"}
                className="grid h-10 w-10 place-items-center rounded-full bg-white/10 backdrop-blur hover:bg-white/15"
              >
                {inList ? <Check className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
              </button>
              <button
                onClick={() => setRating(title.id, rating === 1 ? 0 : 1)}
                aria-pressed={rating === 1}
                aria-label="Thumbs up"
                className={`grid h-10 w-10 place-items-center rounded-full backdrop-blur ${
                  rating === 1 ? "bg-white text-black" : "bg-white/10 hover:bg-white/15"
                }`}
              >
                <ThumbsUp className="h-4 w-4" />
              </button>
              <button
                onClick={() => setRating(title.id, rating === -1 ? 0 : -1)}
                aria-pressed={rating === -1}
                aria-label="Thumbs down"
                className={`grid h-10 w-10 place-items-center rounded-full backdrop-blur ${
                  rating === -1 ? "bg-white text-black" : "bg-white/10 hover:bg-white/15"
                }`}
              >
                <ThumbsDown className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
        <div className="space-y-6 overflow-y-auto p-8">
          <div className="flex flex-wrap items-center gap-2 text-[12px] font-medium text-muted-foreground">
            <span className="rounded-md bg-white/10 px-1.5 py-0.5 text-[10px]">{title.rating}</span>
            <span>{title.year}</span>
            <span>·</span>
            <span>{title.duration}</span>
            <span>·</span>
            <span>{title.genres.join(" · ")}</span>
          </div>
          <p className="text-[15px] font-light leading-relaxed text-foreground/85">
            {title.description}
          </p>
          {title.kind === "series" && <EpisodeList series={title} onPlay={onPlay} />}

          {cast.length > 0 && (
            <section aria-labelledby="cast-heading" className="space-y-3">
              <h4 id="cast-heading" className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                Cast
              </h4>
              <ul className="flex flex-wrap gap-2">
                {cast.map((c) => (
                  <li
                    key={c.name}
                    className="flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-3 py-1.5"
                  >
                    <span
                      aria-hidden
                      className="grid h-6 w-6 place-items-center rounded-full bg-gradient-to-br from-white/20 to-white/5 text-[10px] font-semibold"
                    >
                      {c.name.split(" ").map((n) => n[0]).join("").slice(0, 2)}
                    </span>
                    <span className="text-[13px] font-medium text-foreground/90">{c.name}</span>
                    <span className="text-[11px] text-muted-foreground">· {c.role}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {similar.length > 0 && onSelect && (
            <section aria-labelledby="similar-heading" className="space-y-3">
              <h4 id="similar-heading" className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                More like this
              </h4>
              <div className="-mx-2 flex gap-3 overflow-x-auto px-2 pb-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                {similar.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => onSelect(t)}
                    className="group relative w-[200px] shrink-0 overflow-hidden rounded-xl border border-white/10 bg-white/[0.03] text-left transition hover:border-white/25"
                  >
                    <img
                      src={t.tile}
                      alt=""
                      loading="lazy"
                      decoding="async"
                      width={640}
                      height={360}
                      className="aspect-video w-full object-cover transition group-hover:brightness-110"
                    />
                    <div className="space-y-1 p-2.5">
                      <div className="truncate text-[13px] font-semibold">{t.name}</div>
                      <div className="text-[11px] text-muted-foreground">
                        {t.year} · {t.genres[0]}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

