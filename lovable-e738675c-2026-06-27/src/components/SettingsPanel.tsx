import { useEffect, useRef, useState } from "react";
import { Link as LinkIcon, Trash2, Upload, X, Palette, Download, Keyboard, KeyRound, Accessibility, Puzzle, RefreshCw, Power, AlertCircle, CheckCircle2 } from "lucide-react";
import { toast } from "sonner";
import { parseM3U, SAMPLE_CHANNELS, type IPTVChannel } from "../lib/iptv";
import { useUserData, type Preferences } from "../lib/userdata";
import { providerRegistry } from "../lib/providers/registry";
import { createXtreamProvider } from "../lib/providers/xtream";
import { loadXmltvFromUrl, epgStats, clearEpg, subscribeEpg } from "../lib/epg-source";
import { extensionRepository } from "../lib/extensions/repository";
import { EXTENSION_PRESETS, type InstalledExtension } from "../lib/extensions/types";
import { BRAND, APP_VERSION, BUILD_CHANNEL } from "../lib/brand";

function AboutSection() {
  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="font-display text-lg font-semibold tracking-tight">About {BRAND}</h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        Version {APP_VERSION} · {BUILD_CHANNEL} · A cinematic streaming interface
        for films, series, and live TV.
      </p>
      <p className="mt-2 text-xs text-muted-foreground/70">
        Powered by catalog add-ons, IPTV providers, and XMLTV EPG.
      </p>
    </section>
  );
}

function concatChunks(chunks: Uint8Array[], total: number) {
  const out = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) { out.set(c, offset); offset += c.byteLength; }
  return out;
}

const LS_KEY = "lumen.iptv.channels.v1";
const LS_URL_KEY = "lumen.iptv.lastUrl.v1";

