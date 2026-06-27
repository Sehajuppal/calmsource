import { Platform, useWindowDimensions } from "react-native";

// Platform.isTV is true on Android TV / Fire TV / Apple TV builds.
// We also infer a "TV-like" layout when the window is very wide (e.g. landscape on a tablet
// or a sideloaded build running on a TV-class display) so the 10-foot UI kicks in.
export function useIsTV(): boolean {
  const { width } = useWindowDimensions();
  // @ts-ignore — isTV is on Platform at runtime even when not in older type defs.
  const isTVRuntime: boolean = !!Platform.isTV;
  return isTVRuntime || width >= 1100;
}

// Tile dimensions tuned for couch viewing at ~10ft.
export const TV_TILE_W = 220;
export const TV_TILE_H = 320;
export const TV_LANDSCAPE_W = 380;
export const TV_LANDSCAPE_H = 215;
export const TV_CHANNEL_W = 200;
export const TV_CHANNEL_H = 150;

// Focus ring used on Android TV / Fire TV remotes.
export const focusRing = {
  borderColor: "#ffffff",
  borderWidth: 3,
  shadowColor: "#ffffff",
  shadowOpacity: 0.35,
  shadowRadius: 16,
  shadowOffset: { width: 0, height: 0 },
  elevation: 12,
} as const;
