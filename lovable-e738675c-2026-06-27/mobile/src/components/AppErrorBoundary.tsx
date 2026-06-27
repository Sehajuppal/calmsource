import React from "react";
import { View, Text, StyleSheet, Pressable, ScrollView } from "react-native";

type Props = { children: React.ReactNode };
type State = { error: Error | null };

/**
 * Top-level React Native error boundary. Catches render-time throws
 * anywhere in the tree and shows a recoverable fallback instead of
 * white-screening the Android app.
 *
 * Does NOT catch errors in async callbacks (timers, promises, event
 * handlers); those land on the global handler installed in App.tsx.
 */
export class AppErrorBoundary extends React.Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("[AppErrorBoundary]", error, info.componentStack);
  }

  reset = () => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    return (
      <ScrollView
        style={styles.root}
        contentContainerStyle={styles.container}
      >
        <Text style={styles.title}>Something went wrong</Text>
        <Text style={styles.body}>
          The app hit an unexpected error and couldn&apos;t continue.
          You can try again — your saved profiles and playlists are unaffected.
        </Text>
        <View style={styles.errorBox}>
          <Text style={styles.errorText} numberOfLines={6}>
            {error.message || String(error)}
          </Text>
        </View>
        <Pressable style={styles.button} onPress={this.reset}>
          <Text style={styles.buttonText}>Try again</Text>
        </Pressable>
      </ScrollView>
    );
  }
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: "#05060A" },
  container: { padding: 28, gap: 16, paddingTop: 80 },
  title: { color: "#fff", fontSize: 22, fontWeight: "700", letterSpacing: -0.4 },
  body: { color: "rgba(255,255,255,0.72)", fontSize: 14, lineHeight: 20 },
  errorBox: {
    padding: 14,
    borderRadius: 12,
    backgroundColor: "rgba(239,68,68,0.08)",
    borderWidth: 1,
    borderColor: "rgba(239,68,68,0.3)",
  },
  errorText: { color: "#fecaca", fontSize: 12, fontFamily: "monospace" },
  button: {
    marginTop: 8,
    paddingVertical: 14,
    borderRadius: 12,
    backgroundColor: "#fff",
    alignItems: "center",
  },
  buttonText: { color: "#000", fontWeight: "700", fontSize: 14 },
});
