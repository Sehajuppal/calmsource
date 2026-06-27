import { createFileRoute, Link } from "@tanstack/react-router";
import { memo, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { lazyWithRetry } from "../lib/lazy-retry";
import {
  Play,
  Plus,
  Info,
  Search,
  ChevronRight,
  ChevronLeft,
  Tv,
  Film,
  Sparkles,
  Radio,
  Settings,
  Bookmark,
  X,
} from "lucide-react";
import type { Title, Mood, LeavingTitle } from "../lib/catalog";
import type { IPTVChannel } from "../lib/iptv";
import {
  catalogRepository,
  channelRepository,
  playbackResolver,
} from "../lib/repositories";
import {
  DEFAULT_PROFILES,
  LS_ACTIVE_PROFILE,
  LS_PROFILES,
  PROFILE_AVATARS,
  getProfileAvatarUrl,
  loadProfilesFromStorage,
  type Profile,
} from "../lib/profiles";



import { ErrorBoundary } from "../components/ErrorBoundary";
import { useUserData, selectContinue } from "../lib/userdata";
import { usePlayer } from "../lib/player-store";
import { FilterBar, type SortMode } from "../components/FilterBar";
import { personalizedPicks } from "../lib/recommend";
import { useExtensionCatalog } from "../hooks/use-extension-catalog";
import { useStreamPicker } from "../lib/stream-picker-store";
import { StreamPicker } from "../components/StreamPicker";

// Repository-backed module constants. Today the repo returns fake data;
// swapping the backend is a one-file change in src/lib/repositories.ts.
const FEATURED = catalogRepository.getFeatured();
const FILMS_FEATURED = catalogRepository.getFilmsFeatured();
const SERIES_FEATURED = catalogRepository.getSeriesFeatured();
const ROWS = catalogRepository.getHomeRows();
const FILMS = catalogRepository.getFilms();
const SERIES = catalogRepository.getSeries();
const FILM_ROWS = catalogRepository.getFilmRows();
const SERIES_ROWS = catalogRepository.getSeriesRows();
const TOP_TEN = catalogRepository.getTopTen();
const LEAVING_SOON = catalogRepository.getLeavingSoon();
const MOODS = catalogRepository.getMoods();
const titlesForMood = (mood: Mood) =>
  catalogRepository.getTitlesForMood(mood.id);
const SAMPLE_CHANNELS = channelRepository.getChannels();


const LiveTV = lazyWithRetry(() => import("../components/LiveTV"), "LiveTV");
const DetailsModal = lazyWithRetry(() => import("../components/DetailsModal"), "DetailsModal");
const SettingsPanel = lazyWithRetry(() => import("../components/SettingsPanel"), "SettingsPanel");
const ProfileGate = lazyWithRetry(() => import("../components/ProfileGate"), "ProfileGate");
const Player = lazyWithRetry(() => import("../components/Player"), "Player");
const SearchCommand = lazyWithRetry(() => import("../components/SearchCommand"), "SearchCommand");

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "CalmSource — Films, series & live TV" },
      {
        name: "description",
        content:
          "A cinematic streaming experience with curated films, original series, and live IPTV channels in one place.",
      },
      { property: "og:title", content: "CalmSource — Films, series & live TV" },
      {
        property: "og:description",
        content:
          "Curated films, original series, and live IPTV channels in one cinematic interface.",
      },
      { property: "og:image", content: FEATURED.backdrop },
    ],
    links: [
      { rel: "preload", as: "image", href: FEATURED.backdrop, fetchPriority: "high" },
    ],
  }),
  component: HomePage,
});

type Tab = "home" | "mystuff" | "movies" | "series" | "live";

const LS_KEY = "lumen.iptv.channels.v1";

