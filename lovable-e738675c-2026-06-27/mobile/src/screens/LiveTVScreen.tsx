import React, { memo, useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  TextInput,
} from "react-native";
import { FlashList, type ListRenderItem } from "@shopify/flash-list";
import AsyncStorage from "@react-native-async-storage/async-storage";
import type { IPTVChannel } from "../lib/iptv";
import { channelRepository, playbackResolver } from "../lib/repositories";
import { nowNext, fmtTime } from "../lib/epg";
import { EyebrowPill } from "../components/EyebrowPill";
import { colors } from "../components/theme";

const SAMPLE_CHANNELS = channelRepository.getChannels();
import { useIsTV, focusRing, TV_CHANNEL_W, TV_CHANNEL_H } from "../lib/tv";
import { usePlayer } from "../lib/player-store";

const LS_CHANNELS = "lumen.iptv.channels";
const LS_FAVORITES = "lumen.iptv.favorites.v1";
const ALL_GROUP = "__all__";
const FAV_GROUP = "__fav__";

export default function LiveTVScreen() {
  const isTV = useIsTV();
  const [channels, setChannels] = useState<IPTVChannel[]>(SAMPLE_CHANNELS);
  const [active, setActive] = useState<IPTVChannel | null>(SAMPLE_CHANNELS[0] ?? null);
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [activeGroup, setActiveGroup] = useState<string>(ALL_GROUP);
  const [epgTick, setEpgTick] = useState(0);
  const openPlayer = usePlayer((s) => s.open);

  useEffect(() => {
    let cancelled = false;
    AsyncStorage.getItem(LS_CHANNELS).then((raw) => {
      if (cancelled || !raw) return;
      try {
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) return;
        const valid = parsed.filter(
          (c: unknown): c is IPTVChannel =>
            !!c && typeof c === "object" &&
            typeof (c as IPTVChannel).id === "string" &&
            typeof (c as IPTVChannel).name === "string" &&
            typeof (c as IPTVChannel).url === "string",
        );
        if (valid.length) setChannels(valid);
      } catch {}
    });
    AsyncStorage.getItem(LS_FAVORITES).then((raw) => {
      if (cancelled || !raw) return;
      try {
        const arr = JSON.parse(raw);
        if (Array.isArray(arr)) setFavorites(new Set(arr.filter((s) => typeof s === "string")));
      } catch {}
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const t = setInterval(() => setEpgTick((n) => n + 1), 30_000);
    return () => clearInterval(t);
  }, []);

  // Sync the active channel to the latest list. We read `active` via a
  // ref so re-running `setActive` doesn't bounce the effect again (which
  // it would if `active` were a real dep).
  const activeRef = useRef<IPTVChannel | null>(null);
  activeRef.current = active;
  useEffect(() => {
    const cur = activeRef.current;
    if (!channels.length) { if (cur) setActive(null); return; }
    if (!cur || !channels.find((c) => c.id === cur.id)) setActive(channels[0]);
  }, [channels]);

  const toggleFavorite = useCallback((id: string) => {
    setFavorites((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      AsyncStorage.setItem(LS_FAVORITES, JSON.stringify([...next])).catch(() => {});
      return next;
    });
  }, []);

  // Single entry point for picking a channel: update selection metadata AND
  // hand playback off to the global PlayerRoot. Mirrors web's pickChannel.
  // No inline VideoView on this screen — avoids double-decoding the same
  // stream and keeps mini-player behavior consistent with the rest of the app.
  const pickChannel = useCallback((ch: IPTVChannel) => {
    setActive(ch);
    openPlayer({
      kind: "live",
      channelId: ch.id,
      name: ch.name,
      streamUrl: playbackResolver.resolveLive(ch),
    });
  }, [openPlayer]);

  // Available groups (with Favorites first if any).
  const groups = useMemo(() => {
    const set = new Set<string>();
    channels.forEach((c) => set.add(c.group ?? "Live"));
    const list = [ALL_GROUP, ...(favorites.size ? [FAV_GROUP] : []), ...Array.from(set).sort()];
    return list;
  }, [channels, favorites.size]);

  useEffect(() => {
    if (!groups.includes(activeGroup)) setActiveGroup(ALL_GROUP);
  }, [groups, activeGroup]);

  const filtered = useMemo(() => {
    const q = deferredQuery.trim().toLowerCase();
    return channels.filter((c) => {
      if (activeGroup === FAV_GROUP && !favorites.has(c.id)) return false;
      if (activeGroup !== ALL_GROUP && activeGroup !== FAV_GROUP && (c.group ?? "Live") !== activeGroup) return false;
      if (!q) return true;
      const name = c.name.toLowerCase();
      const group = (c.group ?? "").toLowerCase();
      return name.includes(q) || group.includes(q);
    });
  }, [channels, deferredQuery, activeGroup, favorites]);

  const activeEpg = useMemo(() => {
    if (!active) return null;
    // `epgTick` is intentionally in deps so this recomputes every 30s.
    // `nowNext` reads Date.now() internally, so we don't need to pass it.
    void epgTick;
    return nowNext(active);
  }, [active, epgTick]);

  const onRightNow = useMemo(() => {
    const favList = channels.filter((c) => favorites.has(c.id));
    const rest = channels.filter((c) => !favorites.has(c.id));
    return [...favList, ...rest].slice(0, 6).map((c) => ({ c, ...nowNext(c) }));
  }, [channels, favorites, epgTick]);

  const activeId = active?.id ?? null;

  const renderChannel = useCallback<ListRenderItem<IPTVChannel>>(
    ({ item }) => (
      <ChannelTile
        item={item}
        isActive={item.id === activeId}
        isFav={favorites.has(item.id)}
        onPress={pickChannel}
        onFav={toggleFavorite}
      />
    ),
    [activeId, favorites, toggleFavorite, pickChannel],
  );

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: 48 }}
    >
      <View style={styles.header}>
        <EyebrowPill label="LIVE TV" />
        <Text style={styles.title}>Channels</Text>
        <Text style={styles.subtitle}>
          {channels.length} channels loaded · manage your playlist in Settings.
        </Text>
      </View>

      {active ? (
        <View style={styles.heroCard}>
          <View style={styles.playerMeta}>
            <View style={styles.livePill}>
              <View style={styles.liveDot} />
              <Text style={styles.liveText}>LIVE</Text>
            </View>
            <Text style={styles.playerTitle} numberOfLines={1}>{active.name}</Text>
            <Text style={styles.playerGroup}>{active.group ?? "Live"}</Text>
          </View>
          {activeEpg ? (
            <View style={styles.epgPill}>
              <Text style={styles.epgNow} numberOfLines={1}>
                Now · {activeEpg.now.title}
              </Text>
              <Text style={styles.epgNext} numberOfLines={1}>
                Next · {fmtTime(activeEpg.next.start)} {activeEpg.next.title}
              </Text>
            </View>
          ) : null}
          <Pressable
            focusable
            onPress={() => pickChannel(active)}
            style={({ focused }) => [styles.watchBtn, focused && focusRing, isTV && { paddingVertical: 16 }]}
          >
            <Text style={styles.watchBtnText}>▶  Watch live</Text>
          </Pressable>
        </View>
      ) : (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>No channels loaded</Text>
          <Text style={styles.emptyHint}>Add an M3U playlist from Settings.</Text>
        </View>
      )}

      {onRightNow.length ? (
        <View style={{ marginTop: 26 }}>
          <Text style={styles.sectionHeader}>On right now</Text>
          <View style={styles.nowGrid}>
            {onRightNow.map(({ c, now, next }) => (
              <Pressable
                key={c.id}
                focusable
                onPress={() => pickChannel(c)}
                style={({ focused }) => [styles.nowCard, focused && focusRing]}
              >
                <Text style={styles.nowChan} numberOfLines={1}>{c.name}</Text>
                <Text style={styles.nowTitle} numberOfLines={1}>{now.title}</Text>
                <Text style={styles.nowNext} numberOfLines={1}>
                  {fmtTime(now.start)}–{fmtTime(now.end)} · Next {next.title}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>
      ) : null}

      <View style={{ paddingHorizontal: 20, marginTop: 24 }}>
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Search channels"
          placeholderTextColor={colors.textDim}
          style={styles.search}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
        />
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: 20, gap: 8, paddingTop: 14 }}
      >
        {groups.map((g) => {
          const on = activeGroup === g;
          const label = g === ALL_GROUP ? "All" : g === FAV_GROUP ? "★ Favorites" : g;
          return (
            <Pressable
              key={g}
              onPress={() => setActiveGroup(g)}
              focusable
              style={({ focused }) => [styles.groupChip, on && styles.groupChipActive, focused && focusRing]}
            >
              <Text style={[styles.groupChipText, on && styles.groupChipTextActive]}>{label}</Text>
            </Pressable>
          );
        })}
      </ScrollView>

      <View style={{ marginTop: 14, height: TV_CHANNEL_H + 24 }}>
        {/* Horizontal FlashList inside a vertical ScrollView needs an
            explicit height — otherwise it measures 0 on Android and
            renders nothing. */}
        <FlashList
          horizontal
          data={filtered}
          keyExtractor={(c) => c.id}
          renderItem={renderChannel}
          estimatedItemSize={TV_CHANNEL_W + 12}
          drawDistance={400}
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ paddingHorizontal: 20 }}
          ItemSeparatorComponent={() => <View style={{ width: 12 }} />}
        />
      </View>
    </ScrollView>
  );
}

