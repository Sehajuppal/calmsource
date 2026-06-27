import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  Pressable,
  Alert,
  Switch,
  Share,
} from "react-native";
import * as DocumentPicker from "expo-document-picker";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { parseM3U, SAMPLE_CHANNELS, type IPTVChannel } from "../lib/iptv";
import { EyebrowPill } from "../components/EyebrowPill";
import { colors } from "../components/theme";
import { useUserData, type Preferences } from "../lib/userdata";
import { providerRegistry } from "../lib/providers/registry";
import { createXtreamProvider } from "../lib/providers/xtream";
import { focusRing } from "../lib/tv";

// Wraps a Pressable to apply a focus ring on TV remotes. Visible on web/mobile
// touch only when keyboard-focused, identical to web's :focus-visible look.
function TVPressable({
  style, children, ...rest
}: React.ComponentProps<typeof Pressable>) {
  return (
    <Pressable
      focusable
      {...rest}
      style={(state) => [
        typeof style === "function" ? style(state) : style,
        state.focused && focusRing,
      ]}
    >
      {children as React.ReactNode}
    </Pressable>
  );
}

const LS_CHANNELS = "lumen.iptv.channels";
const LS_URL = "lumen.iptv.lastUrl";
const LS_XTREAM = "lumen.iptv.xtream.v1";

type XtreamCreds = { host: string; username: string; password: string };

