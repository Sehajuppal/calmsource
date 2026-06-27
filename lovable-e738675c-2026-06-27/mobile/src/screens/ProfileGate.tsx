import React, { useEffect, useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  TextInput,
  ScrollView,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { LinearGradient } from "expo-linear-gradient";
import { Image } from "expo-image";
import {
  MAX_PROFILES,
  PROFILE_AVATAR_OPTIONS,
  getProfileAvatarSource,
  type Profile,
} from "../lib/profiles";
import { EyebrowPill } from "../components/EyebrowPill";
import { colors } from "../components/theme";

export default function ProfileGate({
  profiles,
  onPick,
  onPersist,
}: {
  profiles: Profile[];
  onPick: (p: Profile) => void;
  onPersist: (next: Profile[]) => void;
}) {
  const [manage, setManage] = useState(false);
  const [draftId, setDraftId] = useState<string | null>(null);
  const [draftName, setDraftName] = useState("");
  const [draftAvatarId, setDraftAvatarId] = useState<string>(PROFILE_AVATAR_OPTIONS[0]?.id ?? "");

  const renameProfile = (id: string, name: string) =>
    onPersist(profiles.map((p) => (p.id === id ? { ...p, name } : p)));

  const updateAvatar = (id: string, avatarId: string) =>
    onPersist(profiles.map((p) => (p.id === id ? { ...p, avatarId } : p)));

  const setPin = (id: string, pin?: string) =>
    onPersist(
      profiles.map((p) => (p.id === id ? { ...p, ...(pin ? { pin } : { pin: undefined }) } : p)),
    );

  const removeProfile = (id: string) => {
    if (profiles.length <= 1) return;
    onPersist(profiles.filter((p) => p.id !== id));
    if (draftId === id) {
      setDraftId(null);
      setDraftName("");
    }
  };

  const toggleKids = (id: string) =>
    onPersist(profiles.map((p) => (p.id === id ? { ...p, kids: !p.kids } : p)));

  const addProfile = () => {
    if (profiles.length >= MAX_PROFILES) return;
    const palettes: Profile["colors"][] = [
      ["#06b6d4", "#3b82f6"],
      ["#f43f5e", "#a855f7"],
      ["#84cc16", "#14b8a6"],
    ];
    const next: Profile = {
      id: `p${Date.now()}`,
      name: "New",
      colors: palettes[profiles.length % palettes.length],
      avatarId: PROFILE_AVATAR_OPTIONS[profiles.length % PROFILE_AVATAR_OPTIONS.length]?.id,
    };
    onPersist([...profiles, next]);
  };

  const activeDraftProfile = useMemo(
    () => profiles.find((profile) => profile.id === draftId) ?? null,
    [profiles, draftId],
  );

  const commitDraft = () => {
    if (!draftId) return;
    if (draftName.trim()) renameProfile(draftId, draftName.trim());
    if (draftAvatarId) updateAvatar(draftId, draftAvatarId);
    setDraftId(null);
    setDraftName("");
  };

  return (
    <SafeAreaView style={{ flex: 1 }} edges={["top", "bottom"]}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
      >
        <EyebrowPill label="LUMEN" />
        <Text style={styles.title}>Who's watching?</Text>
        <Text style={styles.subtitle}>
          {manage
            ? "Choose a portrait and rename each profile."
            : "Choose a profile to continue. Each one keeps its own taste, watchlist, and history."}
        </Text>

        <View style={styles.grid}>
          {profiles.map((p) => (
            <ProfileTile
              key={p.id}
              profile={p}
              manage={manage}
              isDraft={draftId === p.id}
              draftName={draftName}
              onPick={() => {
                if (manage) {
                  setDraftId(p.id);
                  setDraftName(p.name);
                  setDraftAvatarId(p.avatarId ?? PROFILE_AVATAR_OPTIONS[0]?.id ?? "");
                  return;
                }
                onPick(p);
              }}
              onDraftName={setDraftName}
              onCommit={commitDraft}
            />
          ))}
          {profiles.length < MAX_PROFILES && !manage && (
            <Pressable style={styles.addTile} onPress={addProfile}>
              <Text style={styles.addPlus}>+</Text>
              <Text style={styles.addLabel}>Add Profile</Text>
            </Pressable>
          )}
        </View>

        {manage && activeDraftProfile ? (
          <View style={styles.avatarPanel}>
            <Text style={styles.avatarEyebrow}>Portrait</Text>
            <Text style={styles.avatarHeading}>{draftName.trim() || activeDraftProfile.name}</Text>
            <View style={styles.avatarGrid}>
              {PROFILE_AVATAR_OPTIONS.map((avatar) => {
                const active = draftAvatarId === avatar.id;
                return (
                  <Pressable
                    key={avatar.id}
                    onPress={() => setDraftAvatarId(avatar.id)}
                    style={[styles.avatarOption, active && styles.avatarOptionActive]}
                  >
                    <Image source={avatar.source} style={styles.avatarOptionImage} contentFit="cover" />
                    <Text style={[styles.avatarOptionLabel, active && styles.avatarOptionLabelActive]}>
                      {avatar.name}
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            <View style={styles.pinRow}>
              <TextInput
                placeholder={activeDraftProfile.pin ? "Change PIN (4 digits)" : "Set PIN (4 digits)"}
                placeholderTextColor="rgba(255,255,255,0.4)"
                keyboardType="number-pad"
                maxLength={4}
                secureTextEntry
                style={styles.pinInput}
                onSubmitEditing={(e) => {
                  const v = e.nativeEvent.text.trim();
                  if (/^\d{4}$/.test(v)) setPin(activeDraftProfile.id, v);
                }}
              />
              {activeDraftProfile.pin ? (
                <Pressable style={styles.smallBtn} onPress={() => setPin(activeDraftProfile.id, undefined)}>
                  <Text style={styles.smallBtnText}>Remove PIN</Text>
                </Pressable>
              ) : null}
              <Pressable
                style={[styles.smallBtn, activeDraftProfile.kids && { borderColor: colors.accent }]}
                onPress={() => toggleKids(activeDraftProfile.id)}
              >
                <Text style={[styles.smallBtnText, activeDraftProfile.kids && { color: colors.accent }]}>
                  {activeDraftProfile.kids ? "Kids · On" : "Kids profile"}
                </Text>
              </Pressable>
            </View>

            <View style={{ flexDirection: "row", gap: 10 }}>
              <Pressable style={[styles.saveBtn, { flex: 1 }]} onPress={commitDraft}>
                <Text style={styles.saveText}>Save</Text>
              </Pressable>
              {profiles.length > 1 ? (
                <Pressable
                  style={[styles.saveBtn, { backgroundColor: "rgba(239,68,68,0.85)" }]}
                  onPress={() => removeProfile(activeDraftProfile.id)}
                >
                  <Text style={styles.saveText}>Delete</Text>
                </Pressable>
              ) : null}
            </View>
          </View>
        ) : null}

        <Pressable style={styles.manageBtn} onPress={() => {
          setManage((v) => !v);
          setDraftId(null);
          setDraftName("");
        }}>
          <Text style={styles.manageText}>
            {manage ? "Done" : "Manage Profiles"}
          </Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

function ProfileTile({
  profile,
  manage,
  isDraft,
  draftName,
  onPick,
  onDraftName,
  onCommit,
}: {
  profile: Profile;
  manage: boolean;
  isDraft: boolean;
  draftName: string;
  onPick: () => void;
  onDraftName: (name: string) => void;
  onCommit: () => void;
}) {
  const [name, setName] = useState(profile.name);
  useEffect(() => setName(profile.name), [profile.name]);
  const avatarSource = getProfileAvatarSource(profile.avatarId);

  return (
    <View style={styles.tileWrap}>
      <Pressable
        onPress={onPick}
        style={({ pressed }) => [
          styles.avatar,
          pressed && !manage && styles.avatarPressed,
        ]}
      >
        {avatarSource ? (
          <Image source={avatarSource} style={StyleSheet.absoluteFill} contentFit="cover" />
        ) : (
          <>
            <LinearGradient
              colors={profile.colors}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={StyleSheet.absoluteFill}
            />
            <Text style={styles.avatarInitial}>
              {profile.name.charAt(0).toUpperCase()}
            </Text>
          </>
        )}
        {profile.kids && (
          <View style={styles.kidsBadge}>
            <Text style={styles.kidsText}>KIDS</Text>
          </View>
        )}
        {manage ? <View style={styles.editOverlay} /> : null}
      </Pressable>
      {isDraft ? (
        <TextInput
          autoFocus
          value={draftName}
          onChangeText={(value) => {
            setName(value);
            onDraftName(value);
          }}
          onSubmitEditing={onCommit}
          onEndEditing={onCommit}
          style={styles.nameInput}
          placeholderTextColor="#64748b"
          maxLength={16}
        />
      ) : (
        <Text style={styles.nameLabel} numberOfLines={1}>
          {profile.name}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  scroll: {
    paddingHorizontal: 24,
    paddingTop: 48,
    paddingBottom: 64,
    alignItems: "center",
    gap: 8,
  },
  title: {
    color: "#fff",
    fontSize: 40,
    fontWeight: "700",
    letterSpacing: -0.8,
    textAlign: "center",
    marginTop: 24,
    marginBottom: 12,
  },
  subtitle: {
    color: colors.textMuted,
    fontSize: 15,
    lineHeight: 22,
    textAlign: "center",
    maxWidth: 360,
    marginBottom: 40,
  },
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "center",
    gap: 20,
    marginBottom: 32,
  },
  tileWrap: { width: 120, alignItems: "center" },
  avatar: {
    width: 110,
    height: 110,
    borderRadius: 22,
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.08)",
    shadowColor: "#000",
    shadowOpacity: 0.5,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 12 },
    elevation: 12,
    backgroundColor: "rgba(255,255,255,0.04)",
  },
  avatarPressed: {
    transform: [{ scale: 0.97 }],
    borderColor: "rgba(255,255,255,0.35)",
  },
  editOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(0,0,0,0.24)",
  },
  avatarInitial: {
    color: "#fff",
    fontSize: 44,
    fontWeight: "700",
    letterSpacing: -1,
  },
  kidsBadge: {
    position: "absolute",
    bottom: 8,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
    backgroundColor: "rgba(0,0,0,0.55)",
  },
  kidsText: {
    color: "#fff",
    fontSize: 9,
    fontWeight: "700",
    letterSpacing: 1.5,
  },
  nameLabel: {
    color: "rgba(255,255,255,0.85)",
    fontSize: 14,
    fontWeight: "500",
    marginTop: 14,
  },
  nameInput: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "500",
    marginTop: 12,
    textAlign: "center",
    minWidth: 100,
    paddingVertical: 4,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(255,255,255,0.2)",
  },
  addTile: {
    width: 110,
    height: 110,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: "rgba(255,255,255,0.18)",
    borderStyle: "dashed",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(255,255,255,0.02)",
  },
  addPlus: {
    color: "rgba(255,255,255,0.6)",
    fontSize: 36,
    fontWeight: "300",
    marginBottom: 4,
  },
  addLabel: { color: colors.textMuted, fontSize: 11, fontWeight: "500" },
  avatarPanel: {
    width: "100%",
    maxWidth: 420,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.1)",
    backgroundColor: "rgba(255,255,255,0.04)",
    padding: 16,
    marginBottom: 24,
  },
  avatarEyebrow: {
    color: colors.textDim,
    fontSize: 11,
    fontWeight: "700",
    letterSpacing: 1.8,
    textTransform: "uppercase",
  },
  avatarHeading: {
    color: "#fff",
    fontSize: 20,
    fontWeight: "600",
    marginTop: 6,
    marginBottom: 14,
  },
  avatarGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  avatarOption: {
    width: 70,
    alignItems: "center",
    gap: 6,
    padding: 4,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.08)",
    backgroundColor: "rgba(255,255,255,0.02)",
  },
  avatarOptionActive: {
    borderColor: "rgba(255,255,255,0.55)",
    backgroundColor: "rgba(255,255,255,0.08)",
  },
  avatarOptionImage: {
    width: 58,
    height: 58,
    borderRadius: 12,
  },
  avatarOptionLabel: {
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: "500",
  },
  avatarOptionLabelActive: {
    color: "#fff",
  },
  saveBtn: {
    marginTop: 14,
    alignSelf: "flex-end",
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.16)",
    backgroundColor: "rgba(255,255,255,0.08)",
  },
  saveText: {
    color: "#fff",
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 0.4,
  },
  pinRow: {
    marginTop: 14, flexDirection: "row", alignItems: "center",
    gap: 8, flexWrap: "wrap",
  },
  pinInput: {
    flex: 1, minWidth: 180,
    paddingHorizontal: 14, paddingVertical: 10, borderRadius: 12,
    borderWidth: 1, borderColor: "rgba(255,255,255,0.18)",
    backgroundColor: "rgba(255,255,255,0.06)",
    color: "#fff", fontSize: 13,
  },
  smallBtn: {
    paddingHorizontal: 12, paddingVertical: 8, borderRadius: 999,
    borderWidth: 1, borderColor: "rgba(255,255,255,0.18)",
    backgroundColor: "rgba(255,255,255,0.05)",
  },
  smallBtnText: { color: "rgba(255,255,255,0.85)", fontSize: 11, fontWeight: "700" },
  manageBtn: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.15)",
    backgroundColor: "rgba(255,255,255,0.05)",
  },
  manageText: {
    color: "rgba(255,255,255,0.85)",
    fontSize: 13,
    fontWeight: "600",
    letterSpacing: 0.3,
  },
});