const ChannelTile = memo(function ChannelTile({
  item, isActive, isFav, onPress, onFav,
}: {
  item: IPTVChannel;
  isActive: boolean;
  isFav: boolean;
  onPress: (c: IPTVChannel) => void;
  onFav: (id: string) => void;
}) {
  const isTV = useIsTV();
  const [focused, setFocused] = useState(false);
  return (
    <View style={{ position: "relative" }}>
      <Pressable
        onPress={() => onPress(item)}
        focusable
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        style={[
          styles.channel,
          isTV && { width: TV_CHANNEL_W, height: TV_CHANNEL_H, padding: 18 },
          isActive && styles.channelActive,
          focused && { transform: [{ scale: 1.08 }], ...focusRing },
        ]}
      >
        <Text style={[styles.channelInitial, isTV && { fontSize: 32 }]}>
          {item.name.charAt(0).toUpperCase()}
        </Text>
        <Text style={[styles.channelName, isTV && { fontSize: 15 }]} numberOfLines={2}>
          {item.name}
        </Text>
      </Pressable>
      <Pressable
        onPress={() => onFav(item.id)}
        hitSlop={8}
        style={styles.favBtn}
      >
        <Text style={[styles.favStar, isFav && { color: "#FFD166" }]}>{isFav ? "★" : "☆"}</Text>
      </Pressable>
    </View>
  );
});

