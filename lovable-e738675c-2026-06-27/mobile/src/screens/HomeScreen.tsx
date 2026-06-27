import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  ImageBackground,
  Pressable,
  useWindowDimensions,
} from "react-native";
import { FlashList, type ListRenderItem } from "@shopify/flash-list";
import { Image } from "expo-image";
import { LinearGradient } from "expo-linear-gradient";
import type { Title } from "../lib/catalog";
import {
  catalogRepository,
  playbackResolver,
} from "../lib/repositories";
import { EyebrowPill } from "../components/EyebrowPill";
import { colors, TILE_W, TILE_H, LANDSCAPE_W, LANDSCAPE_H } from "../components/theme";
import {
  useIsTV,
  focusRing,
  TV_TILE_W,
  TV_TILE_H,
  TV_LANDSCAPE_W,
  TV_LANDSCAPE_H,
} from "../lib/tv";
import { selectContinue, useUserData } from "../lib/userdata";
import { DetailsSheet, useDetailsSheet } from "../components/DetailsSheet";
import { FilterBar, applyFilter, type SortMode } from "../components/FilterBar";
import { personalizedPicks } from "../lib/recommend";
import { usePlayer } from "../lib/player-store";
import { useExtensionCatalog } from "../hooks/use-extension-catalog";

// Repository-backed module constants (fake data today, real backend tomorrow).
const FEATURED = catalogRepository.getFeatured();
const ROWS = catalogRepository.getHomeRows();
const TOP_TEN = catalogRepository.getTopTen();
const LEAVING_SOON = catalogRepository.getLeavingSoon();
const MOODS = catalogRepository.getMoods();
const FILMS = catalogRepository.getFilms();
const SERIES = catalogRepository.getSeries();
const FILM_ROWS = catalogRepository.getFilmRows();
const SERIES_ROWS = catalogRepository.getSeriesRows();
const titlesForMood = (mood: { id: string }) =>
  catalogRepository.getTitlesForMood(mood.id);


const BLURHASH = "L6PZfSi_.AyE_3t7t7R**0o#DgR4";
const HERO_POOL: Title[] = [FEATURED, ...TOP_TEN.slice(0, 3)];
const HERO_INTERVAL = 11_000;

type Tab = "home" | "movies" | "series" | "list";

export default function HomeScreen() {
  const isTV = useIsTV();
  const [tab, setTab] = useState<Tab>("home");
  const [activeMood, setActiveMood] = useState<string | null>(null);
  const [genre, setGenre] = useState<string | null>(null);
  const [sort, setSort] = useState<SortMode>("trending");
  const titlesById = useMemo(() => {
    const m = new Map<string, Title>();
    for (const t of [...FILMS, ...SERIES]) m.set(t.id, t);
    return m;
  }, []);
  const filmGenres = useMemo(() => Array.from(new Set(FILMS.flatMap((t) => t.genres))).sort(), []);
  const seriesGenres = useMemo(() => Array.from(new Set(SERIES.flatMap((t) => t.genres))).sort(), []);

  // Reset filter when switching tabs so it doesn't bleed across catalogs.
  useEffect(() => { setGenre(null); }, [tab]);

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: isTV ? 80 : 48 }}
      showsVerticalScrollIndicator={false}
    >
      <Hero isTV={isTV} />
      <TabBar tab={tab} setTab={setTab} isTV={isTV} />

      {tab === "home" && (
        <>
          <ContinueWatching isTV={isTV} titlesById={titlesById} />
          <PicksForYouRow isTV={isTV} />
          <ExtensionRows isTV={isTV} />
        </>
      )}


      {tab === "movies" && (
        <>
          <FilterBar genres={filmGenres} activeGenre={genre} setActiveGenre={setGenre} sort={sort} setSort={setSort} isTV={isTV} />
          {genre || sort !== "trending" ? (
            <Row title={genre ?? "All films"} items={applyFilter(FILMS, genre, sort)} isTV={isTV} />
          ) : (
            FILM_ROWS.map((row) => (
              <Row key={row.title} title={row.title} items={row.items} isTV={isTV} />
            ))
          )}
        </>
      )}

      {tab === "series" && (
        <>
          <FilterBar genres={seriesGenres} activeGenre={genre} setActiveGenre={setGenre} sort={sort} setSort={setSort} isTV={isTV} />
          {genre || sort !== "trending" ? (
            <Row title={genre ?? "All series"} items={applyFilter(SERIES, genre, sort)} isTV={isTV} />
          ) : (
            SERIES_ROWS.map((row) => (
              <Row key={row.title} title={row.title} items={row.items} isTV={isTV} />
            ))
          )}
        </>
      )}

      {tab === "list" && <MyListView isTV={isTV} titlesById={titlesById} />}
      <DetailsSheet />
    </ScrollView>
  );
}