export default function SettingsScreen({
  onSwitchProfile,
}: { onSwitchProfile: () => void }) {
  const [url, setUrl] = useState("");
  const [count, setCount] = useState(0);
  const [busy, setBusy] = useState(false);

  const [xtream, setXtream] = useState<XtreamCreds>({ host: "", username: "", password: "" });

  const prefs = useUserData((s) => s.preferences);
  const setPreference = useUserData((s) => s.setPreference);

  useEffect(() => {
    AsyncStorage.getItem(LS_URL).then((u) => u && setUrl(u)).catch(() => {});
    AsyncStorage.getItem(LS_CHANNELS).then((raw) => {
      if (!raw) return;
      try {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) setCount(parsed.length);
      } catch {}
    }).catch(() => {});
    AsyncStorage.getItem(LS_XTREAM).then((raw) => {
      if (!raw) return;
      try {
        const parsed = JSON.parse(raw) as XtreamCreds;
        if (parsed?.host) {
          setXtream({ host: parsed.host ?? "", username: parsed.username ?? "", password: parsed.password ?? "" });
          if (parsed.host && parsed.username && parsed.password) {
            providerRegistry.register(createXtreamProvider("xtream-default", "Xtream", parsed));
          }
        }
      } catch {}
    }).catch(() => {});
  }, []);

  const saveChannels = async (channels: IPTVChannel[]) => {
    try {
      await AsyncStorage.setItem(LS_CHANNELS, JSON.stringify(channels));
      setCount(channels.length);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Storage error";
      throw new Error(`Could not save channels (${msg})`);
    }
  };

  const loadFromUrl = async () => {
    const trimmed = url.trim();
    if (!trimmed) return;
    try {
      const u = new URL(trimmed);
      if (!/^https?:$/.test(u.protocol)) {
        Alert.alert("Invalid URL", "Only http(s) playlist URLs are supported."); return;
      }
    } catch { Alert.alert("Invalid URL", "Please enter a valid URL."); return; }
    setBusy(true);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20_000);
    try {
      const res = await fetch(trimmed, { signal: controller.signal });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      if (text.length > 20 * 1024 * 1024) throw new Error("Playlist too large (20 MB max)");
      const parsed = parseM3U(text);
      if (!parsed.length) throw new Error("No channels found");
      await saveChannels(parsed);
      await AsyncStorage.setItem(LS_URL, trimmed);
      Alert.alert("Playlist loaded", `${parsed.length} channels imported.`);
    } catch (e: any) {
      Alert.alert("Failed to load", e?.message ?? "Could not fetch playlist.");
    } finally {
      clearTimeout(timer);
      setBusy(false);
    }
  };

  const loadFromFile = async () => {
    const MAX_BYTES = 20 * 1024 * 1024;
    try {
      const res = await DocumentPicker.getDocumentAsync({
        type: ["audio/x-mpegurl", "application/vnd.apple.mpegurl", "*/*"],
        copyToCacheDirectory: true,
      });
      if (res.canceled || !res.assets?.[0]) return;
      const file = res.assets[0];
      if (typeof file.size === "number" && file.size > MAX_BYTES) throw new Error("File too large (20 MB max)");
      const fetched = await fetch(file.uri);
      if (!fetched.ok) throw new Error(`Could not read file (HTTP ${fetched.status})`);
      const text = await fetched.text();
      if (text.length > MAX_BYTES) throw new Error("File too large (20 MB max)");
      const parsed = parseM3U(text);
      if (!parsed.length) throw new Error("No channels found");
      await saveChannels(parsed);
      Alert.alert("Playlist loaded", `${parsed.length} channels imported.`);
    } catch (e) {
      Alert.alert("Failed to load", e instanceof Error ? e.message : "Could not parse file.");
    }
  };

  const loadXtream = async () => {
    const host = xtream.host.trim().replace(/\/+$/, "");
    const username = xtream.username.trim();
    const password = xtream.password.trim();
    if (!host || !username || !password) {
      Alert.alert("Missing fields", "Host, username, and password are required.");
      return;
    }
    try {
      const u = new URL(host);
      if (!/^https?:$/.test(u.protocol)) throw new Error("Use http or https");
    } catch { Alert.alert("Invalid host", "Use a valid http(s) URL."); return; }
    setBusy(true);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 25_000);
    try {
      const m3uUrl = `${host}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=m3u_plus&output=ts`;
      const res = await fetch(m3uUrl, { signal: controller.signal });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      const parsed = parseM3U(text);
      if (!parsed.length) throw new Error("No channels returned");
      await saveChannels(parsed);
      providerRegistry.register(
        createXtreamProvider("xtream-default", "Xtream", { host, username, password }),
      );
      await AsyncStorage.setItem(LS_XTREAM, JSON.stringify({ host, username, password }));
      Alert.alert("Xtream connected", `${parsed.length} channels imported.`);
    } catch (e: any) {
      Alert.alert("Xtream login failed", e?.message ?? "Could not connect.");
    } finally {
      clearTimeout(timer);
      setBusy(false);
    }
  };

  const resetToDemo = async () => {
    try {
      await saveChannels(SAMPLE_CHANNELS);
      Alert.alert("Reset", "Demo channels restored.");
    } catch { Alert.alert("Reset failed", "Could not write to storage."); }
  };

  const exportData = async () => {
    try {
      const keys = await AsyncStorage.getAllKeys();
      const lumen = keys.filter((k) => k.startsWith("lumen."));
      const pairs = await AsyncStorage.multiGet(lumen);
      const obj: Record<string, unknown> = {};
      for (const [k, v] of pairs) {
        if (v == null) continue;
        try { obj[k] = JSON.parse(v); } catch { obj[k] = v; }
      }
      const payload = JSON.stringify({ v: 1, exportedAt: Date.now(), data: obj }, null, 2);
      await Share.share({ title: "CalmSource backup", message: payload });
    } catch (e) {
      Alert.alert("Export failed", e instanceof Error ? e.message : "Unknown error");
    }
  };

  const importData = async () => {
    try {
      const res = await DocumentPicker.getDocumentAsync({
        type: ["application/json", "*/*"],
        copyToCacheDirectory: true,
      });
      if (res.canceled || !res.assets?.[0]) return;
      const fetched = await fetch(res.assets[0].uri);
      const text = await fetched.text();
      const parsed = JSON.parse(text);
      const data = parsed?.data ?? parsed;
      if (!data || typeof data !== "object") throw new Error("Unrecognised backup format");
      const entries = Object.entries(data).filter(([k]) => k.startsWith("lumen."));
      if (!entries.length) throw new Error("No CalmSource keys found in file");
      await AsyncStorage.multiSet(entries.map(([k, v]) => [k, typeof v === "string" ? v : JSON.stringify(v)]));
      Alert.alert("Import complete", `Restored ${entries.length} keys. Restart the app to apply.`);
    } catch (e) {
      Alert.alert("Import failed", e instanceof Error ? e.message : "Could not read file");
    }
  };

  const togglePref = <K extends keyof Preferences>(k: K) => (v: boolean) => {
    setPreference(k, v as Preferences[K]);
  };

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ padding: 24, paddingBottom: 60, gap: 24 }}
    >
      <View style={{ gap: 10 }}>
        <EyebrowPill label="SETTINGS" />
        <Text style={styles.title}>Preferences</Text>
        <Text style={styles.subtitle}>Playlist, accessibility, and profile.</Text>
      </View>

      {/* IPTV M3U */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>IPTV PLAYLIST</Text>
        <Text style={styles.cardMeta}>{count} channels loaded</Text>
        <TextInput
          value={url} onChangeText={setUrl}
          placeholder="https://example.com/playlist.m3u"
          placeholderTextColor={colors.textDim}
          style={styles.input} autoCapitalize="none" keyboardType="url"
        />
        <TVPressable style={[styles.primaryBtn, busy && { opacity: 0.6 }]} onPress={loadFromUrl} disabled={busy}>
          <Text style={styles.primaryBtnText}>{busy ? "Loading…" : "Load from URL"}</Text>
        </TVPressable>
        <TVPressable style={styles.secondaryBtn} onPress={loadFromFile}>
          <Text style={styles.secondaryBtnText}>Upload .m3u file</Text>
        </TVPressable>
        <TVPressable style={styles.ghostBtn} onPress={resetToDemo}>
          <Text style={styles.ghostBtnText}>Reset to demo channels</Text>
        </TVPressable>
      </View>

      {/* Xtream Codes */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>XTREAM CODES</Text>
        <Text style={styles.cardMeta}>Sign in with provider credentials.</Text>
        <TextInput
          value={xtream.host} onChangeText={(host) => setXtream((p) => ({ ...p, host }))}
          placeholder="https://your-portal.tv" placeholderTextColor={colors.textDim}
          style={styles.input} autoCapitalize="none" keyboardType="url"
        />
        <TextInput
          value={xtream.username} onChangeText={(username) => setXtream((p) => ({ ...p, username }))}
          placeholder="Username" placeholderTextColor={colors.textDim}
          style={styles.input} autoCapitalize="none"
        />
        <TextInput
          value={xtream.password} onChangeText={(password) => setXtream((p) => ({ ...p, password }))}
          placeholder="Password" placeholderTextColor={colors.textDim}
          style={styles.input} secureTextEntry
        />
        <TVPressable style={[styles.primaryBtn, busy && { opacity: 0.6 }]} onPress={loadXtream} disabled={busy}>
          <Text style={styles.primaryBtnText}>{busy ? "Connecting…" : "Connect"}</Text>
        </TVPressable>
      </View>

      {/* Accessibility */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>ACCESSIBILITY</Text>
        <PrefRow label="Reduce motion" value={prefs.reduceMotion} onChange={togglePref("reduceMotion")} />
        <PrefRow label="Data saver" value={prefs.dataSaver} onChange={togglePref("dataSaver")} />
        <PrefRow label="Dyslexia-friendly font" value={prefs.dyslexiaFont} onChange={togglePref("dyslexiaFont")} />
        <PrefRow label="Autoplay next episode" value={prefs.autoplayNext} onChange={togglePref("autoplayNext")} />
        <PrefRow label="Mature lock" value={prefs.matureLock} onChange={togglePref("matureLock")} />
      </View>

      {/* Theme */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>THEME</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
          {(["midnight", "oled", "graphite", "light", "high-contrast"] as const).map((t) => {
            const on = prefs.theme === t;
            return (
              <TVPressable key={t} onPress={() => setPreference("theme", t)} style={[styles.themeChip, on && styles.themeChipActive]}>
                <Text style={[styles.themeChipText, on && { color: "#fff" }]}>{t}</Text>
              </TVPressable>
            );
          })}
        </View>
      </View>

      {/* Catalog add-ons */}
      <ExtensionsCard />

      {/* Data */}
      <View style={styles.card}>
        <Text style={styles.sectionLabel}>DATA</Text>
        <TVPressable style={styles.secondaryBtn} onPress={exportData}>
          <Text style={styles.secondaryBtnText}>Export backup…</Text>
        </TVPressable>
        <TVPressable style={styles.secondaryBtn} onPress={importData}>
          <Text style={styles.secondaryBtnText}>Import backup…</Text>
        </TVPressable>
      </View>

      <View style={styles.card}>
        <Text style={styles.sectionLabel}>PROFILE</Text>
        <TVPressable style={styles.secondaryBtn} onPress={onSwitchProfile}>
          <Text style={styles.secondaryBtnText}>Switch profile</Text>
        </TVPressable>
      </View>
    </ScrollView>
  );
}

function PrefRow({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <View style={styles.prefRow}>
      <Text style={styles.prefLabel}>{label}</Text>
      <Switch value={value} onValueChange={onChange} trackColor={{ true: colors.accent, false: "#444" }} thumbColor="#fff" />
    </View>
  );
}

const styles = StyleSheet.create({
  title: { color: "#fff", fontSize: 34, fontWeight: "700", letterSpacing: -0.6, marginTop: 6 },
  subtitle: { color: colors.textMuted, fontSize: 14, lineHeight: 20 },
  card: {
    padding: 20, borderRadius: 16, backgroundColor: colors.card,
    borderWidth: 1, borderColor: colors.border, gap: 12,
  },
  sectionLabel: { color: colors.textDim, fontSize: 11, fontWeight: "700", letterSpacing: 1.5 },
  cardMeta: { color: colors.textMuted, fontSize: 13, marginTop: -4 },
  input: {
    paddingHorizontal: 14, paddingVertical: 12, borderRadius: 10,
    backgroundColor: "rgba(0,0,0,0.3)", borderWidth: 1, borderColor: colors.border,
    color: "#fff", fontSize: 14, marginTop: 6,
  },
  primaryBtn: { paddingVertical: 13, borderRadius: 10, backgroundColor: colors.accent, alignItems: "center" },
  primaryBtnText: { color: colors.accentForeground, fontWeight: "700", fontSize: 14 },
  secondaryBtn: {
    paddingVertical: 13, borderRadius: 10,
    backgroundColor: "rgba(255,255,255,0.08)", borderWidth: 1, borderColor: colors.border, alignItems: "center",
  },
  secondaryBtnText: { color: "#fff", fontWeight: "600", fontSize: 14 },
  ghostBtn: { paddingVertical: 10, alignItems: "center" },
  ghostBtnText: { color: colors.textDim, fontSize: 13 },
  prefRow: {
    flexDirection: "row", alignItems: "center", justifyContent: "space-between",
    paddingVertical: 8,
  },
  prefLabel: { color: "#fff", fontSize: 14, fontWeight: "500" },
  themeChip: {
    paddingHorizontal: 12, paddingVertical: 8, borderRadius: 999,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.04)",
  },
  themeChipActive: { borderColor: colors.accent, backgroundColor: "rgba(46,91,255,0.18)" },
  themeChipText: { color: colors.textMuted, fontSize: 12, fontWeight: "600", textTransform: "capitalize" },
});

import { extensionRepository } from "../lib/extensions/repository";
import { EXTENSION_PRESETS, type InstalledExtension } from "../lib/extensions/types";

function ExtensionsCard() {
  const [items, setItems] = useState<InstalledExtension[]>(() => extensionRepository.getAll());
  const [url, setUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [refreshing, setRefreshing] = useState<string | null>(null);

  useEffect(() => {
    extensionRepository.whenReady().then(() => setItems(extensionRepository.getAll()));
    return extensionRepository.subscribe(() => setItems(extensionRepository.getAll()));
  }, []);

  const install = async (target: string) => {
    const v = target.trim();
    if (!v) { Alert.alert("Missing URL", "Paste a manifest URL or pick a preset."); return; }
    setBusy(true);
    try {
      const ext = await extensionRepository.install(v);
      Alert.alert("Installed", `${ext.manifest.name} v${ext.manifest.version}`);
      setUrl("");
    } catch (e) {
      Alert.alert("Install failed", e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const refresh = async (ext: InstalledExtension) => {
    setRefreshing(ext.baseUrl);
    try {
      await extensionRepository.refresh(ext.baseUrl);
    } catch (e) {
      Alert.alert("Health check failed", e instanceof Error ? e.message : String(e));
    } finally {
      setRefreshing(null);
    }
  };

  const remove = (ext: InstalledExtension) => {
    Alert.alert("Remove extension?", ext.manifest.name, [
      { text: "Cancel", style: "cancel" },
      { text: "Remove", style: "destructive", onPress: () => extensionRepository.uninstall(ext.baseUrl) },
    ]);
  };

  return (
    <View style={styles.card}>
      <Text style={styles.sectionLabel}>CATALOG ADD-ONS</Text>
      <Text style={styles.cardMeta}>
        {items.length === 0 ? "Install a catalog add-on to power movies and series." : `${items.length} installed`}
      </Text>
      <TextInput
        value={url}
        onChangeText={setUrl}
        placeholder="https://example.com/manifest.json"
        placeholderTextColor={colors.textDim}
        style={styles.input}
        autoCapitalize="none"
        keyboardType="url"
        autoCorrect={false}
      />
      <TVPressable style={[styles.primaryBtn, busy && { opacity: 0.6 }]} onPress={() => install(url)}>
        <Text style={styles.primaryBtnText}>{busy ? "Installing…" : "Install"}</Text>
      </TVPressable>

      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 4 }}>
        {EXTENSION_PRESETS.map((p) => {
          const installed = items.some((e) => e.baseUrl.includes(p.manifestUrl.split("/")[2] ?? ""));
          return (
            <TVPressable
              key={p.manifestUrl}
              onPress={() => install(p.manifestUrl)}
              style={[styles.themeChip, installed && styles.themeChipActive, { opacity: busy ? 0.5 : 1 }]}
            >
              <Text style={[styles.themeChipText, installed && { color: "#fff" }]}>
                {installed ? `✓ ${p.name}` : `+ ${p.name}`}
              </Text>
            </TVPressable>
          );
        })}
      </View>

      {items.map((ext) => (
        <View key={ext.baseUrl} style={extStyles.row}>
          <View style={{ flex: 1, paddingRight: 10 }}>
            <Text style={extStyles.name} numberOfLines={1}>{ext.manifest.name}</Text>
            <Text style={extStyles.meta} numberOfLines={1}>
              v{ext.manifest.version} · {ext.manifest.catalogs.length} catalog{ext.manifest.catalogs.length === 1 ? "" : "s"} · {ext.health}
            </Text>
            {ext.error ? <Text style={extStyles.err} numberOfLines={2}>{ext.error}</Text> : null}
          </View>
          <View style={{ flexDirection: "row", gap: 8 }}>
            <TVPressable
              onPress={() => extensionRepository.setEnabled(ext.baseUrl, !ext.enabled)}
              style={[extStyles.iconBtn, ext.enabled && { backgroundColor: "rgba(46,200,120,0.18)" }]}
            >
              <Text style={extStyles.iconTxt}>{ext.enabled ? "On" : "Off"}</Text>
            </TVPressable>
            <TVPressable onPress={() => refresh(ext)} style={extStyles.iconBtn}>
              <Text style={extStyles.iconTxt}>{refreshing === ext.baseUrl ? "…" : "Check"}</Text>
            </TVPressable>
            <TVPressable onPress={() => remove(ext)} style={extStyles.iconBtn}>
              <Text style={[extStyles.iconTxt, { color: "#ff8b8b" }]}>Remove</Text>
            </TVPressable>
          </View>
        </View>
      ))}
    </View>
  );
}

const extStyles = StyleSheet.create({
  row: {
    flexDirection: "row", alignItems: "center",
    padding: 12, borderRadius: 12, borderWidth: 1, borderColor: colors.border,
    backgroundColor: "rgba(255,255,255,0.03)", marginTop: 8,
  },
  name: { color: "#fff", fontSize: 14, fontWeight: "700" },
  meta: { color: colors.textDim, fontSize: 11, marginTop: 2 },
  err: { color: "#ff8b8b", fontSize: 11, marginTop: 4 },
  iconBtn: {
    paddingHorizontal: 10, paddingVertical: 6, borderRadius: 999,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.05)",
  },
  iconTxt: { color: "#fff", fontSize: 11, fontWeight: "600" },
});