export default function SettingsPanel({
  channels,
  setChannels,
  onClose,
}: {
  channels: IPTVChannel[];
  setChannels: (c: IPTVChannel[]) => void;
  onClose: () => void;
}) {
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);


  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    try {
      const last = localStorage.getItem(LS_URL_KEY);
      if (last) setUrl(last);
    } catch {/* ignore */}
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  const apply = (text: string, sourceUrl?: string) => {
    let parsed: IPTVChannel[] = [];
    try {
      parsed = parseM3U(text);
    } catch (e) {
      console.error("[Settings] parse failed", e);
      setError("Could not parse playlist file.");
      toast.error("Playlist parsing failed");
      return;
    }
    if (!parsed.length) {
      setError("No channels found in playlist.");
      toast.error("No channels found");
      return;
    }
    setChannels(parsed);
    try {
      localStorage.setItem(LS_KEY, JSON.stringify(parsed.slice(0, 2000)));
      if (sourceUrl) {
        // Strip embedded credentials before persisting
        let toStore = sourceUrl;
        try {
          const u = new URL(sourceUrl);
          u.username = "";
          u.password = "";
          toStore = u.toString();
        } catch {/* keep original */}
        localStorage.setItem(LS_URL_KEY, toStore);
      }
    } catch (e) {
      console.warn("[Settings] storage quota exceeded", e);
      toast.warning("Could not save playlist locally (storage full)");
    }
    setNotice(`Loaded ${parsed.length} channels.`);
    toast.success(`Loaded ${parsed.length} channels`);
    setError(null);
  };

  const loadFromUrl = async () => {
    const trimmed = url.trim();
    if (!trimmed) return;
    try {
      const parsedUrl = new URL(trimmed);
      if (!/^https?:$/.test(parsedUrl.protocol)) {
        setError("Only http(s) playlist URLs are supported.");
        return;
      }
    } catch {
      setError("Please enter a valid URL.");
      return;
    }
    setLoading(true);
    setError(null);
    setNotice(null);
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 20_000);
      const MAX_BYTES = 20 * 1024 * 1024; // 20 MB
      let res: Response;
      try {
        res = await fetch(trimmed, { signal: controller.signal });
      } finally {
        clearTimeout(timer);
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const declared = Number(res.headers.get("content-length") ?? "0");
      if (declared && declared > MAX_BYTES) {
        throw new Error("Playlist too large (20 MB max)");
      }
      const reader = res.body?.getReader();
      let text: string;
      if (reader) {
        const chunks: Uint8Array[] = [];
        let received = 0;
        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          if (value) {
            received += value.byteLength;
            if (received > MAX_BYTES) {
              try { await reader.cancel(); } catch { /* noop */ }
              throw new Error("Playlist too large (20 MB max)");
            }
            chunks.push(value);
          }
        }
        text = new TextDecoder().decode(
          chunks.length === 1 ? chunks[0] : concatChunks(chunks, received),
        );
      } else {
        text = await res.text();
        if (text.length > MAX_BYTES) throw new Error("Playlist too large (20 MB max)");
      }
      if (!mounted.current) return;
      apply(text, trimmed);
    } catch (e) {
      console.error("[Settings] fetch playlist failed", e);
      const message =
        e instanceof DOMException && e.name === "AbortError"
          ? "Request timed out. Try again or upload the file."
          : e instanceof Error && e.message.startsWith("Playlist too large")
            ? e.message
            : "Could not fetch playlist. The server may block cross-origin requests — upload the file instead.";
      if (mounted.current) setError(message);
      toast.error(message);
    } finally {
      if (mounted.current) setLoading(false);
    }
  };

  const loadFromFile = async (file: File) => {
    const MAX_BYTES = 20 * 1024 * 1024; // 20 MB
    if (file.size > MAX_BYTES) {
      setError("File too large (20 MB max).");
      toast.error("File too large");
      return;
    }
    setLoading(true);
    setError(null);
    setNotice(null);
    try {
      const text = await file.text();
      if (!mounted.current) return;
      apply(text);
    } catch (e) {
      console.error("[Settings] file read failed", e);
      if (mounted.current) setError("Could not read file");
      toast.error("Could not read file");
    } finally {
      if (mounted.current) setLoading(false);
    }
  };


  const reset = () => {
    try {
      localStorage.removeItem(LS_KEY);
      localStorage.removeItem(LS_URL_KEY);
    } catch {/* ignore */}
    setChannels(SAMPLE_CHANNELS);
    setUrl("");
    setNotice("Reset to demo channels.");
    setError(null);
  };

  return (
    <div
      className="fixed inset-0 z-[120] grid place-items-end bg-black/60 backdrop-blur-md sm:place-items-center sm:p-6 animate-in fade-in duration-200"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-panel-title"
    >
      <div
        className="w-full max-w-2xl overflow-hidden rounded-t-3xl glass-strong shadow-2xl sm:rounded-3xl animate-in slide-in-from-bottom-4 duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-[0.2em] text-muted-foreground">
              Preferences
            </div>
            <h2 id="settings-panel-title" className="font-display text-2xl font-semibold tracking-tight">
              Settings
            </h2>
          </div>
          <button
            onClick={onClose}
            aria-label="Close settings"
            className="grid h-9 w-9 place-items-center rounded-full bg-white/5 text-muted-foreground transition hover:bg-white/10 hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="max-h-[80vh] space-y-8 overflow-y-auto p-6 sm:p-8">
          <section>
            <h3 className="font-display text-lg font-semibold tracking-tight">
              IPTV Playlist
            </h3>
            <p className="mt-1 text-sm font-light text-muted-foreground">
              Sign in to your IPTV provider by loading an M3U / M3U8 playlist.
              Your playlist stays on this device.
            </p>

            <div className="mt-5 space-y-3">
              <label className="block">
                <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                  Playlist URL
                </span>
                <div className="mt-2 flex gap-2">
                  <div className="relative flex-1">
                    <LinkIcon className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                    <input
                      type="url"
                      value={url}
                      onChange={(e) => setUrl(e.target.value)}
                      placeholder="https://your-provider.com/get.php?…"
                      className="w-full rounded-full border border-white/10 bg-white/5 py-2.5 pl-10 pr-3 text-sm placeholder:text-muted-foreground focus:border-white/30 focus:outline-none"
                    />
                  </div>
                  <button
                    onClick={loadFromUrl}
                    disabled={loading || !url.trim()}
                    className="inline-flex shrink-0 items-center gap-2 rounded-full bg-foreground px-5 py-2.5 text-sm font-semibold text-background transition hover:bg-white disabled:opacity-40"
                  >
                    {loading ? "Loading…" : "Connect"}
                  </button>
                </div>
              </label>

              <div className="flex items-center gap-3">
                <span className="text-xs text-muted-foreground">or</span>
                <label className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-2 text-sm font-medium transition hover:bg-white/10">
                  <Upload className="h-4 w-4" />
                  Upload .m3u file
                  <input
                    type="file"
                    accept=".m3u,.m3u8,audio/x-mpegurl,application/vnd.apple.mpegurl,text/plain"
                    className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      // Reset so selecting the same file again still triggers change.
                      e.target.value = "";
                      if (f) loadFromFile(f);
                    }}
                  />
                </label>
              </div>

              {error && (
                <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-2.5 text-xs text-red-100">
                  {error}
                </div>
              )}
              {notice && (
                <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-2.5 text-xs text-emerald-100">
                  {notice}
                </div>
              )}
            </div>
          </section>

          <XtreamSection onLoaded={(text, sourceUrl) => apply(text, sourceUrl)} />

          <EpgSection />

          <ExtensionsSection />




          <section className="border-t border-white/10 pt-6">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-display text-lg font-semibold tracking-tight">
                  Loaded channels
                </h3>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {channels.length} channel{channels.length === 1 ? "" : "s"} active
                </p>
              </div>
              <button
                onClick={reset}
                className="inline-flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3.5 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-white/10 hover:text-foreground"
              >
                <Trash2 className="h-3.5 w-3.5" /> Reset
              </button>
            </div>
          </section>

          <AccessibilitySection />

          <ThemeSection />

          <DataSection />

          <ShortcutsSection />

          <AboutSection />

        </div>
      </div>
    </div>
  );
}

