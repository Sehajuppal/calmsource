// Mobile/TV global search — searches across catalog and IPTV channels.
import React, { memo, useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  View, Text, StyleSheet, TextInput, Pressable, ScrollView,
} from "react-native";
import { FlashList } from "@shopify/flash-list";
import { Image } from "expo-image";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";
import type { Title } from "../lib/catalog";
import type { IPTVChannel } from "../lib/iptv";
import { catalogRepository, channelRepository, playbackResolver } from "../lib/repositories";
import { colors } from "../components/theme";
import { useIsTV, focusRing } from "../lib/tv";
import { useDetailsSheet } from "../components/DetailsSheet";
import { usePlayer } from "../lib/player-store";

const FILMS = catalogRepository.getFilms();
const SERIES = catalogRepository.getSeries();
const TOP_TEN = catalogRepository.getTopTen();
const SAMPLE_CHANNELS = channelRepository.getChannels();

const LS_CHANNELS = "lumen.iptv.channels";

const ALL: Title[] = (() => {
  const m = new Map<string, Title>();
  for (const t of [...FILMS, ...SERIES, ...TOP_TEN]) m.set(t.id, t);
  return [...m.values()];
})();

const SUGGESTED = ["thriller", "drama", "sci-fi", "comedy", "documentary", "news", "sports"];

export default function SearchScreen() {
  const isTV = useIsTV();
  const [q, setQ] = useState("");
  const dq = useDeferredValue(q);
  const open = useDetailsSheet((s) => s.open);
  const openPlayer = usePlayer((s) => s.open);

  const [channels, setChannels] = useState<IPTVChannel[]>(SAMPLE_CHANNELS);
  useEffect(() => {
    AsyncStorage.getItem(LS_CHANNELS)
      .then((raw) => {
        if (!raw) return;
        try {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed) && parsed.length) setChannels(parsed);
        } catch {}
      })
      .catch(() => {});
  }, []);

  const titleResults = useMemo(() => {
    const term = dq.trim().toLowerCase();
    if (!term) return [] as Title[];
    return ALL.filter((t) =>
      t.name.toLowerCase().includes(term)
      || t.genres.some((g) => g.toLowerCase().includes(term))
      || t.description.toLowerCase().includes(term),
    ).slice(0, 40);
  }, [dq]);

  const channelResults = useMemo(() => {
    const term = dq.trim().toLowerCase();
    if (!term) return [] as IPTVChannel[];
    return channels.filter((c) =>
      c.name.toLowerCase().includes(term)
      || (c.group ?? "").toLowerCase().includes(term),
    ).slice(0, 30);
  }, [dq, channels]);

  const empty = dq.trim() && !titleResults.length && !channelResults.length;

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.bg }} edges={["top"]}>
      <View style={[styles.header, isTV && { paddingHorizontal: 48, paddingTop: 24 }]}>
        <View style={styles.searchRow}>
          <Ionicons name="search" size={18} color={colors.textMuted} />
          <TextInput
            value={q}
            onChangeText={setQ}
            placeholder="Search titles, channels, genres…"
            placeholderTextColor={colors.textDim}
            autoFocus={!isTV}
            style={[styles.input, isTV && { fontSize: 20 }]}
            returnKeyType="search"
          />
          {q ? (
            <Pressable onPress={() => setQ("")} hitSlop={10}>
              <Ionicons name="close-circle" size={18} color={colors.textDim} />
            </Pressable>
          ) : null}
        </View>

        {!dq.trim() && (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginTop: 14 }}>
            {SUGGESTED.map((s) => (
              <Pressable key={s} focusable onPress={() => setQ(s)} style={styles.chip}>
                <Text style={styles.chipText}>{s}</Text>
              </Pressable>
            ))}
          </ScrollView>
        )}
      </View>

      {empty ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>No matches</Text>
          <Text style={styles.emptyHint}>Try a different word, genre, or channel name.</Text>
        </View>
      ) : (
        <FlashList
          data={[
            ...(titleResults.length ? [{ kind: "header" as const, label: `Titles · ${titleResults.length}` }] : []),
            ...titleResults.map((t) => ({ kind: "title" as const, item: t })),
            ...(channelResults.length ? [{ kind: "header" as const, label: `Live channels · ${channelResults.length}` }] : []),
            ...channelResults.map((c) => ({ kind: "channel" as const, item: c })),
          ]}
          keyExtractor={(row, i) =>
            row.kind === "header" ? `h-${row.label}-${i}` :
            row.kind === "title" ? `t-${row.item.id}` : `c-${row.item.id}`
          }
          getItemType={(row) => row.kind}
          estimatedItemSize={84}
          contentContainerStyle={{ padding: isTV ? 32 : 16 }}
          ItemSeparatorComponent={() => <View style={{ height: 10 }} />}
          renderItem={({ item }) => {
            if (item.kind === "header") return <Text style={styles.section}>{item.label}</Text>;
            if (item.kind === "title")  return <ResultRow item={item.item} onPress={() => open(item.item)} />;
            return (
              <ChannelRow
                item={item.item}
                onPress={() =>
                  openPlayer({ kind: "live", channelId: item.item.id, name: item.item.name, streamUrl: playbackResolver.resolveLive(item.item) })
                }
              />
            );
          }}
          keyboardShouldPersistTaps="handled"
        />
      )}
    </SafeAreaView>
  );
}

