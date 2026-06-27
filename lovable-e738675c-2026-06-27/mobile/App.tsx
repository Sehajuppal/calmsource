import React, { useEffect, useState, useCallback } from "react";
import { View, StyleSheet, Platform, Alert } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { NavigationContainer, DefaultTheme } from "@react-navigation/native";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { GestureHandlerRootView } from "react-native-gesture-handler";

import {
  DEFAULT_PROFILES,
  LEGACY_STORAGE_KEYS,
  STORAGE_KEYS,
  migrateProfile,
  type Profile,
} from "./src/lib/profiles";
import { AppErrorBoundary } from "./src/components/AppErrorBoundary";
import ProfileGate from "./src/screens/ProfileGate";
import HomeScreen from "./src/screens/HomeScreen";
import LiveTVScreen from "./src/screens/LiveTVScreen";
import SettingsScreen from "./src/screens/SettingsScreen";
import SearchScreen from "./src/screens/SearchScreen";
import { PinSheet } from "./src/components/PinSheet";
import { PlayerRoot } from "./src/components/Player";
import { colors } from "./src/components/theme";
import { useIsTV } from "./src/lib/tv";
import { useUserData } from "./src/lib/userdata";

const Tab = createBottomTabNavigator();

const NavTheme = {
  ...DefaultTheme,
  dark: true,
  colors: {
    ...DefaultTheme.colors,
    background: colors.bg,
    card: "rgba(10,12,18,0.95)",
    text: "#fff",
    border: colors.border,
    primary: "#fff",
    notification: colors.accent,
  },
};

export default function App() {
  const [profiles, setProfiles] = useState<Profile[]>(DEFAULT_PROFILES);
  const [active, setActive] = useState<Profile | null>(null);
  const [pendingPin, setPendingPin] = useState<Profile | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // Surface unhandled JS errors so Android doesn't fail silently.
    // ErrorUtils is RN's process-wide error hook.
    const RN = require("react-native") as {
      ErrorUtils?: { setGlobalHandler: (h: (e: Error, isFatal?: boolean) => void) => void };
    };
    RN.ErrorUtils?.setGlobalHandler((e, isFatal) => {
      console.error("[global]", isFatal ? "FATAL" : "", e);
      if (isFatal) {
        Alert.alert("Unexpected error", e?.message ?? "The app hit a problem.");
      }
    });
  }, []);

  useEffect(() => {
    (async () => {
      try {
        let raw = await AsyncStorage.getItem(STORAGE_KEYS.profiles);
        let migratedFromLegacy = false;
        if (!raw) {
          for (const key of LEGACY_STORAGE_KEYS.profiles) {
            const legacy = await AsyncStorage.getItem(key);
            if (legacy) { raw = legacy; migratedFromLegacy = true; break; }
          }
        }

        let loaded: Profile[] = DEFAULT_PROFILES;
        if (raw) {
          try {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) {
              const safe = parsed
                .map(migrateProfile)
                .filter((p): p is Profile => p !== null);
              if (safe.length) {
                loaded = safe;
                setProfiles(safe);
              }
            }
          } catch (parseErr) {
            console.warn("[App] profile JSON corrupt, resetting", parseErr);
            // Self-heal: drop the corrupt blob so we don't keep failing.
            await AsyncStorage.removeItem(STORAGE_KEYS.profiles).catch(() => {});
          }
        }

        let activeId = await AsyncStorage.getItem(STORAGE_KEYS.active);
        if (!activeId) {
          for (const key of LEGACY_STORAGE_KEYS.active) {
            const legacy = await AsyncStorage.getItem(key);
            if (legacy) { activeId = legacy; migratedFromLegacy = true; break; }
          }
        }
        if (activeId) {
          const match = loaded.find((profile) => profile.id === activeId) ?? null;
          if (match) setActive(match);
        }

        if (migratedFromLegacy) {
          try {
            await AsyncStorage.setItem(STORAGE_KEYS.profiles, JSON.stringify(loaded));
            if (activeId) await AsyncStorage.setItem(STORAGE_KEYS.active, activeId);
            await Promise.all([
              ...LEGACY_STORAGE_KEYS.profiles.map((k) => AsyncStorage.removeItem(k)),
              ...LEGACY_STORAGE_KEYS.active.map((k) => AsyncStorage.removeItem(k)),
            ]);
          } catch (migrationErr) {
            console.warn("[App] legacy migration cleanup failed", migrationErr);
          }
        }
      } catch (e) {
        console.error("[App] profile load failed", e);
      }
      setReady(true);
    })();
  }, []);


  const persistProfiles = useCallback(async (next: Profile[]) => {
    setProfiles(next);
    // If the active profile was deleted in `next`, drop it so the gate
    // re-opens instead of pinning a stale (now-missing) selection.
    setActive((current) => {
      if (!current) return current;
      const updated = next.find((profile) => profile.id === current.id);
      return updated ?? null;
    });
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.profiles, JSON.stringify(next));
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Storage error";
      console.error("[App] persistProfiles failed", e);
      Alert.alert("Could not save profiles", msg);
    }
  }, []);


  const finalizePick = useCallback(async (p: Profile) => {
    setActive(p);
    setPendingPin(null);
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.active, p.id);
    } catch (e) {
      console.warn("[App] pickProfile persist failed", e);
    }
  }, []);

  const pickProfile = useCallback((p: Profile) => {
    if (p.pin && p.pin.length === 4) {
      setPendingPin(p);
      return;
    }
    void finalizePick(p);
  }, [finalizePick]);

  const switchProfile = useCallback(async () => {
    setActive(null);
    try {
      await AsyncStorage.removeItem(STORAGE_KEYS.active);
    } catch (e) {
      console.warn("[App] switchProfile clear failed", e);
    }
  }, []);

  // Hydrate the per-profile data store (watchlist, continueWatching, prefs)
  // whenever the active profile changes.
  useEffect(() => {
    if (!active) return;
    useUserData.getState().hydrate(active.id).catch((e) =>
      console.warn("[App] userdata hydrate failed", e),
    );
  }, [active]);



  if (!ready) {
    return <View style={{ flex: 1, backgroundColor: colors.bg }} />;
  }

  return (
    <AppErrorBoundary>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <SafeAreaProvider>
          <StatusBar style="light" />
          <View style={styles.root}>
          <LinearGradient
            colors={["rgba(59,130,246,0.18)", "transparent"]}
            start={{ x: 0.1, y: 0 }}
            end={{ x: 0.9, y: 1 }}
            style={StyleSheet.absoluteFill}
          />
          {active ? (
            <NavigationContainer theme={NavTheme}>
              <TVAwareTabs onSwitchProfile={switchProfile} />
            </NavigationContainer>
          ) : (
            <ProfileGate
              profiles={profiles}
              onPick={pickProfile}
              onPersist={persistProfiles}
            />
          )}
          {pendingPin && (
            <PinSheet
              expected={pendingPin.pin ?? ""}
              onClose={() => setPendingPin(null)}
              onSuccess={() => void finalizePick(pendingPin)}
              title={`Unlock ${pendingPin.name}`}
            />
          )}
          <PlayerRoot />
          </View>
        </SafeAreaProvider>
      </GestureHandlerRootView>
    </AppErrorBoundary>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
});

