export type Profile = {
  id: string;
  name: string;
  /** Hex pair used by native LinearGradient. Web uses Tailwind tokens;
   *  we keep hex on mobile because RN has no Tailwind. */
  colors: [string, string];
  avatarId?: string;
  kids?: boolean;
  /** Optional 4-digit PIN — parity with web profile lock. */
  pin?: string;
};

/** Profile cap — kept in lockstep with web (src/components/ProfileGate.tsx). */
export const MAX_PROFILES = 10;

export const PROFILE_AVATARS = {
  wolf: require("../../assets/profile-avatars/wolf.png"),
  seal: require("../../assets/profile-avatars/seal.png"),
  alpaca: require("../../assets/profile-avatars/alpaca.png"),
  raccoon: require("../../assets/profile-avatars/raccoon.png"),
  sloth: require("../../assets/profile-avatars/sloth.png"),
  flamingo: require("../../assets/profile-avatars/flamingo.png"),
  duck: require("../../assets/profile-avatars/duck.png"),
  parrot: require("../../assets/profile-avatars/parrot.png"),
  gorilla: require("../../assets/profile-avatars/gorilla.png"),
  bunny: require("../../assets/profile-avatars/bunny.png"),
  hedgehog: require("../../assets/profile-avatars/hedgehog.png"),
  redPanda: require("../../assets/profile-avatars/red-panda.png"),
  snackBandit: require("../../assets/profile-avatars/snack-bandit.png"),
  velvetCat: require("../../assets/profile-avatars/velvet-cat.png"),
  glamCat: require("../../assets/profile-avatars/glam-cat.png"),
  pamperedPup: require("../../assets/profile-avatars/pampered-pup.png"),
  coolGoat: require("../../assets/profile-avatars/cool-goat.png"),
  nightOwl: require("../../assets/profile-avatars/night-owl.png"),
} as const;

export const PROFILE_AVATAR_OPTIONS = [
  { id: "wolf", name: "Wolf", source: PROFILE_AVATARS.wolf },
  { id: "seal", name: "Seal", source: PROFILE_AVATARS.seal },
  { id: "alpaca", name: "Alpaca", source: PROFILE_AVATARS.alpaca },
  { id: "raccoon", name: "Raccoon", source: PROFILE_AVATARS.raccoon },
  { id: "sloth", name: "Sloth", source: PROFILE_AVATARS.sloth },
  { id: "flamingo", name: "Flamingo", source: PROFILE_AVATARS.flamingo },
  { id: "duck", name: "Duck", source: PROFILE_AVATARS.duck },
  { id: "parrot", name: "Parrot", source: PROFILE_AVATARS.parrot },
  { id: "gorilla", name: "Gorilla", source: PROFILE_AVATARS.gorilla },
  { id: "bunny", name: "Bunny", source: PROFILE_AVATARS.bunny },
  { id: "hedgehog", name: "Hedgehog", source: PROFILE_AVATARS.hedgehog },
  { id: "red-panda", name: "Red Panda", source: PROFILE_AVATARS.redPanda },
  { id: "snack-bandit", name: "Snack Bandit", source: PROFILE_AVATARS.snackBandit },
  { id: "velvet-cat", name: "Velvet Cat", source: PROFILE_AVATARS.velvetCat },
  { id: "glam-cat", name: "Glam Cat", source: PROFILE_AVATARS.glamCat },
  { id: "pampered-pup", name: "Pampered Pup", source: PROFILE_AVATARS.pamperedPup },
  { id: "cool-goat", name: "Cool Goat", source: PROFILE_AVATARS.coolGoat },
  { id: "night-owl", name: "Night Owl", source: PROFILE_AVATARS.nightOwl },
] as const;

export const DEFAULT_PROFILES: Profile[] = [
  { id: "p1", name: "Alex", colors: ["#3b82f6", "#1e40af"], avatarId: "wolf" },
  { id: "p2", name: "Mia", colors: ["#ec4899", "#8b5cf6"], avatarId: "seal" },
  { id: "p3", name: "Theo", colors: ["#22c55e", "#0ea5e9"], avatarId: "gorilla" },
  { id: "p4", name: "Kids", colors: ["#f59e0b", "#ef4444"], avatarId: "bunny", kids: true },
];

// Active-profile key now matches web (`lumen.profile.active.v2`). The mobile
// build previously used `lumen.activeProfile.v2`; both are listed as legacy
// for the one-shot migration.
export const STORAGE_KEYS = {
  profiles: "lumen.profiles.v2",
  active: "lumen.profile.active.v2",
};

export const LEGACY_STORAGE_KEYS = {
  profiles: ["lumen.profiles"],
  active: ["lumen.activeProfile.v2", "lumen.activeProfile"],
};

export function getProfileAvatarSource(avatarId?: string) {
  return PROFILE_AVATAR_OPTIONS.find((avatar) => avatar.id === avatarId)?.source;
}

export function migrateProfile(value: unknown): Profile | null {
  if (!value || typeof value !== "object") return null;
  const v = value as Record<string, unknown>;
  if (typeof v.id !== "string" || typeof v.name !== "string") return null;
  let colors: [string, string];
  if (
    Array.isArray(v.colors) &&
    v.colors.length === 2 &&
    v.colors.every((c) => typeof c === "string")
  ) {
    colors = [v.colors[0] as string, v.colors[1] as string];
  } else {
    colors = ["#3b82f6", "#1e40af"];
  }
  const kids = typeof v.kids === "boolean"
    ? v.kids
    : typeof v.kid === "boolean"
      ? (v.kid as boolean)
      : undefined;
  const avatarId = typeof v.avatarId === "string" ? v.avatarId : undefined;
  const pin = typeof v.pin === "string" && /^\d{4}$/.test(v.pin) ? v.pin : undefined;
  return {
    id: v.id,
    name: v.name,
    colors,
    avatarId,
    ...(kids !== undefined ? { kids } : {}),
    ...(pin ? { pin } : {}),
  };
}

export function isProfile(value: unknown): value is Profile {
  return migrateProfile(value) !== null;
}