/* ───────────── Hero ───────────── */

const Hero = memo(function Hero({ isTV }: { isTV: boolean }) {
  const { width } = useWindowDimensions();
  const [idx, setIdx] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduceMotion = useUserData((s) => s.preferences.reduceMotion);
  const openSheet = useDetailsSheet((s) => s.open);
  const openPlayer = usePlayer((s) => s.open);
  const play = (t: Title) =>
    openPlayer({ kind: "title", title: t, streamUrl: playbackResolver.resolveTitle(t) });

  useEffect(() => {
    if (paused || reduceMotion || HERO_POOL.length <= 1) return;
    const t = setInterval(() => setIdx((i) => (i + 1) % HERO_POOL.length), HERO_INTERVAL);
    return () => clearInterval(t);
  }, [paused, reduceMotion]);

  const t = HERO_POOL[idx];
  return (
    <Pressable onPressIn={() => setPaused(true)} onPressOut={() => setPaused(false)}>
      <ImageBackground
        source={{ uri: t.backdrop }}
        style={[styles.hero, { width, height: isTV ? 720 : 520 }]}
        imageStyle={{ opacity: 0.7 }}
      >
        <LinearGradient
          colors={["transparent", "rgba(5,6,10,0.6)", colors.bg]}
          style={StyleSheet.absoluteFill}
        />
        <LinearGradient
          colors={["rgba(46,91,255,0.22)", "transparent"]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={StyleSheet.absoluteFill}
        />
        <View style={[styles.heroInner, isTV && { padding: 56, gap: 16, maxWidth: 900 }]}>
          <EyebrowPill label={idx === 0 ? "FEATURED" : `TRENDING #${idx + 1}`} />
          <Text style={[styles.heroTitle, isTV && { fontSize: 64 }]}>{t.name}</Text>
          <View style={styles.heroMetaRow}>
            <Text style={[styles.heroMeta, isTV && { fontSize: 16 }]}>{t.year}</Text>
            <View style={styles.dot} />
            <Text style={[styles.heroMeta, isTV && { fontSize: 16 }]}>{t.rating}</Text>
            <View style={styles.dot} />
            <Text style={[styles.heroMeta, isTV && { fontSize: 16 }]}>{t.duration}</Text>
          </View>
          <Text style={[styles.heroDesc, isTV && { fontSize: 19, lineHeight: 28 }]} numberOfLines={3}>
            {t.description}
          </Text>
          <View style={styles.heroButtons}>
            <FocusablePressable hasTVPreferredFocus onPress={() => play(t)} style={[styles.playBtn, isTV && styles.playBtnTV]}>
              <Text style={[styles.playBtnText, isTV && { fontSize: 18 }]}>▶  Play</Text>
            </FocusablePressable>
            <FocusablePressable onPress={() => openSheet(t)} style={[styles.infoBtn, isTV && styles.infoBtnTV]}>
              <Text style={[styles.infoBtnText, isTV && { fontSize: 18 }]}>More Info</Text>
            </FocusablePressable>
          </View>
          {HERO_POOL.length > 1 && (
            <View style={styles.heroDots}>
              {HERO_POOL.map((_, i) => (
                <View key={i} style={[styles.heroDotIndicator, i === idx && styles.heroDotActive]} />
              ))}
            </View>
          )}
        </View>
      </ImageBackground>
    </Pressable>
  );
});

/* ───────────── Tabs ───────────── */

const TABS: { id: Tab; label: string }[] = [
  { id: "home", label: "Home" },
  { id: "movies", label: "Movies" },
  { id: "series", label: "Series" },
  { id: "list", label: "My List" },
];

const TabBar = memo(function TabBar({
  tab, setTab, isTV,
}: { tab: Tab; setTab: (t: Tab) => void; isTV: boolean }) {
  return (
    <View style={[styles.tabRow, isTV && { paddingHorizontal: 48 }]}>
      {TABS.map((t) => {
        const active = tab === t.id;
        return (
          <Pressable
            key={t.id}
            onPress={() => setTab(t.id)}
            focusable
            style={[styles.tab, active && styles.tabActive]}
          >
            <Text style={[styles.tabText, active && styles.tabTextActive, isTV && { fontSize: 17 }]}>
              {t.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
});

/* ───────────── Continue Watching (real userdata) ───────────── */

const ContinueWatching = memo(function ContinueWatching({
  isTV, titlesById,
}: { isTV: boolean; titlesById: Map<string, Title> }) {
  const entries = useUserData(selectContinue);
  const clearProgress = useUserData((s) => s.clearProgress);
  const openSheet = useDetailsSheet((s) => s.open);
  const items = useMemo(
    () =>
      entries
        .map((e) => {
          const t = titlesById.get(e.id);
          if (!t) return null;
          const pct = e.duration > 0 ? Math.min(0.98, e.position / e.duration) : 0;
          const remaining = e.duration > 0 ? Math.max(1, Math.round((e.duration - e.position) / 60)) : null;
          return { t, pct, remaining };
        })
        .filter(Boolean)
        .slice(0, 12) as { t: Title; pct: number; remaining: number | null }[],
    [entries, titlesById],
  );

  if (!items.length) return null;
  const W = isTV ? TV_LANDSCAPE_W : LANDSCAPE_W;
  const H = isTV ? TV_LANDSCAPE_H : LANDSCAPE_H;

  return (
    <View style={{ marginTop: isTV ? 48 : 28 }}>
      <View style={styles.rowTitleWrap}>
        <Text style={[styles.rowTitle, isTV && tvStyles.rowTitleTV]}>Continue watching</Text>
      </View>
      <View style={{ height: H + 56 }}>
        <FlashList
          horizontal
          data={items}
          keyExtractor={(it) => it.t.id}
          renderItem={({ item }) => (
            <FocusableTile width={W} onPress={() => openSheet(item.t)}>
              <View style={[styles.landscape, { width: W, height: H }]}>
                <Image
                  source={{ uri: item.t.tile || item.t.backdrop }}
                  style={StyleSheet.absoluteFill}
                  contentFit="cover"
                  cachePolicy="memory-disk"
                  transition={200}
                  recyclingKey={item.t.id}
                  placeholder={BLURHASH}
                />
                <LinearGradient colors={["transparent", "rgba(0,0,0,0.8)"]} style={StyleSheet.absoluteFill} />
                <Pressable
                  onPress={() => clearProgress(item.t.id)}
                  hitSlop={10}
                  style={styles.removeBtn}
                >
                  <Text style={styles.removeBtnText}>✕</Text>
                </Pressable>
                <View style={styles.progressTrack}>
                  <View style={[styles.progressFill, { width: `${item.pct * 100}%` }]} />
                </View>
              </View>
              <Text style={[styles.tileTitle, isTV && { fontSize: 16 }]} numberOfLines={1}>{item.t.name}</Text>
              <Text style={[styles.tileMeta, isTV && { fontSize: 13 }]} numberOfLines={1}>
                {item.remaining != null ? `${item.remaining}m left` : "Resume"}
              </Text>
            </FocusableTile>
          )}
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: isTV ? 48 : 20 }}
          ItemSeparatorComponent={() => <View style={{ width: isTV ? 20 : 14 }} />}
          estimatedItemSize={W + 14}
          drawDistance={isTV ? 600 : 300}
        />
      </View>
    </View>
  );
});

/* ───────────── Mood strip ───────────── */

const MoodStrip = memo(function MoodStrip({
  active, onPick, isTV,
}: { active: string | null; onPick: (id: string | null) => void; isTV: boolean }) {
  return (
    <View style={{ marginTop: isTV ? 48 : 32 }}>
      <View style={styles.rowTitleWrap}>
        <Text style={[styles.rowTitle, isTV && tvStyles.rowTitleTV]}>What's the vibe?</Text>
        <Text style={styles.rowReason}>Pick a feeling, get a handful that fits.</Text>
      </View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: isTV ? 48 : 20, gap: 10 }}
      >
        {MOODS.map((m) => {
          const on = active === m.id;
          return (
            <Pressable
              key={m.id}
              focusable
              onPress={() => onPick(on ? null : m.id)}
              style={[
                styles.moodChip,
                on && { backgroundColor: `${m.tint}26`, borderColor: `${m.tint}80` },
                isTV && { paddingHorizontal: 20, paddingVertical: 14 },
              ]}
            >
              <View style={[styles.moodDot, { backgroundColor: m.tint }]} />
              <View style={{ flexShrink: 1 }}>
                <Text style={[styles.moodLabel, on && { color: "#fff" }, isTV && { fontSize: 15 }]}>{m.label}</Text>
                <Text style={[styles.moodBlurb, isTV && { fontSize: 12 }]} numberOfLines={1}>{m.blurb}</Text>
              </View>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
});

const PicksForYouRow = memo(function PicksForYouRow({ isTV }: { isTV: boolean }) {
  const history = useUserData((s) => s.history);
  const watchlist = useUserData((s) => s.watchlist);
  const ratings = useUserData((s) => s.ratings);
  const continueMap = useUserData((s) => s.continueWatching);
  const items = useMemo(
    () =>
      personalizedPicks({
        history,
        watchlist,
        ratings,
        continueIds: Object.keys(continueMap),
      }),
    [history, watchlist, ratings, continueMap],
  );
  if (items.length === 0) return null;
  return <Row title="Picks for you" reason="Tuned to what you've watched, saved, and loved." items={items} isTV={isTV} />;
});

const MoodRow = memo(function MoodRow({ moodId, isTV }: { moodId: string; isTV: boolean }) {

  const mood = MOODS.find((m) => m.id === moodId);
  if (!mood) return null;
  const items = titlesForMood(mood);
  if (!items.length) return null;
  return <Row title={mood.label} reason={mood.blurb} items={items} isTV={isTV} />;
});

/* ───────────── Leaving soon ───────────── */

const LeavingRow = memo(function LeavingRow({ isTV }: { isTV: boolean }) {
  const W = isTV ? TV_TILE_W : TILE_W;
  const H = isTV ? TV_TILE_H : TILE_H;
  const openSheet = useDetailsSheet((s) => s.open);
  return (
    <View style={{ marginTop: isTV ? 48 : 32 }}>
      <View style={styles.rowTitleWrap}>
        <Text style={[styles.rowTitle, isTV && tvStyles.rowTitleTV]}>Leaving soon</Text>
        <Text style={styles.rowReason}>Last chance before they're off the catalog.</Text>
      </View>
      <View style={{ height: H + 56 }}>
        <FlashList
          horizontal
          data={LEAVING_SOON}
          keyExtractor={(t) => t.id}
          renderItem={({ item }) => (
            <FocusableTile width={W} onPress={() => openSheet(item)}>
              <View style={[styles.poster, { width: W, height: H }]}>
                <Image
                  source={{ uri: item.poster }}
                  style={StyleSheet.absoluteFill}
                  contentFit="cover"
                  cachePolicy="memory-disk"
                  placeholder={BLURHASH}
                  recyclingKey={item.id}
                />
                <View style={styles.leaveBadge}>
                  <Text style={styles.leaveBadgeText}>{item.leavesInDays}d left</Text>
                </View>
              </View>
              <Text style={[styles.tileTitle, isTV && { fontSize: 16 }]} numberOfLines={1}>{item.name}</Text>
              <Text style={[styles.tileMeta, isTV && { fontSize: 13 }]} numberOfLines={1}>
                {item.year} · {item.genres[0]}
              </Text>
            </FocusableTile>
          )}
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: isTV ? 48 : 20 }}
          estimatedItemSize={(isTV ? TV_TILE_W : TILE_W) + 14}
          drawDistance={isTV ? 600 : 300}
        />
      </View>
    </View>
  );
});

/* ───────────── Rows ───────────── */

const Row = memo(function Row({
  title, reason, items, isTV, numbered,
}: { title: string; reason?: string; items: Title[]; isTV: boolean; numbered?: boolean }) {
  const W = isTV ? TV_TILE_W : TILE_W;
  const H = isTV ? TV_TILE_H : TILE_H;
  const GAP = isTV ? 20 : 14;
  const ITEM = W + GAP + (numbered ? 50 : 0);
  // TV per-row focus memory: remember the last focused column on this row
  // so D-pad up/down returns to the previous position instead of slamming
  // back to column 0 every traversal. Keyed by row title so each shelf is
  // independent. Lives in a ref → no re-renders for focus tracking.
  const listRef = useRef<FlashList<Title> | null>(null);
  const lastFocusedIndex = useRef(0);
  const renderItem = useCallback<ListRenderItem<Title>>(({ item, index }) => (
    <PosterTile
      item={item}
      width={W}
      height={H}
      largeText={isTV}
      rank={numbered ? index + 1 : undefined}
      onFocusRow={isTV ? () => { lastFocusedIndex.current = index; } : undefined}
    />
  ), [W, H, isTV, numbered]);
  // When the row mounts/remounts on Android TV, scroll to the remembered
  // column so the focus ring lands where the user left it.
  useEffect(() => {
    if (!isTV) return;
    const id = setTimeout(() => {
      try {
        // FlashList v2 uses `viewOffset`; `viewPosition` is silently ignored.
        listRef.current?.scrollToIndex({ index: lastFocusedIndex.current, animated: false, viewOffset: 0 });
      } catch { /* index may be unrendered yet */ }
    }, 50);
    return () => clearTimeout(id);
  }, [isTV]);

  return (
    <View style={{ marginTop: isTV ? 48 : 32 }}>
      <View style={styles.rowTitleWrap}>
        <Text style={[styles.rowTitle, isTV && tvStyles.rowTitleTV]}>{title}</Text>
        {reason ? <Text style={styles.rowReason}>{reason}</Text> : null}
      </View>
      <View style={{ height: H + 56 }}>
        {/* Explicit height is required for horizontal FlashList inside a
            vertical ScrollView — Android otherwise measures 0 and renders
            nothing. 56 ≈ two lines of tile title + meta below the poster. */}
        <FlashList
          ref={listRef}
          horizontal
          data={items}
          keyExtractor={(t) => t.id}
          renderItem={renderItem}
          // FlashList v2 recycles cells on the UI thread and removes the need
          // for getItemLayout / windowSize tuning. estimatedItemSize is just a
          // hint to seed the recycler before measurements arrive.
          estimatedItemSize={ITEM}
          drawDistance={isTV ? 600 : 300}
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: isTV ? 48 : 20 }}
          ItemSeparatorComponent={() => <View style={{ width: GAP }} />}
        />
      </View>
    </View>
  );
});

const PosterTile = memo(function PosterTile({
  item, width, height, largeText, rank, onFocusRow,
}: { item: Title; width: number; height: number; largeText: boolean; rank?: number; onFocusRow?: () => void }) {
  const openSheet = useDetailsSheet((s) => s.open);
  return (
    <FocusableTile width={width + (rank ? 50 : 0)} onPress={() => openSheet(item)} onFocus={onFocusRow}>
      <View style={{ flexDirection: "row", alignItems: "center" }}>
        {rank ? (
          <Text style={styles.rankNum}>{rank}</Text>
        ) : null}
        <View style={[styles.poster, { width, height }]}>
          <Image
            source={{ uri: item.poster }}
            style={StyleSheet.absoluteFill}
            contentFit="cover"
            cachePolicy="memory-disk"
            transition={180}
            recyclingKey={item.id}
            placeholder={BLURHASH}
          />
        </View>
      </View>
      <Text style={[styles.tileTitle, largeText && { fontSize: 16 }]} numberOfLines={1}>{item.name}</Text>
      <Text style={[styles.tileMeta, largeText && { fontSize: 13 }]} numberOfLines={1}>
        {item.year} · {item.genres[0]}
      </Text>
    </FocusableTile>
  );
});

/* ───────────── My List ───────────── */

function MyListView({ isTV, titlesById }: { isTV: boolean; titlesById: Map<string, Title> }) {
  const watchlist = useUserData((s) => s.watchlist);
  const cont = useUserData(selectContinue);
  const items = useMemo(
    () => watchlist.map((id) => titlesById.get(id)).filter(Boolean) as Title[],
    [watchlist, titlesById],
  );
  return (
    <>
      <ContinueWatching isTV={isTV} titlesById={titlesById} />
      {items.length ? (
        <Row title="My watchlist" reason="Saved for later." items={items} isTV={isTV} />
      ) : (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>Your list is empty</Text>
          <Text style={styles.emptyHint}>Tap the bookmark on any title to add it here.</Text>
        </View>
      )}
      {!cont.length && !items.length ? null : null}
    </>
  );
}

/* ───────────── Focus helper ───────────── */

function FocusablePressable({
  style, children, hasTVPreferredFocus, onPress,
}: { style?: any; children: React.ReactNode; hasTVPreferredFocus?: boolean; onPress?: () => void }) {
  const [focused, setFocused] = useState(false);
  return (
    <Pressable
      onPress={onPress}
      focusable
      // @ts-ignore — TV-only prop
      hasTVPreferredFocus={hasTVPreferredFocus}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      style={[style, focused && tvStyles.focused]}
    >
      {children}
    </Pressable>
  );
}

const FocusableTile = memo(function FocusableTile({
  width, children, onPress, onFocus,
}: { width: number; children: React.ReactNode; onPress?: () => void; onFocus?: () => void }) {
  const [focused, setFocused] = useState(false);
  return (
    <Pressable
      focusable
      onPress={onPress}
      onFocus={() => { setFocused(true); onFocus?.(); }}
      onBlur={() => setFocused(false)}
      style={[{ width, borderRadius: 14 }, focused && tvStyles.focusedTile]}
    >
      {children}
    </Pressable>
  );
});

const styles = StyleSheet.create({
  hero: { justifyContent: "flex-end" },
  heroInner: { padding: 24, paddingBottom: 28, gap: 12 },
  heroTitle: { color: "#fff", fontSize: 38, fontWeight: "700", letterSpacing: -0.8, marginTop: 8 },
  heroMetaRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  heroMeta: { color: colors.textMuted, fontSize: 12, fontWeight: "500" },
  dot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: colors.textDim },
  heroDesc: { color: colors.textMuted, fontSize: 14, lineHeight: 20, marginTop: 4 },
  heroButtons: { flexDirection: "row", gap: 12, marginTop: 12 },
  heroDots: { flexDirection: "row", gap: 6, marginTop: 12 },
  heroDotIndicator: { width: 18, height: 3, borderRadius: 2, backgroundColor: "rgba(255,255,255,0.25)" },
  heroDotActive: { backgroundColor: colors.accent, width: 28 },
  playBtn: { paddingHorizontal: 22, paddingVertical: 12, borderRadius: 12, backgroundColor: colors.accent },
  playBtnTV: { paddingHorizontal: 36, paddingVertical: 18, borderRadius: 14 },
  playBtnText: { color: colors.accentForeground, fontWeight: "700", fontSize: 14 },
  infoBtn: {
    paddingHorizontal: 22, paddingVertical: 12, borderRadius: 12,
    backgroundColor: "rgba(255,255,255,0.12)", borderWidth: 1, borderColor: colors.border,
  },
  infoBtnTV: { paddingHorizontal: 36, paddingVertical: 18, borderRadius: 14 },
  infoBtnText: { color: "#fff", fontWeight: "600", fontSize: 14 },

  tabRow: {
    flexDirection: "row", gap: 8, paddingHorizontal: 20, marginTop: 20,
    borderBottomWidth: 1, borderBottomColor: colors.border,
  },
  tab: { paddingHorizontal: 14, paddingVertical: 12, marginBottom: -1, borderBottomWidth: 2, borderBottomColor: "transparent" },
  tabActive: { borderBottomColor: colors.accent },
  tabText: { color: colors.textMuted, fontWeight: "600", fontSize: 14 },
  tabTextActive: { color: "#fff" },

  rowTitleWrap: { paddingHorizontal: 20, marginBottom: 14, gap: 4 },
  rowTitle: { color: "#fff", fontSize: 19, fontWeight: "700", letterSpacing: -0.3 },
  rowReason: { color: colors.textDim, fontSize: 12.5, fontWeight: "500" },

  poster: {
    borderRadius: 12, overflow: "hidden", backgroundColor: "#111",
    borderWidth: 1, borderColor: colors.border,
  },
  landscape: {
    borderRadius: 12, overflow: "hidden", backgroundColor: "#111",
    borderWidth: 1, borderColor: colors.border,
  },
  progressTrack: {
    position: "absolute", left: 10, right: 10, bottom: 10,
    height: 3, borderRadius: 2, backgroundColor: "rgba(255,255,255,0.2)",
  },
  progressFill: { height: "100%", backgroundColor: colors.accent, borderRadius: 2 },
  removeBtn: {
    position: "absolute", top: 8, right: 8, width: 26, height: 26,
    borderRadius: 13, backgroundColor: "rgba(0,0,0,0.55)", alignItems: "center", justifyContent: "center",
    borderWidth: 1, borderColor: "rgba(255,255,255,0.18)",
  },
  removeBtnText: { color: "#fff", fontSize: 12, fontWeight: "700" },
  tileTitle: { color: "#fff", fontSize: 13, fontWeight: "500", marginTop: 10 },
  tileMeta: { color: colors.textDim, fontSize: 11.5, fontWeight: "500", marginTop: 3 },

  rankNum: {
    color: "rgba(255,255,255,0.12)", fontSize: 96, fontWeight: "900",
    letterSpacing: -8, marginRight: -8, lineHeight: 96,
  },

  moodChip: {
    flexDirection: "row", alignItems: "center", gap: 10,
    paddingHorizontal: 14, paddingVertical: 10,
    borderRadius: 999, borderWidth: 1, borderColor: colors.border,
    backgroundColor: "rgba(255,255,255,0.04)", maxWidth: 280,
  },
  moodDot: { width: 10, height: 10, borderRadius: 5 },
  moodLabel: { color: "rgba(255,255,255,0.85)", fontSize: 13, fontWeight: "700" },
  moodBlurb: { color: colors.textDim, fontSize: 11, marginTop: 1 },

  leaveBadge: {
    position: "absolute", top: 8, left: 8, paddingHorizontal: 8, paddingVertical: 3,
    borderRadius: 6, backgroundColor: "rgba(239,68,68,0.85)",
  },
  leaveBadgeText: { color: "#fff", fontSize: 10, fontWeight: "700", letterSpacing: 0.6 },

  empty: {
    margin: 20, padding: 32, borderRadius: 16, alignItems: "center", gap: 8,
    borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card,
  },
  emptyTitle: { color: "#fff", fontSize: 17, fontWeight: "700" },
  emptyHint: { color: colors.textDim, fontSize: 13, textAlign: "center" },
});

const tvStyles = StyleSheet.create({
  focused: { transform: [{ scale: 1.06 }], ...focusRing },
  focusedTile: { transform: [{ scale: 1.08 }], ...focusRing },
  rowTitleTV: { fontSize: 26, marginBottom: 4 },
});

/* ───────────── ExtensionRows (Priority 8) ─────────────
   Extension-driven home rows. Replaces static demo catalogs.
   Falls back gracefully when no catalogs are available. */
const ExtensionRows = memo(function ExtensionRows({ isTV }: { isTV: boolean }) {
  const { rows, status, error, extensionsInstalled, retry } = useExtensionCatalog();

  if (status === "loading") {
    return (
      <View style={{ padding: 20 }}>
        <Text style={{ color: colors.textDim, fontSize: 13 }}>Loading catalogs…</Text>
      </View>
    );
  }
  if (status === "error") {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyTitle}>Couldn't load catalogs</Text>
        <Text style={styles.emptyHint}>{error ?? "Unknown error"}</Text>
        <Pressable onPress={retry} style={[styles.playBtn, { marginTop: 8 }]}>
          <Text style={styles.playBtnText}>Try again</Text>
        </Pressable>
      </View>
    );
  }
  if (status === "empty") {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyTitle}>No catalog rows yet</Text>
        <Text style={styles.emptyHint}>
          {extensionsInstalled > 0
            ? "Installed extensions don't expose catalogs. Try adding Cinemeta in Settings."
            : "Install a catalog extension (e.g. Cinemeta) from Settings to populate Home."}
        </Text>
      </View>
    );
  }
  return (
    <>
      {rows.map((r) => (
        <Row key={r.key} title={r.title} reason={`From ${r.addonName}`} items={r.items} isTV={isTV} />
      ))}
    </>
  );
});