const ResultRow = memo(function ResultRow({ item, onPress }: { item: Title; onPress: () => void }) {
  const [focused, setFocused] = useState(false);
  return (
    <Pressable
      focusable
      onPress={onPress}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      style={[styles.row, focused && focusRing]}
    >
      <Image source={{ uri: item.poster }} style={styles.thumb} contentFit="cover" cachePolicy="memory-disk" transition={180} recyclingKey={item.id} priority="low" />
      <View style={{ flex: 1, gap: 4 }}>
        <Text style={styles.rowTitle} numberOfLines={1}>{item.name}</Text>
        <Text style={styles.rowMeta} numberOfLines={1}>
          {item.year} · {item.kind === "series" ? "Series" : "Film"} · {item.genres.slice(0, 2).join(" · ")}
        </Text>
        <Text style={styles.rowDesc} numberOfLines={2}>{item.description}</Text>
      </View>
    </Pressable>
  );
});

const ChannelRow = memo(function ChannelRow({ item, onPress }: { item: IPTVChannel; onPress: () => void }) {
  const [focused, setFocused] = useState(false);
  return (
    <Pressable
      focusable
      onPress={onPress}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      style={[styles.row, focused && focusRing]}
    >
      <View style={[styles.thumb, styles.chThumb]}>
        {item.logo ? (
          <Image source={{ uri: item.logo }} style={StyleSheet.absoluteFill} contentFit="contain" cachePolicy="memory-disk" recyclingKey={item.id} priority="low" />
        ) : (
          <Text style={styles.chInitial}>{item.name.slice(0, 2).toUpperCase()}</Text>
        )}
      </View>
      <View style={{ flex: 1, gap: 4 }}>
        <Text style={styles.rowTitle} numberOfLines={1}>{item.name}</Text>
        <Text style={styles.rowMeta} numberOfLines={1}>
          Live · {item.group ?? "Channel"}
        </Text>
      </View>
      <Ionicons name="play-circle" size={28} color={colors.accent} />
    </Pressable>
  );
});

const styles = StyleSheet.create({
  header: { paddingHorizontal: 16, paddingTop: 12 },
  searchRow: {
    flexDirection: "row", alignItems: "center", gap: 10,
    paddingHorizontal: 14, paddingVertical: 12, borderRadius: 14,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.04)",
  },
  input: { flex: 1, color: "#fff", fontSize: 16, padding: 0 },
  chip: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999, marginRight: 8,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.04)",
  },
  chipText: { color: colors.textMuted, fontSize: 12.5, fontWeight: "600" },

  section: {
    color: colors.textDim, fontSize: 11, fontWeight: "700",
    letterSpacing: 1.6, textTransform: "uppercase", marginTop: 6, marginBottom: 2,
  },
  row: {
    flexDirection: "row", alignItems: "center", gap: 12, padding: 10, borderRadius: 14,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.03)",
  },
  thumb: { width: 64, height: 90, borderRadius: 8, backgroundColor: "#111" },
  chThumb: { height: 64, alignItems: "center", justifyContent: "center" },
  chInitial: { color: "#fff", fontWeight: "800", fontSize: 20, letterSpacing: 1 },
  rowTitle: { color: "#fff", fontSize: 15, fontWeight: "700" },
  rowMeta: { color: colors.textDim, fontSize: 11.5, fontWeight: "600" },
  rowDesc: { color: colors.textMuted, fontSize: 12.5, lineHeight: 17 },

  empty: { padding: 40, alignItems: "center", gap: 6 },
  emptyTitle: { color: "#fff", fontSize: 18, fontWeight: "700" },
  emptyHint: { color: colors.textDim, fontSize: 13 },
});
