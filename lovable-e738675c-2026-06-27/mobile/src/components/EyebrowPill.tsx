import React from "react";
import { View, Text, StyleSheet } from "react-native";

export function EyebrowPill({ label }: { label: string }) {
  return (
    <View style={styles.pill}>
      <View style={styles.dot} />
      <Text style={styles.text}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.12)",
    backgroundColor: "rgba(255,255,255,0.04)",
    alignSelf: "flex-start",
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: "#2E5BFF",
  },
  text: {
    color: "rgba(255,255,255,0.7)",
    fontSize: 11,
    letterSpacing: 2,
    fontWeight: "600",
  },
});