const THEMES: { id: Preferences["theme"]; name: string; swatch: string; description: string }[] = [
  { id: "midnight", name: "Midnight", swatch: "linear-gradient(135deg, #0e0e16, #1a1a28)", description: "Cinematic default" },
  { id: "oled", name: "OLED Black", swatch: "linear-gradient(135deg, #000, #0a0a10)", description: "True black, mobile-friendly" },
  { id: "graphite", name: "Graphite", swatch: "linear-gradient(135deg, #2a2a32, #3a3a44)", description: "Softer, daytime tone" },
  { id: "light", name: "Light", swatch: "linear-gradient(135deg, #f5f5f5, #e6e6ec)", description: "Bright, high readability" },
  { id: "high-contrast", name: "High contrast", swatch: "linear-gradient(135deg, #000, #fff)", description: "Maximum legibility" },
];

function ThemeSection() {
  const theme = useUserData((s) => s.preferences.theme);
  const setPreference = useUserData((s) => s.setPreference);
  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight">
        <Palette className="h-4 w-4" /> Appearance
      </h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        Pick a theme variant. Changes apply instantly across the app.
      </p>
      <div className="mt-4 grid grid-cols-2 gap-2.5 sm:grid-cols-3">
        {THEMES.map((t) => {
          const active = theme === t.id;
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => {
                setPreference("theme", t.id);
                toast.success(`Theme: ${t.name}`);
              }}
              aria-pressed={active}
              className={`rounded-2xl border p-2.5 text-left transition ${
                active
                  ? "border-foreground/70 bg-white/10"
                  : "border-white/10 bg-white/5 hover:bg-white/10"
              }`}
            >
              <div
                aria-hidden
                className="h-14 w-full rounded-xl ring-1 ring-white/10"
                style={{ background: t.swatch }}
              />
              <div className="mt-2 text-[13px] font-medium leading-tight">{t.name}</div>
              <div className="text-[11px] leading-tight text-muted-foreground">{t.description}</div>
            </button>
          );
        })}
      </div>
    </section>
  );
}

const SHORTCUTS: { keys: string; label: string }[] = [
  { keys: "⌘K  /  Ctrl K", label: "Open search" },
  { keys: "/", label: "Quick search" },
  { keys: "Space", label: "Play / pause (in player)" },
  { keys: "← →", label: "Seek 10 seconds" },
  { keys: "↑ ↓", label: "Volume up / down" },
  { keys: "M", label: "Mute" },
  { keys: "F", label: "Fullscreen" },
  { keys: "C", label: "Toggle captions" },
  { keys: "Esc", label: "Close dialogs" },
];

function ShortcutsSection() {
  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight">
        <Keyboard className="h-4 w-4" /> Keyboard shortcuts
      </h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        CalmSource is fully keyboard-navigable. These work anywhere in the app.
      </p>
      <ul className="mt-4 grid gap-2 sm:grid-cols-2">
        {SHORTCUTS.map((s) => (
          <li key={s.label} className="flex items-center justify-between gap-3 rounded-2xl border border-white/5 bg-white/[0.03] px-3.5 py-2.5">
            <span className="text-[13px] font-light text-foreground/85">{s.label}</span>
            <kbd className="rounded-md border border-white/10 bg-white/5 px-2 py-0.5 font-mono text-[11px] text-foreground/80">{s.keys}</kbd>
          </li>
        ))}
      </ul>
    </section>
  );
}

