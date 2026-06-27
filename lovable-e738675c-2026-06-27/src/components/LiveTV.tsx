import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Play, Radio, Search, Settings, Star } from "lucide-react";
import { HlsPlayer } from "./HlsPlayer";
import type { IPTVChannel } from "../lib/iptv";
import { fmtTime, nowNext } from "../lib/epg";

const LS_FAVS = "lumen.iptv.favorites.v1";

function loadFavs(): Set<string> {
  // SSR guard — function is invoked from a useState initializer which runs
  // during the server render too. Touching localStorage there crashes.
  if (typeof window === "undefined") return new Set();
  try {
    const raw = window.localStorage.getItem(LS_FAVS);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? new Set(arr.filter((x) => typeof x === "string")) : new Set();
  } catch {
    return new Set();
  }
}
function saveFavs(s: Set<string>) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(LS_FAVS, JSON.stringify(Array.from(s)));
  } catch {
    /* ignore */
  }
}

function LiveTV({
  channels,
  onOpenSettings,
  onPlayChannel,
}: {
  channels: IPTVChannel[];
  onOpenSettings: () => void;
  onPlayChannel?: (channel: IPTVChannel) => void;
}) {
  const [active, setActive] = useState<IPTVChannel | null>(channels[0] ?? null);
  const [playing, setPlaying] = useState(false);
  const [query, setQuery] = useState("");
  const [activeGroup, setActiveGroup] = useState<string>("all");
  const [epgTick, setEpgTick] = useState(0);
  const [favs, setFavs] = useState<Set<string>>(() => loadFavs());
  const playerRef = useRef<HTMLDivElement | null>(null);

  const toggleFav = useCallback((id: string) => {
    setFavs((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      saveFavs(next);
      return next;
    });
  }, []);

  useEffect(() => {
    if (!active || !channels.find((c) => c.id === active.id)) {
      setActive(channels[0] ?? null);
      setPlaying(false);
    }
  }, [channels, active]);

  // Reset group filter when the selected group disappears (e.g. playlist swapped),
  // or when the user empties their favorites while filtered to "favorites".
  useEffect(() => {
    if (activeGroup === "all") return;
    if (activeGroup === "favorites") {
      if (favs.size === 0) setActiveGroup("all");
      return;
    }
    const groups = new Set(channels.map((c) => c.group ?? "Live"));
    if (!groups.has(activeGroup)) setActiveGroup("all");
  }, [channels, activeGroup, favs]);

  // Refresh "Now / Next" every 30s so the EPG pill doesn't go stale.
  useEffect(() => {
    const id = window.setInterval(() => setEpgTick((t) => t + 1), 30_000);
    return () => window.clearInterval(id);
  }, []);


  const allGroups = useMemo(() => {
    const set = new Set<string>();
    for (const c of channels) set.add(c.group ?? "Live");
    return Array.from(set).sort();
  }, [channels]);

  const grouped = useMemo(() => {
    const q = query.trim().toLowerCase();
    const filtered = channels.filter((c) => {
      if (q && !c.name.toLowerCase().includes(q)) return false;
      if (activeGroup === "favorites") return favs.has(c.id);
      if (activeGroup !== "all" && (c.group ?? "Live") !== activeGroup) return false;
      return true;
    });
    const map = new Map<string, IPTVChannel[]>();
    for (const c of filtered) {
      const g = activeGroup === "favorites" ? "Favorites" : (c.group ?? "Live");
      if (!map.has(g)) map.set(g, []);
      map.get(g)!.push(c);
    }
    return Array.from(map.entries());
  }, [channels, query, activeGroup, favs]);

  // Top 6 channels for the "Now Playing" snapshot — favorites first, then list order.
  const nowPlayingPicks = useMemo(() => {
    const favList = channels.filter((c) => favs.has(c.id));
    const rest = channels.filter((c) => !favs.has(c.id));
    return [...favList, ...rest].slice(0, 6);
  }, [channels, favs]);

  const epg = useMemo(() => (active ? nowNext(active) : null), [active, epgTick]);

  const watchNow = useCallback(() => {
    if (onPlayChannel && active) {
      onPlayChannel(active);
      return;
    }
    setPlaying(true);
    requestAnimationFrame(() =>
      playerRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }),
    );
  }, [active, onPlayChannel]);

  // Stable handler — captures only `onPlayChannel`. `ChannelTile` is wrapped
  // in React.memo below, so a stable onClick prop means tiles do not
  // re-render when `active` changes (only the new/old active tile flip).
  const pickChannel = useCallback((ch: IPTVChannel) => {
    setActive(ch);
    if (onPlayChannel) {
      onPlayChannel(ch);
      return;
    }
    setPlaying(true);
    requestAnimationFrame(() =>
      playerRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }),
    );
  }, [onPlayChannel]);

  if (!active) {
    return (
      <div className="mx-auto flex min-h-[80vh] max-w-[1600px] flex-col items-center justify-center px-5 text-center sm:px-10">
        <div className="inline-flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.22em] text-muted-foreground sm:text-[11px]">
          <span className="h-1 w-1 rounded-full bg-foreground/70" /> Live TV
        </div>
        <h1 className="mt-5 font-display text-[36px] font-bold leading-[1] tracking-[-0.035em] sm:text-6xl">
          No channels loaded
        </h1>
        <p className="mt-4 max-w-md text-[14px] font-light text-muted-foreground sm:text-[15px]">
          Add an M3U / M3U8 playlist in Settings to start streaming.
        </p>
        <button
          onClick={onOpenSettings}
          className="mt-7 inline-flex items-center gap-2 rounded-full bg-foreground px-6 py-2.5 text-sm font-semibold text-background transition hover:bg-foreground/90"
        >
          <Settings className="h-4 w-4" /> Manage playlist
        </button>
      </div>
    );
  }

  return (
    <div>

      {/* Hero — matches home rhythm */}
      <section className="relative h-[72svh] min-h-[480px] w-full overflow-hidden sm:h-[92vh] sm:min-h-[640px]">
        <div
          className="absolute inset-0 bg-gradient-to-br"
          style={{
            background: `radial-gradient(900px 600px at 70% 30%, oklch(0.32 0.16 ${gradientHue(active.id)} / 0.55), transparent 65%), radial-gradient(700px 500px at 20% 80%, oklch(0.28 0.14 ${(gradientHue(active.id) + 60) % 360} / 0.45), transparent 60%), oklch(0.06 0.005 270)`,
          }}
        />
        <div className="pointer-events-none absolute inset-0 bg-gradient-to-r from-background via-background/70 to-transparent sm:via-background/60" />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-2/3 bg-gradient-to-t from-background via-background/40 to-transparent" />

        <div className="relative z-10 mx-auto flex h-full max-w-[1600px] flex-col justify-end px-5 pb-24 sm:px-10 sm:pb-32 2xl:max-w-[1920px]">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.22em] text-muted-foreground sm:text-[11px]">
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-red-500" />
              On Air · {active.group ?? "Live"}
            </div>
            <h1 className="mt-5 font-display text-[44px] font-bold leading-[0.95] tracking-[-0.035em] sm:mt-6 sm:text-6xl md:text-7xl lg:text-8xl 2xl:text-[9rem]">
              {active.name}
            </h1>
            {epg && (
              <div className="mt-6 inline-flex max-w-full flex-col gap-1 rounded-2xl border border-white/10 bg-black/30 px-4 py-2.5 backdrop-blur-xl sm:mt-7">
                <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-white/80">
                  <span className="rounded-full bg-red-500/90 px-1.5 py-px text-[9px] text-white">NOW</span>
                  <span className="truncate">{epg.now.title}</span>
                  <span className="text-muted-foreground">· until {fmtTime(epg.now.end)}</span>
                </div>
                <div className="flex items-center gap-2 text-[11px] text-white/55">
                  <span className="text-[9px] uppercase tracking-[0.16em]">Next</span>
                  <span className="truncate">{epg.next.title}</span>
                  <span>· {fmtTime(epg.next.start)}</span>
                </div>
              </div>
            )}
            <p className="mt-5 max-w-xl text-[15px] font-light leading-[1.55] text-foreground/80 sm:mt-6 sm:text-lg">
              Live from your IPTV playlist. {channels.length} channel
              {channels.length === 1 ? "" : "s"} ready to stream.
            </p>
            <div className="mt-7 flex flex-wrap items-center gap-2.5 sm:mt-9">
              <button
                onClick={watchNow}
                className="inline-flex items-center gap-2 rounded-full bg-foreground px-6 py-2.5 text-sm font-semibold tracking-[-0.01em] text-background transition-colors hover:bg-foreground/90 sm:px-7 sm:py-3"
              >
                <Play className="h-4 w-4 fill-current" /> Watch live
              </button>
              <button
                onClick={onOpenSettings}
                className="inline-flex items-center gap-2 rounded-full bg-white/10 px-6 py-2.5 text-sm font-semibold tracking-[-0.01em] text-foreground backdrop-blur-xl transition hover:bg-white/15 sm:px-7 sm:py-3"
              >
                <Settings className="h-4 w-4" /> Manage playlist
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* Player + browse rows — mirrors home main */}
      <main className="relative z-10 space-y-16 pb-24 pt-12 sm:space-y-20 sm:pb-28 sm:pt-20">

        {playing && (
          <section
            ref={playerRef}
            className="mx-auto max-w-[1600px] px-5 sm:px-10 2xl:max-w-[1920px]"
          >
            <HlsPlayer
              key={active.id}
              src={active.url}
              className="aspect-video w-full rounded-[16px] shadow-[0_30px_80px_-30px_rgba(0,0,0,0.9)] ring-1 ring-white/10"
              controls
            />
            <div className="mt-5 flex items-center justify-between gap-4 sm:mt-6">
              <div className="min-w-0">
                <div className="truncate font-display text-lg font-semibold tracking-[-0.02em] sm:text-xl">
                  {active.name}
                </div>
                <div className="mt-1.5 text-[10px] font-semibold uppercase tracking-[0.2em] text-muted-foreground sm:text-[11px]">
                  {active.group ?? "Live"} · Now streaming
                </div>
              </div>
            </div>
          </section>
        )}

        <section className="mx-auto max-w-[1600px] px-5 sm:px-10 2xl:max-w-[1920px]">
          <div className="flex flex-col gap-4">
            <div className="relative max-w-md">
              <label htmlFor="live-channel-search" className="sr-only">Search channels</label>
              <Search aria-hidden className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                id="live-channel-search"
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search channels"
                className="w-full rounded-full border border-border bg-card/70 py-2.5 pl-11 pr-4 text-sm placeholder:text-muted-foreground focus:border-foreground/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-foreground/40"
              />
            </div>
            {(allGroups.length > 1 || favs.size > 0) && (
              <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-hide">
                <GroupChip active={activeGroup === "all"} onClick={() => setActiveGroup("all")}>All</GroupChip>
                {favs.size > 0 && (
                  <GroupChip active={activeGroup === "favorites"} onClick={() => setActiveGroup("favorites")}>
                    <span className="inline-flex items-center gap-1.5">
                      <Star className="h-3 w-3 fill-current" />
                      Favorites · {favs.size}
                    </span>
                  </GroupChip>
                )}
                {allGroups.map((g) => (
                  <GroupChip key={g} active={activeGroup === g} onClick={() => setActiveGroup(g)}>{g}</GroupChip>
                ))}
              </div>
            )}
          </div>
        </section>

        {/* Now Playing overview — what's on across your channels right now */}
        {nowPlayingPicks.length > 0 && (
          <section className="mx-auto max-w-[1600px] px-5 sm:px-10 2xl:max-w-[1920px]">
            <div className="mb-4 flex items-end justify-between sm:mb-5">
              <div>
                <h2 className="font-display text-[19px] font-semibold tracking-[-0.02em] sm:text-[22px]">
                  On right now
                </h2>
                <p className="mt-1 text-[12px] leading-[1.4] text-muted-foreground sm:text-[13px]">
                  A peek at what's airing across {favs.size > 0 ? "your favorites and more" : "your playlist"}.
                </p>
              </div>
            </div>
            <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 sm:gap-3 lg:grid-cols-3">
              {nowPlayingPicks.map((ch) => {
                const e = nowNext(ch);
                const isActive = ch.id === active.id;
                return (
                  <button
                    key={ch.id}
                    type="button"
                    onClick={() => pickChannel(ch)}
                    className={`group flex items-center gap-3 rounded-2xl border px-3 py-2.5 text-left transition ${
                      isActive
                        ? "border-foreground/30 bg-white/10"
                        : "border-white/8 bg-white/[0.03] hover:bg-white/[0.07]"
                    }`}
                  >
                    <span
                      className="grid h-10 w-10 shrink-0 place-items-center rounded-lg text-[11px] font-semibold tracking-[-0.01em]"
                      style={{
                        background: `linear-gradient(135deg, oklch(0.22 0.08 ${gradientHue(ch.id)}), oklch(0.13 0.02 270))`,
                      }}
                    >
                      {ch.name.slice(0, 2).toUpperCase()}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="truncate text-[13px] font-medium">{ch.name}</span>
                        {favs.has(ch.id) && <Star className="h-3 w-3 shrink-0 fill-current text-amber-300" />}
                      </div>
                      <div className="mt-0.5 truncate text-[12px] text-muted-foreground">
                        <span className="text-foreground/85">{e.now.title}</span>
                        <span className="mx-1.5 text-white/30">·</span>
                        until {fmtTime(e.now.end)}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          </section>
        )}

        {grouped.length === 0 && (
          <div className="mx-auto max-w-[1600px] px-5 text-sm text-muted-foreground sm:px-10">
            No channels match.
          </div>
        )}

        {grouped.map(([group, list]) => (
          <section key={group} className="mx-auto max-w-[1600px] px-5 sm:px-10 2xl:max-w-[1920px]">
            <div className="mb-4 flex items-end justify-between sm:mb-6">
              <h2 className="font-display text-[19px] font-semibold tracking-[-0.02em] sm:text-[26px] 2xl:text-[30px]">
                {group}
              </h2>
              <span className="text-xs font-medium text-muted-foreground">
                {list.length} channel{list.length === 1 ? "" : "s"}
              </span>
            </div>
            <div className="-mx-5 -my-8 flex gap-3 overflow-x-auto overflow-y-visible px-5 py-8 sm:-mx-2 sm:gap-5 sm:px-2 scrollbar-hide">
              {list.map((ch) => (
                <ChannelTile
                  key={ch.id}
                  channel={ch}
                  active={ch.id === active.id}
                  onPick={pickChannel}
                  isFavorite={favs.has(ch.id)}
                  onToggleFavorite={toggleFav}
                />
              ))}
            </div>
          </section>
        ))}
      </main>

    </div>
  );
}

function GroupChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`shrink-0 rounded-full px-3.5 py-1.5 text-[12px] font-medium transition ${
        active
          ? "bg-foreground text-background"
          : "border border-white/10 bg-white/5 text-muted-foreground hover:bg-white/10 hover:text-foreground"
      }`}
    >
      {children}
    </button>
  );
}

