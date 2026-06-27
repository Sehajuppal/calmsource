// Mobile/TV full-screen + mini player. Mirrors web src/components/Player.tsx
// at the feature level: progress persistence, skip-intro, playback rate, close,
// and a mini overlay above the tab bar.
//
// Perf/UX: the VideoPlayer instance is created ONCE per source at PlayerRoot
// and reused across full ↔ mini transitions. Previously each child created its
// own useVideoPlayer, so minimizing the player reinitialised the stream and
// restarted playback from 0 — which is both a UX bug and a real perf hit
// (manifest fetch + decoder warm-up on every transition).
import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  Modal,
  StatusBar,
  useWindowDimensions,
} from "react-native";
import { Image } from "expo-image";
import { Ionicons } from "@expo/vector-icons";
import { useVideoPlayer, VideoView } from "expo-video";
type VPlayer = ReturnType<typeof useVideoPlayer>;
import { usePlayer, type PlayerSource } from "../lib/player-store";
import { useUserData } from "../lib/userdata";
import { colors } from "./theme";

const SPEEDS = [0.75, 1, 1.25, 1.5, 2] as const;
const SKIP_INTRO_SECONDS = 90;

export function PlayerRoot() {
  const source = usePlayer((s) => s.source);
  const mode = usePlayer((s) => s.mode);
  if (!source || mode === "closed") return null;
  // Mount Host with a key tied to the stream URL so a *new* source spawns a
  // fresh player, but full/mini swaps within the same source reuse it.
  return <PlayerHost key={source.streamUrl} source={source} mode={mode} />;
}

function PlayerHost({ source, mode }: { source: PlayerSource; mode: "full" | "mini" }) {
  const player = useVideoPlayer(source.streamUrl, (p) => {
    p.loop = false;
    // Seek to saved resume point (>5s, <95%) before kicking off playback.
    if (source.kind === "title") {
      try {
        const entry = useUserData.getState().continueWatching[source.title.id];
        if (entry && entry.position > 5) {
          const dur = entry.duration;
          if (!dur || entry.position / dur < 0.95) {
            (p as unknown as { currentTime: number }).currentTime = entry.position;
          }
        }
      } catch { /* noop */ }
    }
    p.play();
  });

  const pushHistory = useUserData((s) => s.pushHistory);
  const recordProgress = useUserData((s) => s.recordProgress);
  // Subscribe to the resume entry so we can re-apply the seek once the
  // store hydrates from AsyncStorage. The init callback above runs
  // synchronously on the *first* render — before hydration on cold-start —
  // and the entry is empty, so without this effect the resume position
  // would be silently dropped.
  const resumeEntry = useUserData((s) =>
    source.kind === "title" ? s.continueWatching[source.title.id] : undefined,
  );
  const appliedResumeRef = React.useRef(false);
  useEffect(() => {
    if (appliedResumeRef.current) return;
    if (source.kind !== "title" || !resumeEntry) return;
    if (resumeEntry.position <= 5) return;
    const dur = resumeEntry.duration;
    if (dur && resumeEntry.position / dur >= 0.95) return;
    try {
      (player as unknown as { currentTime: number }).currentTime = resumeEntry.position;
      appliedResumeRef.current = true;
    } catch { /* noop */ }
  }, [player, source, resumeEntry]);

  // History — once per session per title.
  useEffect(() => {
    if (source.kind === "title") pushHistory(source.title.id);
  }, [source, pushHistory]);


  // Persist progress every 5s for VOD only.
  useEffect(() => {
    if (source.kind !== "title") return;
    const id = source.title.id;
    const iv = setInterval(() => {
      try {
        const pos = (player as unknown as { currentTime?: number }).currentTime ?? 0;
        const dur = (player as unknown as { duration?: number }).duration ?? 0;
        if (pos > 5 && dur > 30) recordProgress(id, pos, dur);
      } catch {/* noop */}
    }, 5000);
    return () => clearInterval(iv);
  }, [player, source, recordProgress]);

  return mode === "mini"
    ? <MiniPlayer source={source} player={player} />
    : <FullPlayer source={source} player={player} />;
}