function DataSection() {
  const fileRef = useRef<HTMLInputElement>(null);
  const exportAll = () => {
    try {
      const dump: Record<string, unknown> = {};
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (!key || !key.startsWith("lumen.")) continue;
        try { dump[key] = JSON.parse(localStorage.getItem(key) ?? "null"); }
        catch { dump[key] = localStorage.getItem(key); }
      }
      const blob = new Blob([JSON.stringify({ v: 1, exportedAt: Date.now(), data: dump }, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `lumen-backup-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success("Exported your data");
    } catch (e) {
      console.error("[Settings] export failed", e);
      toast.error("Export failed");
    }
  };
  const importAll = async (file: File) => {
    try {
      const text = await file.text();
      const parsed = JSON.parse(text) as { data?: Record<string, unknown> };
      if (!parsed.data || typeof parsed.data !== "object") {
        toast.error("Not a valid CalmSource backup");
        return;
      }
      let count = 0;
      for (const [k, v] of Object.entries(parsed.data)) {
        if (!k.startsWith("lumen.")) continue;
        try { localStorage.setItem(k, typeof v === "string" ? v : JSON.stringify(v)); count++; }
        catch { /* quota */ }
      }
      toast.success(`Imported ${count} entries. Reloading…`);
      setTimeout(() => window.location.reload(), 700);
    } catch (e) {
      console.error("[Settings] import failed", e);
      toast.error("Import failed — invalid file");
    }
  };
  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight">
        <Download className="h-4 w-4" /> Your data
      </h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        Export profiles, watchlist, history, and preferences as a JSON backup.
      </p>
      <div className="mt-4 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={exportAll}
          className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-2 text-sm font-medium transition hover:bg-white/10"
        >
          <Download className="h-4 w-4" /> Export backup
        </button>
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-2 text-sm font-medium transition hover:bg-white/10"
        >
          <Upload className="h-4 w-4" /> Restore backup
        </button>
        <input
          ref={fileRef}
          type="file"
          accept="application/json,.json"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            e.target.value = "";
            if (f) importAll(f);
          }}
        />
      </div>
    </section>
  );
}

/* ============================================================
   Xtream Codes — host/username/password → builds an M3U URL
   ============================================================ */
const LS_XTREAM = "lumen.iptv.xtream.v1";

function XtreamSection({ onLoaded }: { onLoaded: (text: string, sourceUrl: string) => void }) {
  const [host, setHost] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(LS_XTREAM);
      if (!raw) return;
      const v = JSON.parse(raw) as { host?: string; username?: string };
      if (v.host) setHost(v.host);
      if (v.username) setUsername(v.username);
    } catch { /* ignore */ }
  }, []);

  const connect = async () => {
    setErr(null);
    let h = host.trim().replace(/\/+$/, "");
    if (!h) { setErr("Server host is required."); return; }
    if (!/^https?:\/\//i.test(h)) h = `http://${h}`;
    let parsed: URL;
    try { parsed = new URL(h); } catch { setErr("Invalid server URL."); return; }
    if (!/^https?:$/.test(parsed.protocol)) { setErr("Use http:// or https://."); return; }
    const user = encodeURIComponent(username.trim());
    const pass = encodeURIComponent(password);
    if (!user || !pass) { setErr("Username and password are required."); return; }
    const url = `${parsed.origin}${parsed.pathname.replace(/\/$/, "")}/get.php?username=${user}&password=${pass}&type=m3u_plus&output=ts`;
    setBusy(true);
    try {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), 30_000);
      let res: Response;
      try { res = await fetch(url, { signal: ctrl.signal }); }
      finally { clearTimeout(timer); }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      if (!text || text.length < 8) throw new Error("Empty response — check credentials.");
      onLoaded(text, url);
      // Register a Xtream provider so future catalog browsing and
      // pseudo-URL playback (`provider://xtream-default/...`) work.
      providerRegistry.register(
        createXtreamProvider("xtream-default", "Xtream", {
          host: `${parsed.origin}${parsed.pathname.replace(/\/$/, "")}`,
          username: username.trim(),
          password,
        }),
      );
      try {
        localStorage.setItem(LS_XTREAM, JSON.stringify({ host: parsed.origin + parsed.pathname.replace(/\/$/, ""), username: username.trim() }));
      } catch { /* ignore */ }
      toast.success("Xtream login successful");
      // Best-effort: pull the matching XMLTV EPG in the background.
      const epgUrl = `${parsed.origin}${parsed.pathname.replace(/\/$/, "")}/xmltv.php?username=${user}&password=${pass}`;
      loadXmltvFromUrl(epgUrl)
        .then((n) => { if (n > 0) toast.success(`EPG loaded (${n} programmes)`); })
        .catch(() => { /* EPG is optional */ });
    } catch (e) {
      const msg = e instanceof DOMException && e.name === "AbortError"
        ? "Request timed out."
        : e instanceof Error ? e.message : "Connection failed";
      setErr(msg);
      toast.error(`Xtream login failed: ${msg}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight">
        <KeyRound className="h-4 w-4" /> Xtream Codes login
      </h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        Most IPTV providers use Xtream Codes. Enter your server URL and credentials — we build the playlist for you.
      </p>
      <div className="mt-4 grid gap-2.5 sm:grid-cols-3">
        <input
          type="url"
          value={host}
          onChange={(e) => setHost(e.target.value)}
          placeholder="http://your-server.com:8080"
          className="rounded-full border border-white/10 bg-white/5 px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:border-white/30 focus:outline-none sm:col-span-3"
          autoComplete="off"
        />
        <input
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Username"
          className="rounded-full border border-white/10 bg-white/5 px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:border-white/30 focus:outline-none"
          autoComplete="username"
        />
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Password"
          className="rounded-full border border-white/10 bg-white/5 px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:border-white/30 focus:outline-none"
          autoComplete="current-password"
        />
        <button
          type="button"
          onClick={connect}
          disabled={busy}
          className="rounded-full bg-foreground px-5 py-2.5 text-sm font-semibold text-background transition hover:bg-white disabled:opacity-40"
        >
          {busy ? "Connecting…" : "Connect"}
        </button>
      </div>
      {err && (
        <div className="mt-3 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-2.5 text-xs text-red-100">
          {err}
        </div>
      )}
      <p className="mt-3 text-[11px] text-muted-foreground">
        Credentials stay on this device. Only the server host & username are remembered for next time.
      </p>
    </section>
  );
}

/* ============================================================
   Accessibility — reduced data, dyslexia font, reduce motion
   ============================================================ */
function AccessibilitySection() {
  const prefs = useUserData((s) => s.preferences);
  const setPreference = useUserData((s) => s.setPreference);

  const toggles: { key: keyof Preferences; label: string; blurb: string }[] = [
    { key: "dataSaver", label: "Data saver", blurb: "Skip large backdrops, prefer lighter art." },
    { key: "dyslexiaFont", label: "Dyslexia-friendly font", blurb: "Switch to a more legible typeface." },
    { key: "reduceMotion", label: "Reduce motion", blurb: "Tone down hero crossfade and hover effects." },
    { key: "autoplayNext", label: "Auto-play next episode", blurb: "Continue series automatically." },
  ];

  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight">
        <Accessibility className="h-4 w-4" /> Accessibility & playback
      </h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        Tweak CalmSource to your eyes, ears, and bandwidth.
      </p>
      <ul className="mt-4 grid gap-2 sm:grid-cols-2">
        {toggles.map((t) => {
          const value = !!prefs[t.key];
          return (
            <li key={t.key as string} className="flex items-start justify-between gap-3 rounded-2xl border border-white/5 bg-white/[0.03] p-3.5">
              <div className="min-w-0">
                <div className="text-[13px] font-medium text-foreground/90">{t.label}</div>
                <div className="text-[11.5px] leading-snug text-muted-foreground">{t.blurb}</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={value}
                onClick={() => setPreference(t.key, !value as never)}
                className={`relative h-6 w-11 shrink-0 rounded-full transition ${value ? "bg-emerald-500/80" : "bg-white/15"}`}
              >
                <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${value ? "translate-x-5" : "translate-x-0.5"}`} />
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/* ============================================================
   EPG (XMLTV) loader — manual URL plus current stats
   ============================================================ */
const LS_EPG_URL = "lumen.epg.xmltvUrl.v1";

function EpgSection() {
  const [url, setUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [, force] = useState(0);

  useEffect(() => {
    try {
      const v = localStorage.getItem(LS_EPG_URL);
      if (v) setUrl(v);
    } catch { /* ignore */ }
    return subscribeEpg(() => force((n) => n + 1));
  }, []);

  const load = async () => {
    const u = url.trim();
    if (!u) return;
    if (!/^https?:\/\//i.test(u)) { toast.error("EPG URL must be http(s)://"); return; }
    setBusy(true);
    try {
      const n = await loadXmltvFromUrl(u);
      if (n === 0) toast.message("No programmes parsed from EPG");
      else toast.success(`EPG loaded (${n} programmes)`);
      try { localStorage.setItem(LS_EPG_URL, u); } catch { /* ignore */ }
    } catch (e) {
      toast.error(`EPG load failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setBusy(false);
    }
  };

  const stats = epgStats();
  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="font-display text-lg font-semibold tracking-tight">EPG (XMLTV)</h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        Paste an XMLTV URL to power the real Now &amp; Next on every channel. Matches on <code>tvg-id</code>.
      </p>
      <div className="mt-4 grid gap-2.5 sm:grid-cols-[1fr_auto_auto]">
        <input
          type="url"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com/xmltv.php?..."
          className="rounded-full border border-white/10 bg-white/5 px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:border-white/30 focus:outline-none"
          autoComplete="off"
        />
        <button
          onClick={load}
          disabled={busy}
          className="rounded-full bg-white px-4 py-2.5 text-sm font-medium text-black transition disabled:opacity-60"
        >
          {busy ? "Loading…" : "Load EPG"}
        </button>
        <button
          onClick={() => { clearEpg(); toast.message("EPG cleared"); }}
          className="rounded-full border border-white/10 bg-white/5 px-4 py-2.5 text-sm font-medium text-muted-foreground transition hover:bg-white/10 hover:text-foreground"
        >
          Clear
        </button>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        {stats.channels > 0
          ? `${stats.channels} channels in guide${stats.loadedAt ? ` · loaded ${new Date(stats.loadedAt).toLocaleTimeString()}` : ""}`
          : "No EPG loaded — using mock now/next."}
      </p>
    </section>
  );
}

function ExtensionsSection() {
  const [items, setItems] = useState<InstalledExtension[]>(() => extensionRepository.getAll());
  const [url, setUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [refreshing, setRefreshing] = useState<string | null>(null);

  useEffect(() => extensionRepository.subscribe(() => setItems(extensionRepository.getAll())), []);

  const install = async (rawUrl: string) => {
    const target = rawUrl.trim();
    if (!target) { toast.error("Paste a manifest URL"); return; }
    setBusy(true);
    try {
      const ext = await extensionRepository.install(target);
      toast.success(`Installed ${ext.manifest.name} v${ext.manifest.version}`);
      setUrl("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Install failed");
    } finally {
      setBusy(false);
    }
  };

  const refresh = async (ext: InstalledExtension) => {
    setRefreshing(ext.baseUrl);
    try {
      await extensionRepository.refresh(ext.baseUrl);
      toast.success(`${ext.manifest.name} is healthy`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Health check failed");
    } finally {
      setRefreshing(null);
    }
  };

  return (
    <section className="border-t border-white/10 pt-6">
      <h3 className="inline-flex items-center gap-2 font-display text-lg font-semibold tracking-tight">
        <Puzzle className="h-4 w-4" /> Catalog add-ons
      </h3>
      <p className="mt-1 text-sm font-light text-muted-foreground">
        Install catalog add-ons to power movies, series, and metadata. Paste a manifest URL or pick a preset.
      </p>

      <div className="mt-4 grid gap-2.5 sm:grid-cols-[1fr_auto]">
        <input
          type="url"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com/manifest.json"
          className="rounded-full border border-white/10 bg-white/5 px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:border-white/30 focus:outline-none"
          autoComplete="off"
          onKeyDown={(e) => { if (e.key === "Enter") install(url); }}
        />
        <button
          onClick={() => install(url)}
          disabled={busy}
          className="rounded-full bg-white px-4 py-2.5 text-sm font-medium text-black transition disabled:opacity-60"
        >
          {busy ? "Installing…" : "Install"}
        </button>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {EXTENSION_PRESETS.map((p) => {
          const installed = items.some((e) => e.manifest.id === p.name.toLowerCase() || e.baseUrl.includes(new URL(p.manifestUrl).host));
          return (
            <button
              key={p.manifestUrl}
              onClick={() => install(p.manifestUrl)}
              disabled={busy || installed}
              title={p.description}
              className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-white/10 hover:text-foreground disabled:opacity-50"
            >
              {installed ? `✓ ${p.name}` : `+ ${p.name}`}
            </button>
          );
        })}
      </div>

      <div className="mt-5 space-y-2.5">
        {items.length === 0 && (
          <div className="rounded-xl border border-dashed border-white/10 bg-white/[0.02] px-4 py-6 text-center text-xs text-muted-foreground">
            No add-ons installed. Try Cinemeta to get started.
          </div>
        )}
        {items.map((ext) => {
          const catalogs = ext.manifest.catalogs?.length ?? 0;
          const resources = ext.manifest.resources?.length ?? 0;
          return (
            <div
              key={ext.baseUrl}
              className="rounded-2xl border border-white/10 bg-white/[0.03] p-4"
            >
              <div className="flex items-start gap-3">
                {ext.manifest.logo ? (
                  <img
                    src={ext.manifest.logo}
                    alt=""
                    className="h-10 w-10 shrink-0 rounded-lg bg-black/40 object-contain"
                    loading="lazy"
                  />
                ) : (
                  <div className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-white/10 text-xs font-semibold">
                    {ext.manifest.name.slice(0, 2).toUpperCase()}
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="truncate text-sm font-semibold">{ext.manifest.name}</span>
                    <span className="rounded-full bg-white/10 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                      v{ext.manifest.version}
                    </span>
                    <HealthBadge health={ext.health} />
                  </div>
                  {ext.manifest.description && (
                    <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{ext.manifest.description}</p>
                  )}
                  <div className="mt-1.5 text-[11px] text-muted-foreground">
                    {catalogs} catalog{catalogs === 1 ? "" : "s"} · {resources} resource{resources === 1 ? "" : "s"} · {ext.manifest.types.slice(0, 3).join(", ")}
                  </div>
                  {ext.error && (
                    <div className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-red-500/10 px-2 py-1 text-[11px] text-red-200">
                      <AlertCircle className="h-3 w-3" /> {ext.error}
                    </div>
                  )}
                </div>
                <div className="flex flex-col items-end gap-1.5">
                  <button
                    onClick={() => extensionRepository.setEnabled(ext.baseUrl, !ext.enabled)}
                    title={ext.enabled ? "Disable" : "Enable"}
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-medium transition ${
                      ext.enabled
                        ? "bg-emerald-500/15 text-emerald-200 hover:bg-emerald-500/25"
                        : "bg-white/5 text-muted-foreground hover:bg-white/10"
                    }`}
                  >
                    <Power className="h-3 w-3" /> {ext.enabled ? "Enabled" : "Disabled"}
                  </button>
                  <button
                    onClick={() => refresh(ext)}
                    disabled={refreshing === ext.baseUrl}
                    title="Re-fetch manifest"
                    className="inline-flex items-center gap-1 rounded-full bg-white/5 px-2.5 py-1 text-[11px] font-medium text-muted-foreground transition hover:bg-white/10 hover:text-foreground disabled:opacity-50"
                  >
                    <RefreshCw className={`h-3 w-3 ${refreshing === ext.baseUrl ? "animate-spin" : ""}`} /> Check
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(`Uninstall ${ext.manifest.name}?`)) {
                        extensionRepository.uninstall(ext.baseUrl);
                        toast.message("Extension removed");
                      }
                    }}
                    className="inline-flex items-center gap-1 rounded-full bg-white/5 px-2.5 py-1 text-[11px] font-medium text-muted-foreground transition hover:bg-red-500/20 hover:text-red-200"
                  >
                    <Trash2 className="h-3 w-3" /> Remove
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function HealthBadge({ health }: { health: InstalledExtension["health"] }) {
  if (health === "ok") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/15 px-1.5 py-0.5 text-[10px] font-medium text-emerald-200">
        <CheckCircle2 className="h-2.5 w-2.5" /> Healthy
      </span>
    );
  }
  if (health === "error") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-red-500/15 px-1.5 py-0.5 text-[10px] font-medium text-red-200">
        <AlertCircle className="h-2.5 w-2.5" /> Error
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-white/10 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
      Unknown
    </span>
  );
}
