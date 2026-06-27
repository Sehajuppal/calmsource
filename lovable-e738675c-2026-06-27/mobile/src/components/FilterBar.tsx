// Genre + sort filter bar for mobile/TV Movies/Series tabs.
// Parity with web src/components/FilterBar.tsx.
import React, { memo } from "react";
import { View, Text, Pressable, ScrollView, StyleSheet } from "react-native";
import { colors } from "./theme";
import { focusRing } from "../lib/tv";

export type SortMode = "trending" | "az" | "newest" | "rating";

const SORTS: { id: SortMode; label: string }[] = [
  { id: "trending", label: "Trending" },
  { id: "newest", label: "Newest" },
  { id: "az", label: "A–Z" },
  { id: "rating", label: "Top rated" },
];

export const FilterBar = memo(function FilterBar({
  genres,
  activeGenre,
  setActiveGenre,
  sort,
  setSort,
  isTV,
}: {
  genres: string[];
  activeGenre: string | null;
  setActiveGenre: (g: string | null) => void;
  sort: SortMode;
  setSort: (s: SortMode) => void;
  isTV?: boolean;
}) {
  return (
    <View style={[styles.wrap, isTV && { paddingHorizontal: 48 }]}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
        <Chip on={!activeGenre} onPress={() => setActiveGenre(null)} label="All" isTV={isTV} />
        {genres.map((g) => (
          <Chip key={g} on={activeGenre === g} onPress={() => setActiveGenre(g)} label={g} isTV={isTV} />
        ))}
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.sortRow}>
        {SORTS.map((s) => (
          <Chip key={s.id} on={sort === s.id} onPress={() => setSort(s.id)} label={s.label} isTV={isTV} small />
        ))}
      </ScrollView>
    </View>
  );
});

const Chip = memo(function Chip({
  on, onPress, label, small, isTV,
}: { on: boolean; onPress: () => void; label: string; small?: boolean; isTV?: boolean }) {
  const [focused, setFocused] = React.useState(false);
  return (
    <Pressable
      focusable
      onPress={onPress}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      style={[
        styles.chip,
        small && styles.chipSmall,
        isTV && { paddingHorizontal: 18, paddingVertical: 10 },
        on && styles.chipOn,
        focused && focusRing,
      ]}
    >
      <Text style={[styles.chipText, on && styles.chipTextOn, isTV && { fontSize: 14 }]}>{label}</Text>
    </Pressable>
  );
});

const styles = StyleSheet.create({
  wrap: { paddingHorizontal: 16, marginTop: 14, gap: 8 },
  row: { gap: 8, paddingRight: 16 },
  sortRow: { gap: 6, paddingRight: 16 },
  chip: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999,
    borderWidth: 1, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.04)",
  },
  chipSmall: { paddingVertical: 6 },
  chipOn: { backgroundColor: colors.accent, borderColor: colors.accent },
  chipText: { color: colors.textMuted, fontSize: 12.5, fontWeight: "700", letterSpacing: 0.2 },
  chipTextOn: { color: colors.accentForeground },
});

export function applyFilter<T extends { genres: string[]; year: number; rating: string; name: string }>(
  items: T[], genre: string | null, sort: SortMode,
): T[] {
  let out = genre ? items.filter((i) => i.genres.includes(genre)) : items.slice();
  if (sort === "az") out.sort((a, b) => a.name.localeCompare(b.name));
  else if (sort === "newest") out.sort((a, b) => b.year - a.year);
  else if (sort === "rating") out.sort((a, b) => a.rating.localeCompare(b.rating));
  return out;
}