function HomePage() {
  const [tab, setTab] = useState<Tab>("home");
  const [selected, setSelected] = useState<Title | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [channels, setChannels] = useState<IPTVChannel[]>(SAMPLE_CHANNELS);
  const [profiles, setProfiles] = useState<Profile[]>(DEFAULT_PROFILES);
  const [activeProfile, setActiveProfile] = useState<Profile | null>(null);
  // Init to `true` so the gate is rendered immediately on first paint
  // when no active profile is stored, avoiding a one-frame flash of the
  // main app before the hydration effect flips it.
  const [showProfileGate, setShowProfileGate] = useState(true);

  const hydrate = useUserData((s) => s.hydrate);
  const continueMap = useUserData((s) => s.continueWatching);
  const themePref = useUserData((s) => s.preferences.theme);
  const dyslexiaFont = useUserData((s) => s.preferences.dyslexiaFont);
  const continueEntries = useMemo(
    () => selectContinue({ continueWatching: continueMap } as Parameters<typeof selectContinue>[0]),
    [continueMap],
  );
  const watchlistIds = useUserData((s) => s.watchlist);
  const openPlayer = usePlayer((s) => s.open);

  useEffect(() => {
    const isChannel = (x: unknown): x is IPTVChannel =>
      !!x && typeof x === "object" &&
      typeof (x as IPTVChannel).id === "string" &&
      typeof (x as IPTVChannel).name === "string" &&
      typeof (x as IPTVChannel).url === "string" &&
      /^(https?|rtmp|rtsp|udp|mms):\/\//i.test((x as IPTVChannel).url);
    try {
      const raw = localStorage.getItem(LS_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
          const safe = parsed.filter(isChannel).slice(0, 5000);
          if (safe.length) setChannels(safe);
        }
      }
    } catch {
      /* ignore */
    }

    const loadedState = loadProfilesFromStorage();
    const loaded: Profile[] = (loadedState?.profiles ?? DEFAULT_PROFILES).slice(0, 10);
    setProfiles(loaded);

    const activeId = loadedState?.activeId ?? null;
    const match = activeId ? loaded.find((p: Profile) => p.id === activeId) : null;
    if (match) {
      setActiveProfile(match);
      hydrate(match.id);
      setShowProfileGate(false);
    } else setShowProfileGate(true);
  }, [hydrate]);

  // Apply theme variant to <html data-theme="…">
  useEffect(() => {
    const root = document.documentElement;
    if (!themePref || themePref === "system" || themePref === "midnight") {
      root.removeAttribute("data-theme");
    } else {
      root.setAttribute("data-theme", themePref);
    }
  }, [themePref]);

  // Dyslexia-friendly font opt-in (Accessibility setting).
  useEffect(() => {
    const root = document.documentElement;
    if (dyslexiaFont) root.setAttribute("data-font", "dyslexia");
    else root.removeAttribute("data-font");
  }, [dyslexiaFont]);

  // Warm hls.js during idle time so the first stream play is instant —
  // otherwise the dynamic import happens on click and adds ~150ms of parse.
  useEffect(() => {
    const w = window as Window & {
      requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number;
    };
    const schedule = w.requestIdleCallback ?? ((cb: () => void) => window.setTimeout(cb, 2000));
    const handle = schedule(() => {
      import("hls.js").catch(() => {/* fine — falls back to on-demand load */});
    }, { timeout: 4000 });
    return () => {
      const cancel = (window as Window & { cancelIdleCallback?: (h: number) => void })
        .cancelIdleCallback;
      if (cancel) cancel(handle as number);
      else window.clearTimeout(handle as number);
    };
  }, []);




  const persistProfiles = useCallback((next: Profile[]) => {
    setProfiles(next);
    setActiveProfile((current) => {
      if (!current) return current;
      const updated = next.find((profile) => profile.id === current.id) ?? current;
      try {
        localStorage.setItem(LS_ACTIVE_PROFILE, updated.id);
      } catch {
        /* ignore */
      }
      return updated;
    });
    try {
      localStorage.setItem(LS_PROFILES, JSON.stringify(next));
    } catch {
      /* ignore */
    }
  }, []);

  const handlePickProfile = useCallback((p: Profile) => {
    setActiveProfile(p);
    setShowProfileGate(false);
    hydrate(p.id);
    try {
      localStorage.setItem(LS_ACTIVE_PROFILE, p.id);
    } catch {
      /* ignore */
    }
  }, [hydrate]);

  const handleAddProfile = useCallback(() => {
    if (profiles.length >= 10) return;
    const palette = [
      "from-indigo-400 to-fuchsia-500",
      "from-amber-300 to-rose-500",
      "from-emerald-300 to-cyan-500",
      "from-sky-300 to-violet-500",
      "from-pink-400 to-orange-400",
    ];
    const next: Profile[] = [
      ...profiles,
      {
        id: `p${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
        name: `Profile ${profiles.length + 1}`,
        color: palette[profiles.length % palette.length],
        avatarId: PROFILE_AVATARS[profiles.length % PROFILE_AVATARS.length]?.id,
      },
    ];
    persistProfiles(next);
  }, [profiles, persistProfiles]);

  const handleRenameProfile = useCallback(
    (id: string, name: string) => {
      const next = profiles.map((p) => (p.id === id ? { ...p, name } : p));
      persistProfiles(next);
    },
    [profiles, persistProfiles],
  );

  const handleAvatarChange = useCallback(
    (id: string, avatarId: string) => {
      const next = profiles.map((p) => (p.id === id ? { ...p, avatarId } : p));
      persistProfiles(next);
    },
    [profiles, persistProfiles],
  );

  const handlePinChange = useCallback(
    (id: string, pin: string | null) => {
      const next = profiles.map((p) => {
        if (p.id !== id) return p;
        const copy = { ...p };
        if (pin) copy.pin = pin;
        else delete copy.pin;
        return copy;
      });
      persistProfiles(next);
    },
    [profiles, persistProfiles],
  );

  const switchProfile = useCallback(() => setShowProfileGate(true), []);
  const openSettings = useCallback(() => setSettingsOpen(true), []);
  const closeSettings = useCallback(() => setSettingsOpen(false), []);
  const closeDetails = useCallback(() => setSelected(null), []);
  const openSearch = useCallback(() => setSearchOpen(true), []);

  const titleById = useMemo(() => {
    const map = new Map<string, Title>();
    for (const t of [FEATURED, ...FILMS, ...SERIES]) map.set(t.id, t);
    return map;
  }, []);

  const continueRow = useMemo(
    () =>
      continueEntries
        .map((e) => titleById.get(e.id))
        .filter((x): x is Title => !!x),
    [continueEntries, titleById],
  );

  const watchlistRow = useMemo(
    () => watchlistIds.map((id) => titleById.get(id)).filter((x): x is Title => !!x),
    [watchlistIds, titleById],
  );

  const historyIds = useUserData((s) => s.history);
  const ratings = useUserData((s) => s.ratings);
  const picksRow = useMemo(
    () =>
      personalizedPicks({
        history: historyIds,
        watchlist: watchlistIds,
        ratings,
        continueIds: continueEntries.map((e) => e.id),
      }),
    [historyIds, watchlistIds, ratings, continueEntries],
  );


  const requestPlay = useStreamPicker((s) => s.requestPlay);
  const playTitle = useCallback(
    (t: Title) => {
      // requestPlay routes extension-backed titles (namespaced ids) through
      // the StreamPicker and falls back to direct playback for local titles.
      void requestPlay(t);
    },
    [requestPlay],
  );

  const playChannel = useCallback(
    (c: IPTVChannel) =>
      openPlayer({ kind: "live", channelId: c.id, name: c.name, streamUrl: playbackResolver.resolveLive(c) }),
    [openPlayer],
  );

  return (
    <div className="min-h-dvh pb-mobile-nav text-foreground">
      <a href="#main-content" className="skip-link">Skip to content</a>
      <TopNav
        tab={tab}
        setTab={setTab}
        onOpenSettings={openSettings}
        onOpenSearch={openSearch}
        activeProfile={activeProfile}
        onSwitchProfile={switchProfile}
      />

      {tab === "live" ? (
        <ErrorBoundary label="live_tv">
          <Suspense fallback={<div role="status" aria-live="polite" className="pt-40 text-center text-muted-foreground">Loading…</div>}>
            <main id="main-content">
              <LiveTV channels={channels} onOpenSettings={openSettings} onPlayChannel={playChannel} />
            </main>
          </Suspense>
        </ErrorBoundary>
      ) : tab === "mystuff" ? (
        <main id="main-content" className="relative z-10 mx-auto max-w-[1600px] space-y-16 px-5 pb-24 pt-32 sm:space-y-20 sm:px-10 sm:pb-28 sm:pt-36">
          <header className="space-y-3">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.18em] text-white/80">
              <Bookmark className="h-3.5 w-3.5" />
              My List
            </div>
            <h1 className="font-display text-[clamp(2.2rem,5vw,3.6rem)] font-semibold tracking-[-0.035em]">
              {continueRow.length > 0 ? "Pick up where you left off." : "Your saved corner of CalmSource."}
            </h1>
            <p className="max-w-[60ch] text-[14px] leading-[1.6] text-muted-foreground sm:text-[15px]">
              Everything you've started, saved, or finished — all in one place.
            </p>
          </header>
          <ErrorBoundary label="mystuff_rows">
            {continueRow.length > 0 ? (
              <Row title="Continue Watching" reason="Resume from where you stopped." items={continueRow} onSelect={setSelected} />
            ) : (
              <EmptyState
                icon={<Play className="h-5 w-5 fill-current" />}
                title="Nothing in progress"
                body="Start something on the Home tab and we'll save your place here automatically."
                ctaLabel="Browse Home"
                onCta={() => setTab("home")}
              />
            )}
            {watchlistRow.length > 0 ? (
              <Row title="My List" reason="Titles you saved for later." items={watchlistRow} onSelect={setSelected} />
            ) : (
              <EmptyState
                icon={<Bookmark className="h-5 w-5" />}
                title="Your list is empty"
                body="Tap the bookmark on any title to save it here. We won't forget."
                ctaLabel="Find something to save"
                onCta={() => setTab("movies")}
              />
            )}
          </ErrorBoundary>
        </main>
      ) : tab === "movies" ? (
        <>
          <CategoryHero
            eyebrow="Films"
            title="Cinema, on your time."
            description="Hand-picked films from emerging directors and modern classics — in pristine 4K HDR."
            featured={FILMS_FEATURED}
            onPlay={() => playTitle(FILMS_FEATURED)}
            onMoreInfo={() => setSelected(FILMS_FEATURED)}
          />
          <main id="main-content" className="relative z-10 space-y-12 pb-24 pt-10 sm:space-y-16 sm:pb-28 sm:pt-14">
            <CatalogBrowser items={FILMS} rows={FILM_ROWS} onSelect={setSelected} label="film" />
          </main>
        </>
      ) : tab === "series" ? (
        <>
          <CategoryHero
            eyebrow="Series"
            title="Stories worth staying up for."
            description="Original series and complete seasons — premiering weekly on CalmSource."
            featured={SERIES_FEATURED}
            onPlay={() => playTitle(SERIES_FEATURED)}
            onMoreInfo={() => setSelected(SERIES_FEATURED)}
          />
          <main id="main-content" className="relative z-10 space-y-12 pb-24 pt-10 sm:space-y-16 sm:pb-28 sm:pt-14">
            <CatalogBrowser items={SERIES} rows={SERIES_ROWS} onSelect={setSelected} label="series" />
          </main>
        </>
      ) : (
        <>
          <HomeTab
            continueRow={continueRow}
            watchlistRow={watchlistRow}
            picksRow={picksRow}
            liveChannels={channels}
            onSelect={setSelected}
            onPlay={playTitle}
            onPlayChannel={playChannel}
            onOpenSettings={openSettings}
            onOpenLive={() => setTab("live")}
          />
        </>
      )}

      <Suspense fallback={null}>
        {selected ? (
          <DetailsModal
            title={selected}
            onClose={closeDetails}
            onPlay={() => {
              const t = selected;
              closeDetails();
              playTitle(t);
            }}
            onSelect={setSelected}
          />

        ) : null}
        {settingsOpen ? (
          <SettingsPanel
            channels={channels}
            setChannels={setChannels}
            onClose={closeSettings}
          />
        ) : null}
        <Player />
        <StreamPicker />
        <SearchCommand
          channels={channels}
          onPickTitle={(t: Title) => setSelected(t)}
          onPickChannel={(c: IPTVChannel) => playChannel(c)}
          open={searchOpen}
          setOpen={setSearchOpen}
        />
      </Suspense>

      {showProfileGate ? (
        <Suspense fallback={<div className="fixed inset-0 z-[110] bg-background" aria-hidden />}>
          <ProfileGate
            profiles={profiles}
            onSelect={handlePickProfile}
            onAdd={handleAddProfile}
            onRename={handleRenameProfile}
            onAvatarChange={handleAvatarChange}
            onPinChange={handlePinChange}
          />
        </Suspense>
      ) : null}
    </div>
  );
}

const TABS = [
  { id: "home" as const, label: "Home", icon: Sparkles },
  { id: "mystuff" as const, label: "My List", icon: Bookmark },
  { id: "movies" as const, label: "Movies", icon: Film },
  { id: "series" as const, label: "Series", icon: Tv },
  { id: "live" as const, label: "Live TV", icon: Radio },
];

function TopNav({
  tab,
  setTab,
  onOpenSettings,
  onOpenSearch,
  activeProfile,
  onSwitchProfile,
}: {
  tab: Tab;
  setTab: (t: Tab) => void;
  onOpenSettings: () => void;
  onOpenSearch: () => void;
  activeProfile: Profile | null;
  onSwitchProfile: () => void;
}) {
  const [scrolled, setScrolled] = useState(false);
  const activeAvatarUrl = activeProfile ? getProfileAvatarUrl(activeProfile) : null;

  useEffect(() => {
    let ticking = false;
    let current = scrolled;
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        const next = window.scrollY > 16;
        if (next !== current) {
          current = next;
          setScrolled(next);
        }
        ticking = false;
      });
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <header
        className={`fixed inset-x-0 top-0 z-50 safe-top transition-all duration-500 ${
          scrolled ? "glass-strong" : ""
        }`}
      >
        <div className="mx-auto flex h-16 max-w-[1600px] items-center gap-6 px-4 sm:gap-10 sm:px-10">
          <Link to="/" aria-label="CalmSource home" className="flex items-center gap-2.5">
            <div className="grid h-7 w-7 shrink-0 place-items-center rounded-[8px] bg-gradient-to-br from-white to-white/70 text-background shadow-lg">
              <span aria-hidden className="font-display text-[13px] font-black tracking-[-0.04em]">L</span>
            </div>
            <span className="font-display text-[17px] font-semibold tracking-[-0.02em]">
              CalmSource
            </span>
          </Link>

          <nav aria-label="Primary" className="adaptive-nav hidden items-center gap-1 md:flex">
            {TABS.map((t) => {
              const Icon = t.icon;
              const active = tab === t.id;
              return (
                <button
                  key={t.id}
                  onClick={() => setTab(t.id)}
                  aria-current={active ? "page" : undefined}
                  className={`relative inline-flex min-h-9 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 py-1.5 text-[13px] font-medium transition-colors ${
                    active ? "adaptive-fg-strong" : "adaptive-fg-muted hover:adaptive-fg-strong"
                  }`}
                >
                  <Icon aria-hidden className="h-3.5 w-3.5" />
                  {t.label}
                  {active && (
                    <span aria-hidden className="absolute inset-0 -z-10 rounded-full bg-white/10 ring-1 ring-white/10" />
                  )}
                </button>
              );
            })}
          </nav>

          <div className="ml-auto flex items-center gap-1">
            <button
              type="button"
              onClick={onOpenSearch}
              aria-label="Search (⌘K)"
              title="Search (⌘K)"
              className="grid h-11 w-11 place-items-center rounded-full text-muted-foreground transition hover:bg-white/10 hover:text-foreground sm:h-10 sm:w-10"
            >
              <Search aria-hidden className="h-[18px] w-[18px]" strokeWidth={1.75} />
            </button>
            <button
              type="button"
              onClick={onOpenSettings}
              className="grid h-11 w-11 place-items-center rounded-full text-muted-foreground transition hover:bg-white/10 hover:text-foreground sm:h-10 sm:w-10"
              aria-label="Open settings"
            >
              <Settings aria-hidden className="h-[18px] w-[18px]" strokeWidth={1.75} />
            </button>
            <button
              type="button"
              onClick={onSwitchProfile}
              aria-label={activeProfile ? `Switch profile, currently ${activeProfile.name}` : "Choose profile"}
              className="profile-avatar ml-1 grid h-9 w-9 place-items-center overflow-hidden rounded-full ring-2 ring-white/10"
            >
              {activeAvatarUrl ? (
                <img src={activeAvatarUrl} alt="" className="h-full w-full object-cover" />
              ) : (
                <span
                  aria-hidden
                  className={`grid h-full w-full place-items-center bg-gradient-to-br ${activeProfile?.color ?? "from-indigo-400 to-fuchsia-500"} font-display text-[13px] font-bold tracking-[-0.02em] text-white`}
                >
                  {(activeProfile?.name ?? "·").trim().charAt(0).toUpperCase()}
                </span>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Mobile bottom tab bar — fixed, safe-area aware, always reachable */}
      <nav
        aria-label="Primary mobile"
        className="fixed inset-x-0 bottom-0 z-50 glass-strong border-t border-white/10 md:hidden"
        style={{ paddingBottom: "env(safe-area-inset-bottom, 0px)" }}
      >
        <ul className="mx-auto flex max-w-[640px] items-stretch justify-around px-2 pt-1.5">
          {TABS.map((t) => {
            const Icon = t.icon;
            const active = tab === t.id;
            return (
              <li key={t.id} className="flex-1">
                <button
                  type="button"
                  onClick={() => setTab(t.id)}
                  aria-current={active ? "page" : undefined}
                  aria-label={t.label}
                  className={`relative flex min-h-14 w-full flex-col items-center justify-center gap-0.5 rounded-xl px-2 py-1.5 text-[10.5px] font-medium tracking-[0.01em] transition-colors ${
                    active ? "text-foreground" : "text-muted-foreground"
                  }`}
                >
                  <Icon
                    aria-hidden
                    className={`h-[22px] w-[22px] transition-transform ${active ? "scale-105" : ""}`}
                    strokeWidth={active ? 2.25 : 1.75}
                  />
                  <span>{t.label}</span>
                  {active && (
                    <span
                      aria-hidden
                      className="absolute -top-px left-1/2 h-[2px] w-8 -translate-x-1/2 rounded-full bg-foreground"
                    />
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </nav>
    </>
  );
}

// Editorial reasons cycled per hero slide — sells *why* this title is featured today.
const HERO_REASONS = [
  "Featured today",
  "Trending #1",
  "New this week",
  "Top 10 on CalmSource",
  "Critics' pick",
  "Hidden gem",
  "Hot right now",
];

function Hero({ onSelect, onPlay }: { onSelect: (title: Title) => void; onPlay: (title: Title) => void }) {
  const slides = useMemo(() => [FEATURED, ...ROWS.flatMap((row) => row.items).slice(0, 5)], []);
  const [index, setIndex] = useState(0);
  const [prevIndex, setPrevIndex] = useState<number | null>(null);
  const [paused, setPaused] = useState(false);
  const [offscreen, setOffscreen] = useState(false);
  const sectionRef = useRef<HTMLElement | null>(null);

  // Pause auto-advance when the hero scrolls out of view — saves CPU,
  // avoids hidden image decodes, and resets the progress bar smoothly.
  useEffect(() => {
    const node = sectionRef.current;
    if (!node || typeof IntersectionObserver === "undefined") return;
    const io = new IntersectionObserver(
      ([entry]) => setOffscreen(!entry.isIntersecting),
      { threshold: 0.15 },
    );
    io.observe(node);
    return () => io.disconnect();
  }, []);

  const goTo = useCallback((next: number) => {
    setIndex((current) => {
      if (current === next) return current;
      setPrevIndex(current);
      return next;
    });
  }, []);

  // setTimeout (not setInterval): cleanly restarts the 11s window whenever
  // index changes — including manual dot clicks — matching the progress bar.
  useEffect(() => {
    if (paused || offscreen) return;
    const id = window.setTimeout(() => {
      goTo((index + 1) % slides.length);
    }, 11000);
    return () => window.clearTimeout(id);
  }, [paused, offscreen, slides.length, index, goTo]);

  // Warm the next slide's backdrop so the crossfade has no decode jank.
  useEffect(() => {
    const next = slides[(index + 1) % slides.length];
    if (!next) return;
    const img = new Image();
    img.decoding = "async";
    img.src = next.backdrop;
  }, [index, slides]);

  const active = slides[index] ?? FEATURED;
  const openActive = () => onSelect(active);

  const reason = HERO_REASONS[index % HERO_REASONS.length];

  return (
    <section
      ref={sectionRef}
      className="relative isolate min-h-[54svh] overflow-hidden sm:min-h-[74svh]"
      onPointerEnter={(e) => { if (e.pointerType !== "touch") setPaused(true); }}
      onPointerLeave={(e) => { if (e.pointerType !== "touch") setPaused(false); }}
    >
      <div className="absolute inset-0">
        {slides.map((slide, slideIndex) => {
          // Mount: active slide, the outgoing slide (for crossfade after a
          // multi-step jump), and the next neighbor (pre-decoded for auto-advance).
          const isActive = slideIndex === index;
          const isPrev = slideIndex === prevIndex;
          const isNext = slideIndex === (index + 1) % slides.length;
          if (!isActive && !isPrev && !isNext) return null;
          return (
            <img
              key={slide.id}
              src={slide.backdrop}
              alt={slide.name}
              width={1920}
              height={1080}
              decoding="async"
              loading={slideIndex === 0 ? "eager" : "lazy"}
              fetchPriority={slideIndex === 0 ? "high" : "auto"}
              className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-[1400ms] ${
                isActive ? "opacity-100" : "opacity-0"
              }`}
              // Only hint the compositor for the active and outgoing slides —
              // keeping willChange on idle slides pins GPU layers we never
              // animate, wasting VRAM on low-end phones / TVs.
              style={{
                transform: isActive ? "scale(1.04)" : "scale(1)",
                willChange: isActive || isPrev ? "transform, opacity" : "auto",
              }}
            />
          );
        })}

      </div>
      <div className="hero-scrim absolute inset-0" aria-hidden />


      <div className="relative z-10 mx-auto flex min-h-[54svh] max-w-[1600px] items-end px-5 pb-12 pt-24 sm:min-h-[74svh] sm:px-10 sm:pb-20 sm:pt-32">
        <div className="max-w-2xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-black/30 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.18em] text-white/90 backdrop-blur-xl">
            <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-red-500 animate-pulse" />
            {reason}
            <span aria-hidden className="text-white/40">·</span>
            <span className="text-white/65">{active.kind === "series" ? "Series" : "Film"}</span>
          </div>
          <h1 className="mt-5 max-w-[14ch] font-display text-[clamp(2.6rem,8vw,6.6rem)] font-semibold leading-[0.94] tracking-[-0.045em] text-white">
            {active.name}
          </h1>



          <p className="mt-5 max-w-[56ch] text-[15px] leading-[1.6] text-white/72 sm:text-[16px]">
            {active.description}
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <button
              onClick={() => onPlay(active)}
              className="inline-flex min-h-11 items-center gap-2 rounded-full bg-brand px-5 py-3 text-[13px] font-semibold text-brand-foreground shadow-[0_10px_30px_-8px_color-mix(in_oklab,var(--color-brand)_70%,transparent)] transition hover:brightness-110"
            >
              <Play className="h-4 w-4 fill-current" />
              Play now
            </button>
            <button
              onClick={openActive}
              className="inline-flex min-h-11 items-center gap-2 rounded-full border border-white/14 bg-white/8 px-5 py-3 text-[13px] font-medium text-white backdrop-blur-2xl transition hover:bg-white/12"
            >
              <Info className="h-4 w-4" />
              More info
            </button>
          </div>
          <div className="mt-10 flex items-center gap-2.5">
            {slides.map((slide, slideIndex) => (
              <button
                key={slide.id}
                aria-label={`Go to ${slide.name}`}
                onClick={() => goTo(slideIndex)}
                className={`relative h-1.5 overflow-hidden rounded-full transition-all ${
                  slideIndex === index ? "w-12 bg-white/25" : "w-6 bg-white/10"
                }`}
              >
                {slideIndex === index ? <span key={index} className="absolute inset-y-0 left-0 w-full animate-[hero-progress_11s_linear] rounded-full bg-brand" style={{ animationPlayState: (paused || offscreen) ? "paused" : "running" }} /> : null}
              </button>
            ))}

            <button
              type="button"
              onClick={() => setPaused((value) => !value)}
              className="ml-1 rounded-full border border-white/12 bg-white/8 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.16em] text-white/75 backdrop-blur-xl transition hover:bg-white/12"
            >
              {paused ? "Resume" : "Pause"}
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}