function FullPlayer({ source, player }: { source: PlayerSource; player: VPlayer }) {
  const minimize = usePlayer((s) => s.minimize);
  const close = usePlayer((s) => s.close);
  const [speedIdx, setSpeedIdx] = useState(1);
  const { width, height } = useWindowDimensions();

  useEffect(() => {
    try { (player as unknown as { playbackRate: number }).playbackRate = SPEEDS[speedIdx]; } catch {}
  }, [player, speedIdx]);

  const skipIntro = () => {
    try {
      const p = player as unknown as { currentTime: number };
      p.currentTime = (p.currentTime ?? 0) + SKIP_INTRO_SECONDS;
    } catch {}
  };

  const title = source.kind === "title" ? source.title.name : source.name;
  const subtitle = source.kind === "title"
    ? `${source.title.year} · ${source.title.genres[0]}`
    : "Live";

  return (
    <Modal visible transparent={false} animationType="fade" onRequestClose={close} statusBarTranslucent>
      <StatusBar hidden />
      <View style={[styles.fullRoot, { width, height }]}>
        <VideoView
          player={player}
          style={StyleSheet.absoluteFill}
          contentFit="contain"
          nativeControls
          allowsPictureInPicture
        />
        <View style={styles.topBar} pointerEvents="box-none">
          <Pressable
            onPress={minimize}
            hitSlop={12}
            focusable
            style={({ focused }) => [styles.topBtn, focused && styles.topBtnFocused]}
          >
            <Ionicons name="chevron-down" size={22} color="#fff" />
          </Pressable>
          <View style={{ flex: 1, marginHorizontal: 12 }}>
            <Text style={styles.topTitle} numberOfLines={1}>{title}</Text>
            <Text style={styles.topSub} numberOfLines={1}>{subtitle}</Text>
          </View>
          <Pressable
            onPress={() => setSpeedIdx((i) => (i + 1) % SPEEDS.length)}
            hitSlop={10}
            focusable
            style={({ focused }) => [styles.speedPill, focused && styles.topBtnFocused]}
          >
            <Text style={styles.speedText}>{SPEEDS[speedIdx]}×</Text>
          </Pressable>
          {source.kind === "title" && (
            <Pressable
              onPress={skipIntro}
              hitSlop={10}
              focusable
              hasTVPreferredFocus
              style={({ focused }) => [styles.skipBtn, focused && styles.topBtnFocused]}
            >
              <Ionicons name="play-forward" size={16} color="#fff" />
              <Text style={styles.skipText}>Skip intro</Text>
            </Pressable>
          )}
          <Pressable
            onPress={close}
            hitSlop={12}
            focusable
            style={({ focused }) => [styles.topBtn, focused && styles.topBtnFocused]}
          >
            <Ionicons name="close" size={22} color="#fff" />
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

function MiniPlayer({ source, player }: { source: PlayerSource; player: VPlayer }) {
  const restore = usePlayer((s) => s.restore);
  const close = usePlayer((s) => s.close);

  const thumb = source.kind === "title" ? source.title.tile : null;
  const label = source.kind === "title" ? source.title.name : source.name;

  return (
    <View pointerEvents="box-none" style={styles.miniWrap}>
      <Pressable onPress={restore} style={styles.miniCard}>
        <View style={styles.miniThumb}>
          {thumb ? (
            <Image source={{ uri: thumb }} style={StyleSheet.absoluteFill} contentFit="cover" />
          ) : (
            <View style={[StyleSheet.absoluteFill, { backgroundColor: "#000" }]} />
          )}
          <VideoView
            player={player}
            style={StyleSheet.absoluteFill}
            contentFit="cover"
            nativeControls={false}
          />
        </View>
        <View style={{ flex: 1, paddingHorizontal: 12, gap: 2 }}>
          <Text style={styles.miniTitle} numberOfLines={1}>{label}</Text>
          <Text style={styles.miniSub} numberOfLines={1}>
            {source.kind === "live" ? "Live" : "Tap to resume"}
          </Text>
        </View>
        <Pressable onPress={restore} hitSlop={8} style={styles.miniIcon}>
          <Ionicons name="expand" size={18} color="#fff" />
        </Pressable>
        <Pressable onPress={close} hitSlop={8} style={styles.miniIcon}>
          <Ionicons name="close" size={18} color="#fff" />
        </Pressable>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  fullRoot: { flex: 1, backgroundColor: "#000" },
  topBar: {
    position: "absolute", top: 0, left: 0, right: 0,
    flexDirection: "row", alignItems: "center", gap: 8,
    paddingTop: 44, paddingBottom: 14, paddingHorizontal: 14,
    backgroundColor: "rgba(0,0,0,0.55)",
  },
  topBtn: {
    width: 38, height: 38, borderRadius: 19,
    alignItems: "center", justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.08)",
    borderWidth: 1, borderColor: "rgba(255,255,255,0.15)",
  },
  topTitle: { color: "#fff", fontSize: 14, fontWeight: "700" },
  topSub: { color: "rgba(255,255,255,0.6)", fontSize: 11, fontWeight: "500" },
  speedPill: {
    paddingHorizontal: 12, paddingVertical: 7, borderRadius: 999,
    backgroundColor: "rgba(255,255,255,0.10)",
    borderWidth: 1, borderColor: "rgba(255,255,255,0.18)",
  },
  speedText: { color: "#fff", fontWeight: "700", fontSize: 12, letterSpacing: 0.3 },
  skipBtn: {
    flexDirection: "row", alignItems: "center", gap: 6,
    paddingHorizontal: 10, paddingVertical: 7, borderRadius: 999,
    backgroundColor: "rgba(255,255,255,0.10)",
    borderWidth: 1, borderColor: "rgba(255,255,255,0.18)",
  },
  skipText: { color: "#fff", fontWeight: "700", fontSize: 11 },
  topBtnFocused: {
    borderColor: "#fff",
    backgroundColor: "rgba(255,255,255,0.22)",
    shadowColor: "#fff", shadowOpacity: 0.35, shadowRadius: 14, shadowOffset: { width: 0, height: 0 },
    elevation: 10,
  },

  miniWrap: {
    position: "absolute", left: 12, right: 12, bottom: 92,
    zIndex: 50,
  },
  miniCard: {
    flexDirection: "row", alignItems: "center",
    padding: 8, borderRadius: 16,
    backgroundColor: "rgba(12,14,22,0.94)",
    borderWidth: 1, borderColor: colors.border,
    shadowColor: "#000", shadowOpacity: 0.5, shadowRadius: 18, shadowOffset: { width: 0, height: 8 },
    elevation: 20,
  },
  miniThumb: {
    width: 86, height: 50, borderRadius: 10, overflow: "hidden",
    backgroundColor: "#111",
  },
  miniTitle: { color: "#fff", fontSize: 13, fontWeight: "700" },
  miniSub: { color: "rgba(255,255,255,0.55)", fontSize: 11 },
  miniIcon: {
    width: 32, height: 32, borderRadius: 16, alignItems: "center", justifyContent: "center",
  },
});