// Tab navigator that swaps to a top "nav rail" on TV displays so D-pad lands
// on focusable labels at the top of the screen instead of a buried bottom bar.
function TVAwareTabs({ onSwitchProfile }: { onSwitchProfile: () => void }) {
  const isTV = useIsTV();
  const Ionicons = require("@expo/vector-icons/Ionicons").default as React.ComponentType<{
    name: string;
    size?: number;
    color?: string;
  }>;
  const iconFor = (route: string, focused: boolean): string => {
    if (route === "Home") return focused ? "home" : "home-outline";
    if (route === "Search") return focused ? "search" : "search-outline";
    if (route === "Live TV") return focused ? "radio" : "radio-outline";
    return focused ? "settings" : "settings-outline";
  };
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        ...(isTV ? { tabBarPosition: "top" as const } : null),
        tabBarStyle: {
          backgroundColor: "rgba(8,10,16,0.96)",
          borderTopColor: colors.border,
          borderTopWidth: isTV ? 0 : 1,
          borderBottomColor: colors.border,
          borderBottomWidth: isTV ? 1 : 0,
          paddingTop: isTV ? 14 : 8,
          paddingBottom: isTV ? 14 : Platform.OS === "ios" ? 28 : 10,
          height: isTV ? 78 : Platform.OS === "android" ? 68 : 88,
        },
        tabBarActiveTintColor: "#fff",
        tabBarInactiveTintColor: colors.textDim,
        tabBarLabelStyle: {
          fontSize: isTV ? 18 : 11,
          fontWeight: "600",
          letterSpacing: 0.3,
          marginTop: 2,
        },
        tabBarItemStyle: isTV ? { paddingHorizontal: 28 } : { paddingVertical: 4 },
        tabBarIcon: ({ focused, color }: { focused: boolean; color: string }) => (
          <Ionicons name={iconFor(route.name, focused)} size={isTV ? 26 : 22} color={color} />
        ),
      }) as any}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Search" component={SearchScreen} />
      <Tab.Screen name="Live TV" component={LiveTVScreen} />
      <Tab.Screen name="Settings">
        {() => <SettingsScreen onSwitchProfile={onSwitchProfile} />}
      </Tab.Screen>
    </Tab.Navigator>
  );
}