function CategoryHero({
  eyebrow,
  title,
  description,
  featured,
  onPlay,
  onMoreInfo,
}: {
  eyebrow: string;
  title: string;
  description: string;
  featured: Title;
  onPlay: () => void;
  onMoreInfo: () => void;
}) {
  return (
    <section className="relative isolate min-h-[62svh] overflow-hidden">
      <img src={featured.backdrop} alt={featured.name} width={1920} height={1080} decoding="async" loading="eager" fetchPriority="high" className="absolute inset-0 h-full w-full object-cover" />
      <div className="hero-scrim absolute inset-0" aria-hidden />

      <div className="relative z-10 mx-auto flex min-h-[62svh] max-w-[1600px] items-end px-5 pb-14 pt-28 sm:px-10 sm:pb-18 sm:pt-32">
        <div className="max-w-2xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.18em] text-white/85 backdrop-blur-xl">
            <Film className="h-3.5 w-3.5" />
            {eyebrow}
          </div>
          <h1 className="mt-5 max-w-[13ch] font-display text-[clamp(2.6rem,7vw,5.8rem)] font-semibold leading-[0.95] tracking-[-0.045em] text-white">
            {title}
          </h1>
          <p className="mt-5 max-w-[56ch] text-[15px] leading-[1.6] text-white/72 sm:text-[16px]">
            {description}
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <button
              onClick={onPlay}
              className="inline-flex min-h-11 items-center gap-2 rounded-full bg-brand px-5 py-3 text-[13px] font-semibold text-brand-foreground shadow-[0_10px_30px_-8px_color-mix(in_oklab,var(--color-brand)_70%,transparent)] transition hover:brightness-110"
            >
              <Play className="h-4 w-4 fill-current" />
              Play now
            </button>
            <button
              onClick={onMoreInfo}
              className="inline-flex min-h-11 items-center gap-2 rounded-full border border-white/14 bg-white/8 px-5 py-3 text-[13px] font-medium text-white backdrop-blur-2xl transition hover:bg-white/12"
            >
              <Info className="h-4 w-4" />
              More info
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}

function CatalogBrowser({
  items,
  rows,
  onSelect,
  label,
}: {
  items: Title[];
  rows: { title: string; reason?: string; items: Title[] }[];
  onSelect: (t: Title) => void;
  label: string;
}) {
  const [genre, setGenre] = useState("all");
  const [sort, setSort] = useState<SortMode>("featured");

  const genres = useMemo(() => {
    const set = new Set<string>();
    for (const t of items) for (const g of t.genres) set.add(g);
    return Array.from(set).sort();
  }, [items]);

  const filteringActive = genre !== "all" || sort !== "featured";

  const filtered = useMemo(() => {
    let out = genre === "all" ? items : items.filter((t) => t.genres.includes(genre));
    const compareName = (a: Title, b: Title) => a.name.localeCompare(b.name);
    switch (sort) {
      case "az":
        out = [...out].sort(compareName);
        break;
      case "za":
        out = [...out].sort((a, b) => -compareName(a, b));
        break;
      case "year-new":
        out = [...out].sort((a, b) => b.year - a.year);
        break;
      case "year-old":
        out = [...out].sort((a, b) => a.year - b.year);
        break;
      default:
        break;
    }
    return out;
  }, [items, genre, sort]);

  return (
    <ErrorBoundary label={`${label}_catalog`}>
      <FilterBar genres={genres} activeGenre={genre} setActiveGenre={setGenre} sort={sort} setSort={setSort} />
      {filteringActive ? (
        <section className="mx-auto max-w-[1600px] px-5 sm:px-10">
          <h2 className="mb-6 text-[1.1rem] font-medium tracking-[-0.02em] sm:text-[1.35rem]">
            {filtered.length} {filtered.length === 1 ? "result" : "results"}
          </h2>
          {filtered.length === 0 ? (
            <p className="text-sm text-muted-foreground">No titles match these filters.</p>
          ) : (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 sm:gap-5 lg:grid-cols-4 xl:grid-cols-5">
              {filtered.map((t) => (
                <PosterCard key={t.id} item={t} onSelect={onSelect} />
              ))}
            </div>
          )}
        </section>
      ) : (
        <div className="space-y-16 sm:space-y-20">
          {rows.map((row) => (
            <Row key={row.title} title={row.title} reason={row.reason} items={row.items} onSelect={onSelect} />
          ))}
        </div>
      )}
    </ErrorBoundary>
  );
}



const Row = memo(function Row({
  title,
  reason,
  items,
  onSelect,
}: {
  title: string;
  reason?: string;
  items: Title[];
  onSelect: (item: Title) => void;
}) {
  const scrollerRef = useRef<HTMLDivElement | null>(null);

  const scrollByAmount = useCallback((direction: 1 | -1) => {
    const node = scrollerRef.current;
    if (!node) return;
    const amount = Math.round(node.clientWidth * 0.78) * direction;
    node.scrollBy({ left: amount, behavior: "smooth" });
  }, []);

  return (
    <section className="row-contain relative px-5 sm:px-10">


      <div className="mb-5 flex items-end justify-between gap-4 sm:mb-6">
        <div className="min-w-0">
          <h2 className="text-[1.1rem] font-medium tracking-[-0.02em] text-foreground sm:text-[1.35rem]">
            {title}
          </h2>
          {reason && (
            <p className="mt-1 text-[12px] leading-[1.4] text-muted-foreground sm:text-[13px]">
              {reason}
            </p>
          )}
        </div>
        <div className="hidden shrink-0 items-center gap-2 sm:flex">
          <button
            onClick={() => scrollByAmount(-1)}
            className="grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-white/5 text-white/80 transition hover:bg-white/10"
            aria-label={`Scroll ${title} left`}
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            onClick={() => scrollByAmount(1)}
            className="grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-white/5 text-white/80 transition hover:bg-white/10"
            aria-label={`Scroll ${title} right`}
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div
        ref={scrollerRef}
        className="-mx-5 -my-8 flex gap-4 overflow-x-auto overflow-y-visible px-5 py-8 scrollbar-hide sm:-mx-10 sm:gap-5 sm:px-10"

      >
        {items.map((item) => (
          <PosterCard key={item.id} item={item} onSelect={onSelect} />
        ))}
      </div>
    </section>
  );
});

const PosterCard = memo(function PosterCard({
  item,
  onSelect,
}: {
  item: Title;
  onSelect: (item: Title) => void;
}) {
  const progress = useUserData((s) => s.continueWatching[item.id]);
  const clearProgress = useUserData((s) => s.clearProgress);
  const pct =
    progress && progress.duration > 0
      ? Math.min(100, Math.round((progress.position / progress.duration) * 100))
      : 0;
  const inProgress = pct > 0;
  return (
    <div className="group/poster relative shrink-0" style={{ width: "min(38vw, 260px)" }}>
      <button
        onClick={() => onSelect(item)}
        className="w-full text-left"
        aria-label={`Open ${item.name}${pct > 0 ? `, ${pct}% watched` : ""}`}
      >
        <div className="poster-card relative aspect-video w-full">
          <div className="tile-glow" aria-hidden />
          <div className="tv-tile relative h-full w-full overflow-hidden rounded-[18px] ring-1 ring-white/10">
            <img
              src={item.tile ?? item.backdrop}
              alt={item.name}
              loading="lazy"
              decoding="async"
              width={640}
              height={360}
              className="h-full w-full object-cover"
            />
            {pct > 0 && (
              <div className="tile-progress" aria-hidden>
                <span style={{ width: `${pct}%` }} />
              </div>
            )}
          </div>
        </div>
        <div className="mt-3 space-y-1">
          <div className="text-[14px] font-medium leading-[1.3] tracking-[-0.01em] text-foreground">
            {item.name}
          </div>
          <div className="text-[12px] leading-[1.35] text-muted-foreground">
            {item.year} · {item.genres.join(" · ")}
          </div>
        </div>
      </button>
      {inProgress && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            clearProgress(item.id);
          }}
          aria-label={`Remove ${item.name} from Continue Watching`}
          className="absolute right-2 top-2 z-20 grid h-8 w-8 place-items-center rounded-full bg-black/70 text-white opacity-0 ring-1 ring-white/20 backdrop-blur-md transition-opacity hover:bg-black/85 focus-visible:opacity-100 group-hover/poster:opacity-100"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      )}
    </div>
  );
});

