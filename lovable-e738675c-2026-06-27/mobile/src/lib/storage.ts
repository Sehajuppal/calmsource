// Async parity layer for AsyncStorage — mirrors web src/lib/storage.ts API,
// but every read/write is async because RN has no synchronous storage.
import AsyncStorage from "@react-native-async-storage/async-storage";

const PREFIX = "lumen";

export function nsKey(profileId: string | null | undefined, key: string) {
  return `${PREFIX}.${profileId ?? "global"}.${key}.v1`;
}

export async function readJSON<T>(key: string, fallback: T): Promise<T> {
  try {
    const raw = await AsyncStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch (e) {
    console.warn("[storage] readJSON failed for", key, e);
    try { await AsyncStorage.removeItem(key); } catch { /* ignore */ }
    return fallback;
  }
}

export async function writeJSON(key: string, value: unknown): Promise<boolean> {
  try {
    await AsyncStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch (e) {
    console.warn("[storage] writeJSON failed for", key, e);
    return false;
  }
}

export async function removeKey(key: string): Promise<void> {
  try { await AsyncStorage.removeItem(key); } catch (e) {
    console.warn("[storage] removeKey failed for", key, e);
  }
}
