// Single source of truth for the product name so a future rename is one line.
export const BRAND = "CalmSource" as const;
export const APP_VERSION = "1.0.0" as const;
export const BUILD_CHANNEL = (import.meta.env.MODE ?? "production") as string;
