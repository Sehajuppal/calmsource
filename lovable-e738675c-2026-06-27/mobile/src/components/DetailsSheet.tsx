// Mobile/TV details sheet — Play / Watchlist / Episodes.
// Opened via the shared `useDetailsSheet` store from any tile.
import React, { memo, useMemo, useState } from "react";
import {
  View,
  Text,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  ImageBackground,
  useWindowDimensions,
} from "react-native";
import { Image } from "expo-image";
import { LinearGradient } from "expo-linear-gradient";
import { create } from "zustand";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "./theme";
import { useIsTV, focusRing } from "../lib/tv";
import { useUserData } from "../lib/userdata";
import { getEpisodes } from "../lib/episodes";
import { castFor, similarTo } from "../lib/recommend";
import { usePlayer } from "../lib/player-store";
import { playbackResolver } from "../lib/repositories";
import type { Title } from "../lib/catalog";


type SheetState = {
  title: Title | null;
  open: (t: Title) => void;
  close: () => void;
};

export const useDetailsSheet = create<SheetState>((set) => ({
  title: null,
  open: (t) => set({ title: t }),
  close: () => set({ title: null }),
}));

export const DetailsSheet = memo(function DetailsSheet() {
  const title = useDetailsSheet((s) => s.title);
  const close = useDetailsSheet((s) => s.close);
  const isTV = useIsTV();
  const { height } = useWindowDimensions();

  const inList = useUserData((s) => (title ? s.watchlist.includes(title.id) : false));
  const toggle = useUserData((s) => s.toggleWatchlist);
  const rating = useUserData((s) => (title ? s.ratings[title.id] : undefined));
  const setRating = useUserData((s) => s.setRating);
  const openPlayer = usePlayer((s) => s.open);

  const [season, setSeason] = useState(1);
  const episodes = useMemo(() => (title?.kind === "series" ? getEpisodes(title) : null), [title]);
  const seasonNums = episodes ? Object.keys(episodes).map(Number).sort((a, b) => a - b) : [];
  const cast = useMemo(() => (title ? castFor(title) : []), [title]);
  const similar = useMemo(() => (title ? similarTo(title, 10) : []), [title]);
  const openSheet = useDetailsSheet((s) => s.open);

  const play = () => {
    if (!title) return;
    openPlayer({ kind: "title", title, streamUrl: playbackResolver.resolveTitle(title) });
    close();
  };


  if (!title) return null;

  return (
    <Modal
      visible
      transparent
      animationType="slide"
      onRequestClose={close}
      statusBarTranslucent
    >
      <View style={styles.backdrop}>
        <Pressable style={StyleSheet.absoluteFill} onPress={close} />
        <View style={[styles.sheet, { maxHeight: height * 0.92 }, isTV && styles.sheetTV]}>
          <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 32 }}>
            <ImageBackground source={{ uri: title.backdrop }} style={styles.cover} imageStyle={{ opacity: 0.85 }}>
              <LinearGradient
                colors={["transparent", "rgba(8,10,16,0.7)", colors.bg]}
                style={StyleSheet.absoluteFill}
              />
              <Pressable onPress={close} hitSlop={12} style={styles.closeBtn}>
                <Ionicons name="close" size={20} color="#fff" />
              </Pressable>
              <View style={styles.coverInner}>
                <Text style={styles.titleText} numberOfLines={2}>{title.name}</Text>
                <View style={styles.metaRow}>
                  <Text style={styles.meta}>{title.year}</Text>
                  <View style={styles.dot} />
                  <Text style={styles.meta}>{title.rating}</Text>
                  <View style={styles.dot} />
                  <Text style={styles.meta}>{title.duration}</Text>
                </View>
              </View>
            </ImageBackground>

            <View style={styles.body}>
              <View style={styles.actions}>
                <Pressable focusable onPress={play} style={[styles.playBtn]}>
                  <Ionicons name="play" size={16} color={colors.accentForeground} />
                  <Text style={styles.playText}>Play</Text>
                </Pressable>
                <Pressable
                  focusable
                  onPress={() => toggle(title.id)}
                  style={[styles.iconBtn, inList && { borderColor: colors.accent }]}
                >
                  <Ionicons
                    name={inList ? "checkmark" : "add"}
                    size={18}
                    color={inList ? colors.accent : "#fff"}
                  />
                  <Text style={[styles.iconBtnText, inList && { color: colors.accent }]}>
                    {inList ? "In My List" : "My List"}
                  </Text>
                </Pressable>
                <Pressable
                  focusable
                  onPress={() => setRating(title.id, rating === 1 ? 0 : 1)}
                  style={[styles.iconBtn, rating === 1 && { borderColor: colors.accent }]}
                >
                  <Ionicons
                    name={rating === 1 ? "thumbs-up" : "thumbs-up-outline"}
                    size={18}
                    color={rating === 1 ? colors.accent : "#fff"}
                  />
                </Pressable>
                <Pressable
                  focusable
                  onPress={() => setRating(title.id, rating === -1 ? 0 : -1)}
                  style={[styles.iconBtn, rating === -1 && { borderColor: "#ef4444" }]}
                >
                  <Ionicons
                    name={rating === -1 ? "thumbs-down" : "thumbs-down-outline"}
                    size={18}
                    color={rating === -1 ? "#ef4444" : "#fff"}
                  />
                </Pressable>
              </View>

              <Text style={styles.desc}>{title.description}</Text>

              <View style={styles.tagRow}>
                {title.genres.map((g) => (
                  <View key={g} style={styles.tag}>
                    <Text style={styles.tagText}>{g}</Text>
                  </View>
                ))}
              </View>

              {cast.length > 0 && (
                <View style={{ marginTop: 22, gap: 10 }}>
                  <Text style={styles.sectionLabel}>Cast</Text>
                  <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
                    {cast.map((c) => (
                      <View key={c.name} style={styles.castChip}>
                        <View style={styles.castAvatar}>
                          <Text style={styles.castInitials}>
                            {c.name.split(" ").map((n) => n[0]).join("").slice(0, 2)}
                          </Text>
                        </View>
                        <View>
                          <Text style={styles.castName}>{c.name}</Text>
                          <Text style={styles.castRole}>{c.role}</Text>
                        </View>
                      </View>
                    ))}
                  </View>
                </View>
              )}

              {similar.length > 0 && (
                <View style={{ marginTop: 22, gap: 10 }}>
                  <Text style={styles.sectionLabel}>More like this</Text>
                  <ScrollView
                    horizontal
                    showsHorizontalScrollIndicator={false}
                    contentContainerStyle={{ gap: 10, paddingRight: 20 }}
                  >
                    {similar.map((t) => (
                      <Pressable
                        key={t.id}
                        focusable
                        onPress={() => openSheet(t)}
                        style={styles.similarCard}
                      >
                        <Image
                          source={{ uri: t.tile }}
                          style={styles.similarImg}
                          contentFit="cover"
                          cachePolicy="memory-disk"
                          recyclingKey={t.id}
                          transition={160}
                          priority="low"
                        />
                        <Text style={styles.similarName} numberOfLines={1}>{t.name}</Text>
                        <Text style={styles.similarMeta} numberOfLines={1}>
                          {t.year} · {t.genres[0]}
                        </Text>
                      </Pressable>
                    ))}
                  </ScrollView>
                </View>
              )}

              {episodes && (
                <View style={{ marginTop: 28 }}>

                  <View style={styles.seasonRow}>
                    {seasonNums.map((s) => {
                      const on = s === season;
                      return (
                        <Pressable
                          key={s}
                          focusable
                          onPress={() => setSeason(s)}
                          style={[styles.seasonChip, on && styles.seasonChipOn]}
                        >
                          <Text style={[styles.seasonText, on && styles.seasonTextOn]}>S{s}</Text>
                        </Pressable>
                      );
                    })}
                  </View>
                  {(episodes[season] ?? []).map((ep) => (
                    <Pressable key={ep.id} focusable onPress={play} style={styles.epRow}>
                      <Image
                        source={{ uri: ep.still }}
                        style={styles.epStill}
                        contentFit="cover"
                        cachePolicy="memory-disk"
                        recyclingKey={ep.id}
                        priority="low"
                      />
                      <View style={{ flex: 1, gap: 4 }}>
                        <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                          <Text style={styles.epTitle} numberOfLines={1}>
                            {ep.number}. {ep.title}
                          </Text>
                          <Text style={styles.epDur}>{ep.duration}</Text>
                        </View>
                        <Text style={styles.epDesc} numberOfLines={2}>{ep.description}</Text>
                      </View>
                    </Pressable>
                  ))}
                </View>
              )}
            </View>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
});

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.65)", justifyContent: "flex-end" },
  sheet: {
    backgroundColor: colors.bg, borderTopLeftRadius: 24, borderTopRightRadius: 24,
    overflow: "hidden", borderWidth: 1, borderColor: colors.border,
  },
  sheetTV: { maxWidth: 1100, alignSelf: "center", width: "92%", borderRadius: 24, marginBottom: 40 },
  cover: { height: 260, justifyContent: "flex-end" },
  coverInner: { padding: 20, gap: 8 },
  closeBtn: {
    position: "absolute", top: 14, right: 14, width: 34, height: 34, borderRadius: 17,
    backgroundColor: "rgba(0,0,0,0.55)", alignItems: "center", justifyContent: "center",
    borderWidth: 1, borderColor: "rgba(255,255,255,0.18)",
  },
  titleText: { color: "#fff", fontSize: 28, fontWeight: "800", letterSpacing: -0.6 },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  meta: { color: colors.textMuted, fontSize: 12, fontWeight: "600" },
  dot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: colors.textDim },

  body: { paddingHorizontal: 20, paddingTop: 18, gap: 14 },
  actions: { flexDirection: "row", gap: 10 },
  playBtn: {
    flexDirection: "row", alignItems: "center", gap: 8,
    backgroundColor: colors.accent, paddingHorizontal: 20, paddingVertical: 12, borderRadius: 12,
    ...focusRing,
  },
  playText: { color: colors.accentForeground, fontWeight: "700", fontSize: 14 },
  iconBtn: {
    flexDirection: "row", alignItems: "center", gap: 8,
    paddingHorizontal: 16, paddingVertical: 12, borderRadius: 12,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.06)",
  },
  iconBtnText: { color: "#fff", fontWeight: "600", fontSize: 13 },

  desc: { color: colors.textMuted, fontSize: 14, lineHeight: 20 },
  tagRow: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  tag: {
    paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.04)",
  },
  tagText: { color: colors.textDim, fontSize: 11, fontWeight: "600", letterSpacing: 0.3 },

  seasonRow: { flexDirection: "row", gap: 8, marginBottom: 14 },
  seasonChip: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 10,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.04)",
  },
  seasonChipOn: { borderColor: colors.accent, backgroundColor: "rgba(46,91,255,0.18)" },
  seasonText: { color: colors.textMuted, fontSize: 13, fontWeight: "700" },
  seasonTextOn: { color: "#fff" },

  epRow: {
    flexDirection: "row", gap: 12, paddingVertical: 10,
    borderTopWidth: 1, borderTopColor: colors.border,
  },
  epStill: { width: 110, height: 64, borderRadius: 8, backgroundColor: "#111" },
  epTitle: { color: "#fff", fontSize: 14, fontWeight: "600", flex: 1, marginRight: 8 },
  epDur: { color: colors.textDim, fontSize: 12, fontWeight: "500" },
  epDesc: { color: colors.textDim, fontSize: 12, lineHeight: 17 },

  sectionLabel: {
    color: colors.textMuted, fontSize: 11, fontWeight: "700",
    letterSpacing: 2, textTransform: "uppercase",
  },
  castChip: {
    flexDirection: "row", alignItems: "center", gap: 8,
    paddingHorizontal: 10, paddingVertical: 6, borderRadius: 999,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.04)",
  },
  castAvatar: {
    width: 26, height: 26, borderRadius: 13, alignItems: "center", justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.12)",
  },
  castInitials: { color: "#fff", fontSize: 10, fontWeight: "700" },
  castName: { color: "#fff", fontSize: 12, fontWeight: "600" },
  castRole: { color: colors.textDim, fontSize: 10 },

  similarCard: { width: 180 },
  similarImg: { width: 180, height: 101, borderRadius: 10, backgroundColor: "#111", marginBottom: 6 },
  similarName: { color: "#fff", fontSize: 13, fontWeight: "600" },
  similarMeta: { color: colors.textDim, fontSize: 11 },
});

