// PIN entry sheet — gates locked profiles, parity with web src/components/PinDialog.tsx.
import React, { useEffect, useRef, useState } from "react";
import { Modal, View, Text, TextInput, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "./theme";

export function PinSheet({
  expected,
  onClose,
  onSuccess,
  title = "Enter PIN",
  description = "This profile is locked. Enter the 4-digit PIN to continue.",
}: {
  expected: string;
  onClose: () => void;
  onSuccess: () => void;
  title?: string;
  description?: string;
}) {
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<TextInput>(null);

  useEffect(() => {
    const t = setTimeout(() => inputRef.current?.focus(), 80);
    return () => clearTimeout(t);
  }, []);

  useEffect(() => {
    if (pin.length === 4) {
      if (pin === expected) onSuccess();
      else { setError("Incorrect PIN"); setPin(""); }
    }
  }, [pin, expected, onSuccess]);

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.card} onPress={() => { /* swallow */ }}>
          <View style={styles.headerRow}>
            <View style={styles.iconWrap}>
              <Ionicons name="lock-closed" size={18} color="#fff" />
            </View>
            <Pressable onPress={onClose} hitSlop={10} style={styles.closeBtn}>
              <Ionicons name="close" size={18} color={colors.textMuted} />
            </Pressable>
          </View>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.desc}>{description}</Text>

          <View style={styles.dotsRow}>
            {[0, 1, 2, 3].map((i) => (
              <View key={i} style={[styles.dot, pin.length > i && styles.dotOn]} />
            ))}
          </View>

          <TextInput
            ref={inputRef}
            value={pin}
            onChangeText={(t) => { setPin(t.replace(/\D/g, "").slice(0, 4)); setError(null); }}
            keyboardType="number-pad"
            secureTextEntry
            maxLength={4}
            autoComplete="off"
            style={styles.hidden}
          />
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Pressable onPress={() => inputRef.current?.focus()} style={styles.tapHint}>
            <Text style={styles.tapHintText}>Tap to enter PIN</Text>
          </Pressable>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.75)", alignItems: "center", justifyContent: "center", padding: 20 },
  card: {
    width: "100%", maxWidth: 380, padding: 28, borderRadius: 24,
    backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, gap: 6,
  },
  headerRow: { flexDirection: "row", justifyContent: "space-between" },
  iconWrap: {
    width: 42, height: 42, borderRadius: 14, backgroundColor: "rgba(255,255,255,0.1)",
    alignItems: "center", justifyContent: "center",
  },
  closeBtn: { padding: 6 },
  title: { color: "#fff", fontSize: 22, fontWeight: "700", marginTop: 12, letterSpacing: -0.4 },
  desc: { color: colors.textMuted, fontSize: 13, lineHeight: 18, marginTop: 4 },

  dotsRow: { flexDirection: "row", gap: 16, alignSelf: "center", marginTop: 24 },
  dot: { width: 18, height: 18, borderRadius: 9, borderWidth: 1.5, borderColor: colors.border, backgroundColor: "rgba(255,255,255,0.03)" },
  dotOn: { backgroundColor: colors.accent, borderColor: colors.accent },

  hidden: { position: "absolute", opacity: 0, height: 1, width: 1 },
  error: { color: "#fda4af", textAlign: "center", marginTop: 10, fontSize: 12.5 },
  tapHint: { marginTop: 18, paddingVertical: 12, alignItems: "center", borderRadius: 12, backgroundColor: "rgba(255,255,255,0.06)" },
  tapHintText: { color: "#fff", fontSize: 13, fontWeight: "600" },
});
