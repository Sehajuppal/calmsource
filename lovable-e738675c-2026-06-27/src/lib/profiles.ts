import wolfAsset from "../assets/profile-avatars/wolf.asset.json";
import sealAsset from "../assets/profile-avatars/seal.asset.json";
import alpacaAsset from "../assets/profile-avatars/alpaca.asset.json";
import raccoonAsset from "../assets/profile-avatars/raccoon.asset.json";
import slothAsset from "../assets/profile-avatars/sloth.asset.json";
import flamingoAsset from "../assets/profile-avatars/flamingo.asset.json";
import duckAsset from "../assets/profile-avatars/duck.asset.json";
import parrotAsset from "../assets/profile-avatars/parrot.asset.json";
import gorillaAsset from "../assets/profile-avatars/gorilla.asset.json";
import bunnyAsset from "../assets/profile-avatars/bunny.asset.json";
import hedgehogAsset from "../assets/profile-avatars/hedgehog.asset.json";
import redPandaAsset from "../assets/profile-avatars/red-panda.asset.json";
import snackBanditAsset from "../assets/profile-avatars/snack-bandit.asset.json";
import velvetCatAsset from "../assets/profile-avatars/velvet-cat.asset.json";
import glamCatAsset from "../assets/profile-avatars/glam-cat.asset.json";
import pamperedPupAsset from "../assets/profile-avatars/pampered-pup.asset.json";
import coolGoatAsset from "../assets/profile-avatars/cool-goat.asset.json";
import nightOwlAsset from "../assets/profile-avatars/night-owl.asset.json";

export type Profile = {
  id: string;
  name: string;
  color: string; // gradient pair "from to"
  avatarId?: string;
  kids?: boolean;
  /** Optional 4-digit PIN. When set, ProfileGate prompts before unlock. */
  pin?: string;
};

export const PROFILE_AVATARS = [
  { id: "wolf", name: "Wolf", url: wolfAsset.url },
  { id: "seal", name: "Seal", url: sealAsset.url },
  { id: "alpaca", name: "Alpaca", url: alpacaAsset.url },
  { id: "raccoon", name: "Raccoon", url: raccoonAsset.url },
  { id: "sloth", name: "Sloth", url: slothAsset.url },
  { id: "flamingo", name: "Flamingo", url: flamingoAsset.url },
  { id: "duck", name: "Duck", url: duckAsset.url },
  { id: "parrot", name: "Parrot", url: parrotAsset.url },
  { id: "gorilla", name: "Gorilla", url: gorillaAsset.url },
  { id: "bunny", name: "Bunny", url: bunnyAsset.url },
  { id: "hedgehog", name: "Hedgehog", url: hedgehogAsset.url },
  { id: "red-panda", name: "Red Panda", url: redPandaAsset.url },
  { id: "snack-bandit", name: "Snack Bandit", url: snackBanditAsset.url },
  { id: "velvet-cat", name: "Velvet Cat", url: velvetCatAsset.url },
  { id: "glam-cat", name: "Glam Cat", url: glamCatAsset.url },
  { id: "pampered-pup", name: "Pampered Pup", url: pamperedPupAsset.url },
  { id: "cool-goat", name: "Cool Goat", url: coolGoatAsset.url },
  { id: "night-owl", name: "Night Owl", url: nightOwlAsset.url },
] as const;

export const DEFAULT_PROFILES: Profile[] = [
  { id: "p1", name: "Alex", color: "from-indigo-400 to-fuchsia-500", avatarId: "wolf" },
  { id: "p2", name: "Mia", color: "from-amber-300 to-rose-500", avatarId: "seal" },
  { id: "p3", name: "Theo", color: "from-emerald-300 to-cyan-500", avatarId: "gorilla" },
  { id: "p4", name: "Kids", color: "from-sky-300 to-violet-500", avatarId: "bunny", kids: true },
];

export const LS_PROFILES = "lumen.profiles.v2";
export const LS_ACTIVE_PROFILE = "lumen.profile.active.v2";
// Legacy keys, read once for migration then removed.
const LEGACY_KEYS = ["lumen.profiles.v1"] as const;
const LEGACY_ACTIVE_KEYS = ["lumen.profile.active.v1"] as const;

export function getProfileAvatar(avatarId?: string) {
  return PROFILE_AVATARS.find((avatar) => avatar.id === avatarId) ?? null;
}

export function getProfileAvatarUrl(profile: Pick<Profile, "avatarId">) {
  return getProfileAvatar(profile.avatarId)?.url ?? null;
}

/** Coerce any legacy profile shape into the current schema. Returns null if unsalvageable. */
export function migrateProfile(value: unknown): Profile | null {
  if (!value || typeof value !== "object") return null;
  const v = value as Record<string, unknown>;
  if (typeof v.id !== "string" || typeof v.name !== "string") return null;
  const color = typeof v.color === "string"
    ? v.color
    : Array.isArray(v.colors) && v.colors.length === 2 && v.colors.every((c) => typeof c === "string")
      ? "from-indigo-400 to-fuchsia-500" // fall back to a default gradient when migrating from mobile shape
      : "from-indigo-400 to-fuchsia-500";
  const kids = typeof v.kids === "boolean"
    ? v.kids
    : typeof v.kid === "boolean"
      ? v.kid
      : undefined;
  const avatarId = typeof v.avatarId === "string" ? v.avatarId : undefined;
  const pin = typeof v.pin === "string" && /^\d{4}$/.test(v.pin) ? v.pin : undefined;
  return { id: v.id, name: v.name, color, avatarId, ...(kids !== undefined ? { kids } : {}), ...(pin ? { pin } : {}) };
}

export function isProfile(value: unknown): value is Profile {
  return migrateProfile(value) !== null;
}

/** Read profiles from localStorage, migrating from any legacy key. SSR-safe (returns null). */
export function loadProfilesFromStorage(): { profiles: Profile[]; activeId: string | null } | null {
  if (typeof window === "undefined") return null;
  try {
    let raw = window.localStorage.getItem(LS_PROFILES);
    let migrated = false;
    if (!raw) {
      for (const key of LEGACY_KEYS) {
        const legacy = window.localStorage.getItem(key);
        if (legacy) { raw = legacy; migrated = true; break; }
      }
    }
    let profiles: Profile[] | null = null;
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        const safe = parsed.map(migrateProfile).filter((p): p is Profile => p !== null);
        if (safe.length) profiles = safe;
      }
    }

    let activeId = window.localStorage.getItem(LS_ACTIVE_PROFILE);
    if (!activeId) {
      for (const key of LEGACY_ACTIVE_KEYS) {
        const legacy = window.localStorage.getItem(key);
        if (legacy) { activeId = legacy; migrated = true; break; }
      }
    }

    if (migrated && profiles) {
      window.localStorage.setItem(LS_PROFILES, JSON.stringify(profiles));
      if (activeId) window.localStorage.setItem(LS_ACTIVE_PROFILE, activeId);
      for (const k of LEGACY_KEYS) window.localStorage.removeItem(k);
      for (const k of LEGACY_ACTIVE_KEYS) window.localStorage.removeItem(k);
    }

    return { profiles: profiles ?? DEFAULT_PROFILES, activeId };
  } catch {
    return { profiles: DEFAULT_PROFILES, activeId: null };
  }
}