function gradientHue(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) % 360;
  return h;
}

const ChannelTile = memo(function ChannelTile({
  channel,
  active,
  onPick,
  isFavorite,
  onToggleFavorite,
}: {
  channel: IPTVChannel;
  active: boolean;
  onPick: (channel: IPTVChannel) => void;
  isFavorite?: boolean;
  onToggleFavorite?: (id: string) => void;
}) {
  const hue = gradientHue(channel.id);
  const [logoFailed, setLogoFailed] = useState(false);
  // Stable per-instance handler so memo'd children downstream don't churn.
  const handleClick = useCallback(() => onPick(channel), [onPick, channel]);
  const handleFav = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onToggleFavorite?.(channel.id);
    },
    [onToggleFavorite, channel.id],
  );
  return (
    <div className="group relative w-[68%] shrink-0 snap-start xs:w-[58%] sm:w-[34%] md:w-[26%] lg:w-[20%] xl:w-[16%] 2xl:w-[14%]">
      <button
        type="button"
        onClick={handleClick}
        aria-label={`Play ${channel.name}${channel.group ? `, ${channel.group}` : ""}`}
        aria-current={active ? "true" : undefined}
        className="poster-card block w-full text-left"
      >
        <div
          className={`tv-tile relative aspect-video overflow-hidden rounded-[12px] bg-card shadow-[0_8px_24px_-12px_rgba(0,0,0,0.55)] ${
            active ? "ring-2 ring-foreground" : "ring-1 ring-border"
          }`}
          style={{
            background: `linear-gradient(135deg, oklch(0.18 0.035 ${hue} / 0.95), oklch(0.105 0.012 270))`,
          }}
        >
          <div className="absolute inset-0 bg-gradient-to-br from-foreground/[0.05] to-transparent" />
          <div className="absolute inset-0 grid place-items-center">
            {channel.logo && !logoFailed ? (
              <img
                src={channel.logo}
                alt=""
                loading="lazy"
                decoding="async"
                onError={() => setLogoFailed(true)}
                className="max-h-[50%] max-w-[58%] object-contain drop-shadow-lg"
              />
            ) : (
              <div className="flex flex-col items-center gap-2">
                <span className="grid h-9 w-9 place-items-center rounded-full bg-foreground/10 ring-1 ring-foreground/10">
                  <Radio className="h-4 w-4 text-foreground/70" strokeWidth={1.6} />
                </span>
                <span className="font-display text-[19px] font-semibold leading-none">
                  {channel.name.slice(0, 2).toUpperCase()}
                </span>
              </div>
            )}
          </div>
          <div className="absolute inset-0 grid place-items-center bg-background/35 opacity-0 transition-opacity duration-200 group-hover:opacity-100">
            <div className="grid h-11 w-11 place-items-center rounded-full bg-foreground text-background shadow-lg">
              <Play className="h-4 w-4 fill-current" />
            </div>
          </div>
          {active && (
            <span className="absolute right-2 top-2 inline-flex items-center gap-1 rounded-full bg-foreground px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-background">
              <span className="h-1.5 w-1.5 rounded-full bg-background" />
              Live
            </span>
          )}
        </div>
        <div className="space-y-1.5 px-0.5 pt-4">
          <div className="truncate text-[13px] font-medium leading-[1.35] tracking-[-0.005em] text-foreground">
            {channel.name}
          </div>
          <div className="text-[11.5px] font-normal leading-[1.3] text-muted-foreground">
            {channel.group ?? "Live"}
          </div>
        </div>
      </button>
      {onToggleFavorite && (
        <button
          type="button"
          onClick={handleFav}
          aria-pressed={!!isFavorite}
          aria-label={isFavorite ? `Remove ${channel.name} from favorites` : `Add ${channel.name} to favorites`}
          className={`absolute left-2 top-2 z-10 grid h-8 w-8 place-items-center rounded-full bg-black/55 backdrop-blur-md transition ${
            isFavorite
              ? "text-amber-300 opacity-100"
              : "text-white/75 opacity-0 hover:bg-black/75 hover:text-white group-hover:opacity-100 focus-visible:opacity-100"
          }`}
        >
          <Star className={`h-4 w-4 ${isFavorite ? "fill-current" : ""}`} />
        </button>
      )}
    </div>
  );
});


export default memo(LiveTV);