const styles = StyleSheet.create({
  header: { padding: 24, paddingTop: 32, gap: 10 },
  title: { color: "#fff", fontSize: 34, fontWeight: "700", letterSpacing: -0.6, marginTop: 8 },
  subtitle: { color: colors.textMuted, fontSize: 14, lineHeight: 20 },
  heroCard: {
    marginHorizontal: 20, padding: 16, borderRadius: 16, gap: 12,
    backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border,
  },
  playerMeta: { flexDirection: "row", alignItems: "center", gap: 10 },
  livePill: {
    flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 8, paddingVertical: 3,
    borderRadius: 6, backgroundColor: "rgba(255,255,255,0.08)", borderWidth: 1, borderColor: colors.border,
  },
  liveDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: "#ef4444" },
  liveText: { color: "#fff", fontSize: 10, fontWeight: "700", letterSpacing: 1.2 },
  playerTitle: { color: "#fff", fontSize: 15, fontWeight: "600", flex: 1 },
  playerGroup: { color: colors.textDim, fontSize: 12 },
  epgPill: {
    padding: 12, borderRadius: 12,
    backgroundColor: "rgba(255,255,255,0.05)", borderWidth: 1, borderColor: colors.border, gap: 2,
  },
  epgNow: { color: "#fff", fontSize: 13, fontWeight: "700" },
  epgNext: { color: colors.textDim, fontSize: 12 },
  watchBtn: {
    alignSelf: "flex-start", paddingHorizontal: 22, paddingVertical: 12,
    borderRadius: 12, backgroundColor: colors.accent,
  },
  watchBtnText: { color: colors.accentForeground, fontWeight: "700", fontSize: 14 },
  empty: {
    margin: 20, padding: 28, borderRadius: 14, borderWidth: 1, borderColor: colors.border,
    backgroundColor: colors.card, alignItems: "center", gap: 6,
  },
  emptyText: { color: "#fff", fontSize: 16, fontWeight: "600" },
  emptyHint: { color: colors.textDim, fontSize: 13 },
  search: {
    paddingHorizontal: 14, paddingVertical: 12, borderRadius: 12,
    backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border,
    color: "#fff", fontSize: 14,
  },
  sectionHeader: {
    color: "#fff", fontSize: 17, fontWeight: "700", letterSpacing: -0.3,
    paddingHorizontal: 20, marginBottom: 12,
  },
  nowGrid: {
    paddingHorizontal: 20, gap: 10, flexDirection: "row", flexWrap: "wrap",
  },
  nowCard: {
    width: "48%", padding: 12, borderRadius: 12,
    borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card, gap: 3,
  },
  nowChan: { color: colors.accent, fontSize: 11, fontWeight: "700", letterSpacing: 0.6 },
  nowTitle: { color: "#fff", fontSize: 13, fontWeight: "600" },
  nowNext: { color: colors.textDim, fontSize: 11 },
  groupChip: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999,
    borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card,
  },
  groupChipActive: { borderColor: colors.accent, backgroundColor: "rgba(46,91,255,0.18)" },
  groupChipText: { color: colors.textMuted, fontSize: 12, fontWeight: "600" },
  groupChipTextActive: { color: "#fff" },
  channel: {
    width: 130, height: 110, padding: 14, borderRadius: 14,
    backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, justifyContent: "space-between",
  },
  channelActive: { borderColor: colors.accent, backgroundColor: "rgba(46,91,255,0.12)" },
  channelInitial: { color: "#fff", fontSize: 24, fontWeight: "700", opacity: 0.8 },
  channelName: { color: colors.textMuted, fontSize: 12, fontWeight: "500" },
  favBtn: {
    position: "absolute", top: 6, right: 6, width: 28, height: 28,
    alignItems: "center", justifyContent: "center", borderRadius: 14,
    backgroundColor: "rgba(0,0,0,0.45)",
  },
  favStar: { color: "rgba(255,255,255,0.7)", fontSize: 16, fontWeight: "700" },
});