function EmptyState({ title, body, icon, ctaLabel, onCta }: { title: string; body: string; icon?: React.ReactNode; ctaLabel?: string; onCta?: () => void }) {
  return (
    <div className="relative overflow-hidden rounded-3xl border border-white/8 bg-gradient-to-b from-white/[0.04] to-white/[0.015] px-6 py-14 text-center sm:py-20">
      <div aria-hidden className="pointer-events-none absolute inset-x-0 -top-24 mx-auto h-48 w-48 rounded-full bg-gradient-to-br from-indigo-400/20 via-fuchsia-400/10 to-transparent blur-3xl" />
      {icon && (
        <div className="mx-auto mb-5 grid h-12 w-12 place-items-center rounded-2xl border border-white/10 bg-white/5 text-foreground/85 backdrop-blur-xl">
          {icon}
        </div>
      )}
      <p className="font-display text-[1.1rem] font-medium tracking-[-0.02em] text-foreground sm:text-[1.25rem]">
        {title}
      </p>
      <p className="mx-auto mt-2 max-w-[42ch] text-[13px] leading-[1.55] text-muted-foreground sm:text-[14px]">{body}</p>
      {ctaLabel && onCta && (
        <button
          type="button"
          onClick={onCta}
          className="mt-6 inline-flex min-h-10 items-center gap-2 rounded-full bg-white px-5 py-2 text-[13px] font-semibold text-black transition hover:bg-white/90"
        >
          {ctaLabel}
          <ChevronRight className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}


/* ============================================================
   HomeTab — extension-driven home (Priority 8).
   Static catalog rows are gone; rows come from installed Stremio
   addons via useExtensionCatalog(). When no addon provides a
   catalog, render a clear "install an extension" empty state
   instead of fake picsum content.
   ============================================================ */
function HomeTab({
  continueRow,
  watchlistRow,
  picksRow,
  liveChannels,
  onSelect,
  onPlay,
  onPlayChannel,
  onOpenSettings,
  onOpenLive,
}: {
  continueRow: Title[];
  watchlistRow: Title[];
  picksRow: Title[];
  liveChannels: IPTVChannel[];
  onSelect: (t: Title) => void;
  onPlay: (t: Title) => void;
  onPlayChannel: (c: IPTVChannel) => void;
  onOpenSettings: () => void;
  onOpenLive: () => void;
}) {
  const { rows, status, error, extensionsInstalled, retry } = useExtensionCatalog();

  // Build a hero from the first available extension item so users
  // see real content instead of a placeholder backdrop. Fall back
  // to nothing when no extension content is available yet — the
  // empty state below carries the page.
  const heroTitle: Title | null =
    rows.find((r) => r.items[0])?.items[0] ??
    picksRow[0] ??
    continueRow[0] ??
    watchlistRow[0] ??
    null;

  const hasAnyUserRow = continueRow.length + watchlistRow.length + picksRow.length > 0;
  const showEmptyShell = status === "empty" && !hasAnyUserRow;

  if (showEmptyShell) {
    return (
      <main id="main-content" className="relative z-10 mx-auto flex min-h-[80vh] max-w-[1600px] flex-col items-center justify-center px-5 py-24 text-center sm:px-10">
        <div className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.18em] text-white/80">
          <Sparkles className="h-3.5 w-3.5" />
          Welcome
        </div>
        <h1 className="mt-5 font-display text-[clamp(2rem,5vw,3.4rem)] font-semibold tracking-[-0.035em]">
          Nothing to watch — yet.
        </h1>
        <p className="mt-4 max-w-[52ch] text-[14px] leading-[1.65] text-muted-foreground sm:text-[16px]" suppressHydrationWarning>
          CalmSource pulls its catalog from installed add-ons. Install one
          to populate Home with movies, series, and metadata.
          {extensionsInstalled > 0 ? " You have add-ons installed, but none expose a catalog yet — try Cinemeta." : ""}
        </p>
        <button
          onClick={onOpenSettings}
          className="mt-8 inline-flex min-h-11 items-center gap-2 rounded-full bg-white px-6 py-2.5 text-[13px] font-semibold text-black transition hover:bg-white/90"
        >
          Open Extensions
          <ChevronRight className="h-4 w-4" />
        </button>
      </main>
    );
  }

  return (
    <>
      {heroTitle ? (
        <ExtensionHero title={heroTitle} onSelect={onSelect} onPlay={onPlay} />
      ) : null}

      <main id="main-content" className="relative z-10 space-y-16 pb-24 pt-12 sm:space-y-20 sm:pb-28 sm:pt-20">
        <ErrorBoundary label="home_rows">
          {continueRow.length > 0 && (
            <Row title="Continue watching" reason="Pick up exactly where you left off." items={continueRow} onSelect={onSelect} />
          )}
          {watchlistRow.length > 0 && (
            <Row title="My list" reason="Saved by you, waiting for the right night." items={watchlistRow} onSelect={onSelect} />
          )}
          {picksRow.length > 0 && (
            <Row
              title="Picks for you"
              reason="Tuned to what you've watched, saved, and loved."
              items={picksRow}
              onSelect={onSelect}
            />
          )}

          {liveChannels.length > 0 && (
            <LiveChannelsRow channels={liveChannels.slice(0, 12)} onPlay={onPlayChannel} onOpenLive={onOpenLive} />
          )}

          {status === "loading" && (
            <div className="space-y-12 sm:space-y-16" role="status" aria-live="polite" aria-label="Loading catalog">
              {Array.from({ length: 3 }).map((_, r) => (
                <div key={r} className="px-5 sm:px-10">
                  <div className="mb-5 space-y-2 sm:mb-6">
                    <div className="skeleton-shimmer h-5 w-48 rounded-md" />
                    <div className="skeleton-shimmer h-3 w-72 rounded-md opacity-70" />
                  </div>
                  <div className="flex gap-3 overflow-hidden sm:gap-4">
                    {Array.from({ length: 7 }).map((_, i) => (
                      <div key={i} className="skeleton-shimmer aspect-[16/9] w-[240px] shrink-0 rounded-[18px] sm:w-[300px]" />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {status === "error" && (
            <div className="mx-5 rounded-2xl border border-white/10 bg-white/5 p-6 sm:mx-10" role="alert">
              <h2 className="font-display text-xl font-semibold">Couldn't load extension catalogs</h2>
              <p className="mt-2 max-w-[60ch] text-[14px] text-muted-foreground">
                {error ?? "Something went wrong reaching your extensions."}
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                <button
                  onClick={retry}
                  className="inline-flex min-h-10 items-center gap-2 rounded-full bg-white px-5 py-2 text-[13px] font-semibold text-black transition hover:bg-white/90"
                >
                  Try again
                </button>
                <button
                  onClick={onOpenSettings}
                  className="inline-flex min-h-10 items-center gap-2 rounded-full border border-white/15 bg-white/5 px-5 py-2 text-[13px] font-semibold text-white transition hover:bg-white/10"
                >
                  Manage extensions
                </button>
              </div>
            </div>
          )}

          {status === "empty" && (
            <EmptyState
              icon={<Sparkles className="h-5 w-5" />}
              title="No catalog rows yet"
              body="Install an extension that exposes catalogs (e.g. Cinemeta) to fill Home with movies and series."
              ctaLabel="Open Extensions"
              onCta={onOpenSettings}
            />
          )}

          {rows.map((row, idx) => (
            <Row
              key={row.key}
              title={row.title}
              reason={
                idx === 0
                  ? `Fresh from ${row.addonName}.`
                  : `Curated by ${row.addonName}.`
              }
              items={row.items}
              onSelect={onSelect}
            />
          ))}
        </ErrorBoundary>
      </main>
    </>
  );
}

/* Minimal hero for an extension title — does NOT depend on static
   catalog. Reuses the same visual language as the original Hero. */
function ExtensionHero({
  title,
  onSelect,
  onPlay,
}: {
  title: Title;
  onSelect: (t: Title) => void;
  onPlay: (t: Title) => void;
}) {
  return (
    <section className="relative isolate h-[78vh] min-h-[560px] w-full overflow-hidden">
      <div className="absolute inset-0">
        {title.backdrop ? (
          <img
            src={title.backdrop}
            alt=""
            className="h-full w-full object-cover"
            decoding="async"
            fetchPriority="high"
          />
        ) : (
          <div className="h-full w-full bg-gradient-to-br from-zinc-900 via-zinc-950 to-black" />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-background via-background/40 to-transparent" />
        <div className="absolute inset-0 bg-gradient-to-r from-background/80 via-background/20 to-transparent" />
      </div>
      <div className="relative z-10 mx-auto flex h-full max-w-[1600px] flex-col justify-end px-5 pb-16 sm:px-10 sm:pb-20">
        <div className="max-w-2xl space-y-4">
          <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-black/30 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.18em] text-white/80 backdrop-blur">
            <Sparkles className="h-3.5 w-3.5" />
            Featured
          </div>
          <h1 className="font-display text-[clamp(2.2rem,5.5vw,4.2rem)] font-semibold leading-[1.05] tracking-[-0.035em]">
            {title.name}
          </h1>
          {title.description ? (
            <p className="max-w-[58ch] text-[14px] leading-[1.6] text-white/80 sm:text-[16px]">
              {title.description.length > 240 ? `${title.description.slice(0, 240)}…` : title.description}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-3 pt-2">
            <button
              onClick={() => onPlay(title)}
              className="inline-flex min-h-11 items-center gap-2 rounded-full bg-white px-6 py-2.5 text-[14px] font-semibold text-black transition hover:bg-white/90"
            >
              <Play className="h-4 w-4 fill-current" />
              Play
            </button>
            <button
              onClick={() => onSelect(title)}
              className="inline-flex min-h-11 items-center gap-2 rounded-full border border-white/20 bg-white/10 px-6 py-2.5 text-[14px] font-semibold text-white backdrop-blur transition hover:bg-white/15"
            >
              <Info className="h-4 w-4" />
              More info
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}





/* ============================================================
   Top 10 row — giant outline rank numerals (Netflix-style)
   ============================================================ */
const TopTenRow = memo(function TopTenRow({
  items,
  onSelect,
}: {
  items: Title[];
  onSelect: (item: Title) => void;
}) {
  const scrollerRef = useRef<HTMLDivElement | null>(null);
  const scrollByAmount = useCallback((dir: 1 | -1) => {
    const node = scrollerRef.current;
    if (!node) return;
    node.scrollBy({ left: Math.round(node.clientWidth * 0.8) * dir, behavior: "smooth" });
  }, []);
  return (
    <section className="row-contain relative px-5 sm:px-10">

      <div className="mb-5 flex items-end justify-between gap-4 sm:mb-6">
        <div className="min-w-0">
          <h2 className="text-[1.1rem] font-medium tracking-[-0.02em] text-foreground sm:text-[1.35rem]">
            Top 10 on CalmSource today
          </h2>
          <p className="mt-1 text-[12px] leading-[1.4] text-muted-foreground sm:text-[13px]">
            What everyone's watching right now — ranked.
          </p>
        </div>
        <div className="hidden shrink-0 items-center gap-2 sm:flex">
          <button onClick={() => scrollByAmount(-1)} aria-label="Scroll Top 10 left" className="grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-white/5 text-white/80 transition hover:bg-white/10">
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button onClick={() => scrollByAmount(1)} aria-label="Scroll Top 10 right" className="grid h-9 w-9 place-items-center rounded-full border border-white/10 bg-white/5 text-white/80 transition hover:bg-white/10">
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>
      <div ref={scrollerRef} className="-mx-5 -my-8 flex gap-3 overflow-x-auto overflow-y-visible px-5 py-8 scrollbar-hide sm:-mx-10 sm:gap-5 sm:px-10">
        {items.slice(0, 10).map((item, i) => (
          <button
            key={item.id}
            type="button"
            onClick={() => onSelect(item)}
            aria-label={`Rank ${i + 1}: ${item.name}`}
            className="group/poster relative flex shrink-0 items-end gap-1 text-left sm:gap-2"
          >
            <span aria-hidden className="top-ten-num pl-0.5 pr-0">{i + 1}</span>
            <div className="poster-card relative -ml-6 aspect-[2/3] w-[42vw] max-w-[200px] sm:-ml-8 sm:w-[15vw] sm:max-w-[200px]">
              <div className="tile-glow" aria-hidden />
              <div className="tv-tile relative h-full w-full overflow-hidden rounded-[14px] ring-1 ring-white/10">
                <img src={item.poster} alt={item.name} loading="lazy" decoding="async" width={300} height={450} className="h-full w-full object-cover" />
              </div>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
});

/* ============================================================
   Mood / vibes browser — chips that swap the row content
   ============================================================ */
const MoodStrip = memo(function MoodStrip({
  moods,
  onSelect,
}: {
  moods: Mood[];
  onSelect: (item: Title) => void;
}) {
  const [active, setActive] = useState<Mood>(moods[0]);
  const items = useMemo(() => titlesForMood(active), [active]);
  return (
    <section className="row-contain relative px-5 sm:px-10">

      <div className="mb-5 flex flex-col gap-1 sm:mb-6">
        <h2 className="text-[1.1rem] font-medium tracking-[-0.02em] text-foreground sm:text-[1.35rem]">
          What's your vibe?
        </h2>
        <p className="text-[12px] leading-[1.4] text-muted-foreground sm:text-[13px]">
          {active.blurb}
        </p>
      </div>
      <div className="-mx-5 mb-5 flex gap-2.5 overflow-x-auto px-5 pb-1 scrollbar-hide sm:-mx-10 sm:px-10">
        {moods.map((m) => {
          const isActive = m.id === active.id;
          return (
            <button
              key={m.id}
              type="button"
              onClick={() => setActive(m)}
              aria-pressed={isActive}
              className="mood-chip shrink-0 text-[12.5px] font-medium tracking-[0.005em]"
              style={{
                ["--mood-bg" as never]: m.swatch,
                ["--mood-tint" as never]: m.tint,
              }}
            >
              <span className="mood-chip__dot" aria-hidden />
              {m.label}
            </button>


          );
        })}
      </div>

      <div className="-mx-5 -my-8 flex gap-4 overflow-x-auto overflow-y-visible px-5 py-8 scrollbar-hide sm:-mx-10 sm:gap-5 sm:px-10">
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No titles match this mood yet.</p>
        ) : (
          items.map((t) => <PosterCard key={`${active.id}-${t.id}`} item={t} onSelect={onSelect} />)
        )}
      </div>
    </section>
  );
});

/* ============================================================
   Leaving soon — shelf with "leaves in N days" badges
   ============================================================ */
const LeavingSoonRow = memo(function LeavingSoonRow({
  items,
  onSelect,
}: {
  items: LeavingTitle[];
  onSelect: (item: Title) => void;
}) {
  if (items.length === 0) return null;
  return (
    <section className="row-contain relative px-5 sm:px-10">
      <div className="mb-5 flex flex-col gap-1 sm:mb-6">
        <h2 className="text-[1.1rem] font-medium tracking-[-0.02em] text-foreground sm:text-[1.35rem]">
          Leaving soon
        </h2>
        <p className="text-[12px] leading-[1.4] text-muted-foreground sm:text-[13px]">
          Catch these before they head out of the library.
        </p>
      </div>
      <div className="-mx-5 -my-8 flex gap-4 overflow-x-auto overflow-y-visible px-5 py-8 scrollbar-hide sm:-mx-10 sm:gap-5 sm:px-10">
        {items.map((item) => (
          <div key={item.id} className="relative shrink-0" style={{ width: "min(38vw, 260px)" }}>
            <PosterCard item={item} onSelect={onSelect} />
            <span className="pointer-events-none absolute left-2 top-2 z-10 inline-flex items-center gap-1 rounded-full bg-black/70 px-2 py-0.5 text-[10.5px] font-semibold uppercase tracking-[0.08em] text-amber-200 ring-1 ring-amber-300/40 backdrop-blur-md">
              Leaves in {item.leavesInDays}d
            </span>
          </div>
        ))}
      </div>
    </section>
  );
});

/* Live TV row on Home — pulls top channels and routes plays to the player. */
function LiveChannelsRow({
  channels,
  onPlay,
  onOpenLive,
}: {
  channels: IPTVChannel[];
  onPlay: (c: IPTVChannel) => void;
  onOpenLive: () => void;
}) {
  return (
    <section className="space-y-4 px-5 sm:px-10">
      <div className="flex items-end justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-2 text-[11px] font-medium uppercase tracking-[0.18em] text-white/70">
            <Radio className="h-3.5 w-3.5" /> Live TV
          </div>
          <h2 className="font-display text-[clamp(1.2rem,2.4vw,1.6rem)] font-semibold tracking-tight">
            On now
          </h2>
        </div>
        <button
          onClick={onOpenLive}
          className="inline-flex items-center gap-1 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[12px] font-medium text-white/80 transition hover:bg-white/10"
        >
          All channels <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
      <div className="-mx-5 flex gap-3 overflow-x-auto px-5 py-2 scrollbar-hide sm:-mx-10 sm:px-10">
        {channels.map((c) => (
          <button
            key={c.id}
            onClick={() => onPlay(c)}
            className="group relative flex aspect-[16/9] w-[200px] shrink-0 flex-col items-center justify-center overflow-hidden rounded-xl border border-white/10 bg-gradient-to-br from-white/[0.06] to-white/[0.02] p-3 text-center transition hover:border-white/25 hover:bg-white/[0.08] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand,#3b6dff)] sm:w-[240px]"
          >
            {c.logo ? (
              <img src={c.logo} alt="" loading="lazy" className="max-h-12 max-w-[80%] object-contain opacity-90 transition group-hover:opacity-100" />
            ) : (
              <Radio className="h-7 w-7 text-white/40" />
            )}
            <span className="mt-2 line-clamp-2 text-[12px] font-medium text-white/90">{c.name}</span>
            {c.group ? (
              <span className="mt-0.5 text-[10px] uppercase tracking-[0.1em] text-white/40">{c.group}</span>
            ) : null}
          </button>
        ))}
      </div>
    </section>
  );
}
