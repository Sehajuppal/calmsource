import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Captions,
  ChevronsRight,
  Maximize,
  Minimize2,
  Pause,
  PictureInPicture2,
  Play,
  Settings2,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";
import { usePlayer } from "../lib/player-store";
import { useUserData } from "../lib/userdata";
import { subtitleToBlobUrl } from "../lib/srt";

type SubtitleTrack = { label: string; src: string };

function formatTime(s: number) {
  if (!Number.isFinite(s) || s < 0) s = 0;
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = Math.floor(s % 60);
  const mm = m.toString().padStart(h ? 2 : 1, "0");
  const ss = sec.toString().padStart(2, "0");
  return h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export default function Player() {
  const { source, mode, close, minimize, restore } = usePlayer();
  const recordProgress = useUserData((s) => s.recordProgress);
  // NOTE: we deliberately do NOT subscribe to `continueWatching` here.
  // `recordProgress` runs every 5s during playback and writes a new map
  // reference; subscribing would re-render the entire player UI on every
  // tick. We only need the resume position once at mount — read it
  // imperatively inside the effect via `useUserData.getState()`.
  const pushHistory = useUserData((s) => s.pushHistory);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const shellRef = useRef<HTMLDivElement | null>(null);
  // Loose typing so we can read HLS audio/quality tracks without pinning hls.js types.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const hlsRef = useRef<any>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const gainRef = useRef<GainNode | null>(null);

  const [playing, setPlaying] = useState(false);
  const [current, setCurrent] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [rate, setRate] = useState(1);
  const [boost, setBoost] = useState(1);
  const [showSubs, setShowSubs] = useState(false);
  const [extraTracks, setExtraTracks] = useState<SubtitleTrack[]>([]);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [showSettings, setShowSettings] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [audioTracks, setAudioTracks] = useState<{ id: number; name: string; lang?: string }[]>([]);
  const [audioId, setAudioId] = useState<number | null>(null);
  const [subTracks, setSubTracks] = useState<{ index: number; label: string; lang?: string }[]>([]);
  const [activeSubIndex, setActiveSubIndex] = useState<number>(-1); // -1 = off
  const subInputId = useId();

  const sourceId =
    source?.kind === "title" ? source.title.id : source?.kind === "live" ? source.channelId : "";
  const isLive = source?.kind === "live";

  // ── Mount / attach stream ──────────────────────────────────────────────
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !source) return;

    setError(null);
    setCurrent(0);
    setDuration(0);
    let destroyed = false;
    const isM3U8 = /\.m3u8($|\?)/i.test(source.streamUrl);
    const canNative = video.canPlayType("application/vnd.apple.mpegurl");

    // Local handle so cleanup destroys exactly the instance this effect
    // created. Previously we only destroyed `hlsRef.current`, which a rapid
    // second source-switch could have already overwritten — leaking the
    // first instance and its socket/decoder.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let localHls: any = null;

    const markHistory = () => {
      if (source.kind === "title") pushHistory(source.title.id);
    };

    const start = async () => {
      try {
        if (isM3U8 && !canNative) {
          const mod = await import("hls.js");
          if (destroyed) return;
          const Hls = mod.default;
          if (!Hls.isSupported()) {
            setError("HLS not supported in this browser");
            return;
          }
          const { hlsConfig } = await import("../lib/hls-config");
          if (destroyed) return;
          const instance = new Hls(hlsConfig());
          localHls = instance;
          hlsRef.current = instance;
          instance.loadSource(source.streamUrl);
          instance.attachMedia(video);
          instance.on(Hls.Events.MANIFEST_PARSED, () => {
            if (destroyed) return;
            markHistory();
            // resume position for titles
            if (source.kind === "title") {
              const resume = useUserData.getState().continueWatching[source.title.id]?.position;
              if (resume && resume > 5) video.currentTime = resume;
            }
            // Surface audio tracks (multi-language streams)
            const at = (instance.audioTracks ?? []) as Array<{ id: number; name: string; lang?: string }>;
            setAudioTracks(at.map((t) => ({ id: t.id, name: t.name || t.lang || `Track ${t.id + 1}`, lang: t.lang })));
            setAudioId(typeof instance.audioTrack === "number" ? instance.audioTrack : null);
            void video.play().catch(() => {});
          });
          instance.on(Hls.Events.AUDIO_TRACK_SWITCHED, (_e: unknown, data: { id: number }) => {
            setAudioId(data.id);
          });
          instance.on(Hls.Events.ERROR, (_e: unknown, data: { fatal?: boolean }) => {
            if (data?.fatal) setError("Stream unavailable");
          });
        } else {
          video.src = source.streamUrl;
          video.addEventListener(
            "loadedmetadata",
            () => {
              if (destroyed) return;
              markHistory();
              if (source.kind === "title") {
                const resume = useUserData.getState().continueWatching[source.title.id]?.position;
                if (resume && resume > 5) video.currentTime = resume;
              }
              void video.play().catch(() => {});
            },
            { once: true },
          );
        }
      } catch {
        setError("Could not load stream");
      }
    };

    void start();

    return () => {
      destroyed = true;
      // Destroy via local handle — guarantees we tear down the exact
      // instance created above, even if `hlsRef.current` was reassigned by
      // a rapidly-mounted next effect.
      if (localHls) {
        try { localHls.destroy(); } catch { /* noop */ }
      }
      if (hlsRef.current === localHls) hlsRef.current = null;
      try {
        video.removeAttribute("src");
        video.load();
      } catch {
        /* noop */
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceId]);

  // ── Video element event wiring ─────────────────────────────────────────
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    const onTime = () => setCurrent(video.currentTime || 0);
    const onLoaded = () => setDuration(video.duration || 0);
    const onVol = () => {
      setVolume(video.volume);
      setMuted(video.muted);
    };
    const onRate = () => setRate(video.playbackRate || 1);
    video.addEventListener("play", onPlay);
    video.addEventListener("pause", onPause);
    video.addEventListener("timeupdate", onTime);
    video.addEventListener("loadedmetadata", onLoaded);
    video.addEventListener("durationchange", onLoaded);
    video.addEventListener("volumechange", onVol);
    video.addEventListener("ratechange", onRate);
    return () => {
      video.removeEventListener("play", onPlay);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("timeupdate", onTime);
      video.removeEventListener("loadedmetadata", onLoaded);
      video.removeEventListener("durationchange", onLoaded);
      video.removeEventListener("volumechange", onVol);
      video.removeEventListener("ratechange", onRate);
    };
  }, []);

  // ── Persist resume position every 5s (titles only) ─────────────────────
  useEffect(() => {
    if (!source || source.kind !== "title") return;
    const id = window.setInterval(() => {
      const v = videoRef.current;
      if (!v || v.paused) return;
      recordProgress(source.title.id, v.currentTime, v.duration || 0);
    }, 5000);
    return () => window.clearInterval(id);
  }, [source, recordProgress]);

  // ── Audio-boost wiring (WebAudio GainNode) ─────────────────────────────
  // Always keep gain in sync so dropping boost back to 1× actually applies.
  // Lazily create the AudioContext on first non-trivial value to avoid
  // wiring WebAudio before any user gesture.
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    try {
      if (!audioCtxRef.current) {
        if (boost === 1) return; // nothing to do yet — defer ctx creation
        const Ctx = (window.AudioContext ||
          (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext) as typeof AudioContext;
        const ctx = new Ctx();
        const src = ctx.createMediaElementSource(video);
        const gain = ctx.createGain();
        src.connect(gain).connect(ctx.destination);
        audioCtxRef.current = ctx;
        gainRef.current = gain;
      }
      if (gainRef.current) gainRef.current.gain.value = boost;
      void audioCtxRef.current?.resume();
    } catch {
      /* user gesture required or already piped — ignore */
    }
  }, [boost]);

  // ── Keep playbackRate in sync ─────────────────────────────────────────
  useEffect(() => {
    const v = videoRef.current;
    if (v) v.playbackRate = rate;
  }, [rate]);

  // ── Auto-hide controls ─────────────────────────────────────────────────
  useEffect(() => {
    if (mode !== "full") return;
    let timer = window.setTimeout(() => setControlsVisible(false), 3000);
    const reset = () => {
      setControlsVisible(true);
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setControlsVisible(false), 3000);
    };
    const node = shellRef.current;
    node?.addEventListener("mousemove", reset);
    node?.addEventListener("touchstart", reset);
    return () => {
      window.clearTimeout(timer);
      node?.removeEventListener("mousemove", reset);
      node?.removeEventListener("touchstart", reset);
    };
  }, [mode]);

  // ── Keyboard shortcuts ─────────────────────────────────────────────────
  useEffect(() => {
    if (mode !== "full") return;
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      const v = videoRef.current;
      if (!v) return;
      switch (e.key) {
        case " ":
        case "k":
          e.preventDefault();
          v.paused ? void v.play() : v.pause();
          break;
        case "ArrowLeft":
          v.currentTime = Math.max(0, v.currentTime - 10);
          break;
        case "ArrowRight":
          v.currentTime = Math.min(v.duration || Infinity, v.currentTime + 10);
          break;
        case "ArrowUp":
          v.volume = Math.min(1, v.volume + 0.05);
          break;
        case "ArrowDown":
          v.volume = Math.max(0, v.volume - 0.05);
          break;
        case "m":
          v.muted = !v.muted;
          break;
        case "f":
          void toggleFullscreen();
          break;
        case "c":
          setShowSubs((x) => !x);
          break;
        case "Escape":
          // let browser handle fullscreen escape first
          if (!document.fullscreenElement) close();
          break;
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  // ── Subtitle tracks: enumerate + apply selection ──────────────────────
  // Mirrors both embedded HLS subs and uploaded VTT/SRT into one menu.
  // activeSubIndex: -1 disables all, otherwise picks the one to show.
  // showSubs acts as a master switch; selection persists when toggled off.
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    const refresh = () => {
      const list: { index: number; label: string; lang?: string }[] = [];
      for (let i = 0; i < v.textTracks.length; i++) {
        const t = v.textTracks[i];
        list.push({ index: i, label: t.label || t.language || `Subtitle ${i + 1}`, lang: t.language });
      }
      setSubTracks(list);
    };
    refresh();
    v.textTracks.addEventListener?.("change", refresh);
    v.textTracks.addEventListener?.("addtrack", refresh);
    v.textTracks.addEventListener?.("removetrack", refresh);
    return () => {
      v.textTracks.removeEventListener?.("change", refresh);
      v.textTracks.removeEventListener?.("addtrack", refresh);
      v.textTracks.removeEventListener?.("removetrack", refresh);
    };
  }, [extraTracks.length, source]);

  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    const pick = showSubs ? (activeSubIndex >= 0 ? activeSubIndex : 0) : -1;
    for (let i = 0; i < v.textTracks.length; i++) {
      v.textTracks[i].mode = i === pick ? "showing" : "disabled";
    }
  }, [showSubs, activeSubIndex, subTracks.length]);

  const switchAudio = useCallback((id: number) => {
    const hls = hlsRef.current;
    if (!hls) return;
    try {
      hls.audioTrack = id;
      setAudioId(id);
    } catch {
      /* ignore */
    }
  }, []);

  const toggleFullscreen = useCallback(async () => {
    const node = shellRef.current;
    if (!node) return;
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await node.requestFullscreen();
    } catch {
      /* ignore */
    }
  }, []);

  const togglePiP = useCallback(async () => {
    const v = videoRef.current as
      | (HTMLVideoElement & { requestPictureInPicture?: () => Promise<unknown> })
      | null;
    if (!v) return;
    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
      } else if (v.requestPictureInPicture) {
        await v.requestPictureInPicture();
      }
    } catch {
      /* ignore */
    }
  }, []);

  const onSubtitleFile = useCallback(async (file: File) => {
    const text = await file.text();
    const kind = /\.srt$/i.test(file.name) ? "srt" : "vtt";
    const url = subtitleToBlobUrl(text, kind);
    setExtraTracks((prev) => [...prev, { label: file.name, src: url }]);
    setShowSubs(true);
  }, []);

  // Revoke any subtitle blob URLs on unmount — they're held by the
  // browser until released and would otherwise leak per uploaded file.
  useEffect(() => {
    return () => {
      for (const t of extraTracks) {
        try { URL.revokeObjectURL(t.src); } catch { /* noop */ }
      }
    };
    // We intentionally only run cleanup on unmount; mid-life additions
    // are released here too because the cleanup closes over the latest
    // array on each render.
  }, [extraTracks]);

  // Timecode-aware Skip Intro.
  // Order of precedence:
  //   1. user-saved marker in localStorage (intro-markers:{id})
  //   2. title.introStart/introEnd from extension/catalog metadata
  //   3. sensible default window for series only (5s..90s)
  const introMarkers = useMemo(() => {
    if (isLive || source?.kind !== "title") return null;
    const t = source.title;
    let start: number | undefined;
    let end: number | undefined;
    try {
      const raw = typeof window !== "undefined"
        ? window.localStorage.getItem(`intro-markers:${t.id}`)
        : null;
      if (raw) {
        const parsed = JSON.parse(raw) as { start?: number; end?: number };
        if (typeof parsed.start === "number") start = parsed.start;
        if (typeof parsed.end === "number") end = parsed.end;
      }
    } catch {
      // ignore corrupt entry
    }
    if (start == null && typeof t.introStart === "number") start = t.introStart;
    if (end == null && typeof t.introEnd === "number") end = t.introEnd;
    if (start == null && end == null && t.kind === "series") {
      start = 5;
      end = 90;
    }
    if (start == null || end == null || end <= start) return null;
    return { start, end };
  }, [isLive, source]);

  const skipIntroVisible = useMemo(
    () => !!introMarkers && current >= introMarkers.start && current < introMarkers.end,
    [current, introMarkers],
  );

  if (!source || mode === "closed") return null;

  const heading = source.kind === "title" ? source.title.name : source.name;
  const isMini = mode === "mini";

  // Single shared <video> element across mini/full so HLS attachment survives
  // mode toggles. Wrapper layout & overlays switch on `mode` instead.
  return (
    <div
      ref={shellRef}
      role="dialog"
      aria-modal={!isMini}
      aria-label={`Now playing ${heading}`}
      className={
        isMini
          ? "fixed bottom-5 right-5 z-[120] w-[340px] overflow-hidden rounded-2xl bg-black shadow-[0_30px_80px_-30px_rgba(0,0,0,0.9)] ring-1 ring-white/15"
          : `fixed inset-0 z-[110] bg-black text-white ${
              controlsVisible ? "cursor-default" : "cursor-none"
            }`
      }
    >
      <video
        ref={videoRef}
        playsInline
        className={
          isMini
            ? "aspect-video w-full bg-black object-contain"
            : "absolute inset-0 h-full w-full bg-black object-contain"
        }
        onClick={() => {
          if (isMini) {
            restore();
            return;
          }
          const v = videoRef.current;
          if (!v) return;
          v.paused ? void v.play() : v.pause();
        }}
      >
        {extraTracks.map((t, i) => (
          <track
            key={t.src}
            kind="subtitles"
            src={t.src}
            label={t.label}
            default={i === 0}
          />
        ))}
      </video>

      {isMini ? (
        <div className="flex items-center gap-2 px-3 py-2">
          <button
            type="button"
            onClick={restore}
            className="min-w-0 flex-1 truncate text-left text-[12px] font-medium text-white"
          >
            {heading}
          </button>
          <button
            onClick={() => {
              const v = videoRef.current;
              if (!v) return;
              v.paused ? void v.play() : v.pause();
            }}
            className="grid h-7 w-7 place-items-center rounded-full bg-white/10 text-white hover:bg-white/20"
            aria-label={playing ? "Pause" : "Play"}
          >
            {playing ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
          </button>
          <button
            onClick={close}
            className="grid h-7 w-7 place-items-center rounded-full bg-white/10 text-white hover:bg-white/20"
            aria-label="Close"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ) : (
        <>
          {error && (
            <div
              role="alert"
              className="absolute inset-x-0 top-1/2 -translate-y-1/2 text-center text-sm text-white/80"
            >
              {error}
            </div>
          )}

          {/* Top bar */}
          <div
            className={`pointer-events-none absolute inset-x-0 top-0 flex items-start justify-between gap-3 bg-gradient-to-b from-black/70 to-transparent p-4 transition-opacity duration-300 ${
              controlsVisible ? "opacity-100" : "opacity-0"
            }`}
          >
            <div className="pointer-events-auto min-w-0">
              <div className="text-[11px] font-medium uppercase tracking-[0.2em] text-white/60">
                {isLive ? "Live" : "Now playing"}
              </div>
              <div className="mt-1 truncate font-display text-lg font-semibold tracking-[-0.02em]">
                {heading}
              </div>
            </div>
            <div className="pointer-events-auto flex items-center gap-2">
              <button
                onClick={minimize}
                aria-label="Minimize"
                className="grid h-10 w-10 place-items-center rounded-full bg-white/10 hover:bg-white/20"
              >
                <Minimize2 className="h-4 w-4" />
              </button>
              <button
                onClick={close}
                aria-label="Close player"
                className="grid h-10 w-10 place-items-center rounded-full bg-white/10 hover:bg-white/20"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>

          {/* Skip intro */}
          {skipIntroVisible && introMarkers && (
            <button
              onClick={() => {
                const v = videoRef.current;
                if (v) v.currentTime = introMarkers.end;
              }}
              className="absolute bottom-28 right-6 z-10 inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 text-[13px] font-semibold text-black shadow-lg hover:bg-white/90"
            >
              <ChevronsRight className="h-4 w-4" /> Skip intro
            </button>
          )}

          {/* Bottom controls */}
          <div
            className={`absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent px-4 pb-5 pt-10 transition-opacity duration-300 ${
              controlsVisible ? "opacity-100" : "opacity-0 pointer-events-none"
            }`}
          >
            {!isLive && (
              <div className="mb-3 flex items-center gap-3 text-[11px] tabular-nums text-white/70">
                <span>{formatTime(current)}</span>
                <input
                  type="range"
                  min={0}
                  max={duration || 0}
                  step={0.1}
                  value={current}
                  onChange={(e) => {
                    const v = videoRef.current;
                    if (v) v.currentTime = Number(e.target.value);
                  }}
                  className="flex-1 accent-white"
                  aria-label="Seek"
                />
                <span>{formatTime(duration)}</span>
              </div>
            )}
            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => {
                  const v = videoRef.current;
                  if (!v) return;
                  v.paused ? void v.play() : v.pause();
                }}
                aria-label={playing ? "Pause" : "Play"}
                className="grid h-11 w-11 place-items-center rounded-full bg-white text-black hover:bg-white/90"
              >
                {playing ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 fill-current" />}
              </button>
              <div className="ml-1 flex items-center gap-2">
                <button
                  onClick={() => {
                    const v = videoRef.current;
                    if (v) v.muted = !v.muted;
                  }}
                  aria-label={muted ? "Unmute" : "Mute"}
                  className="grid h-10 w-10 place-items-center rounded-full bg-white/10 hover:bg-white/20"
                >
                  {muted || volume === 0 ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
                </button>
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.01}
                  value={muted ? 0 : volume}
                  onChange={(e) => {
                    const v = videoRef.current;
                    if (!v) return;
                    v.volume = Number(e.target.value);
                    v.muted = v.volume === 0;
                  }}
                  className="w-24 accent-white"
                  aria-label="Volume"
                />
              </div>
              <div className="ml-auto flex items-center gap-2">
                <label
                  htmlFor={subInputId}
                  className="inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full bg-white/10 px-3 text-[12px] font-medium hover:bg-white/20"
                  title="Upload subtitles (.srt or .vtt)"
                >
                  Upload subs
                </label>
                <input
                  id={subInputId}
                  type="file"
                  accept=".srt,.vtt,text/vtt"
                  className="hidden"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) void onSubtitleFile(f);
                    e.target.value = "";
                  }}
                />
                <button
                  onClick={() => setShowSubs((x) => !x)}
                  aria-pressed={showSubs}
                  aria-label="Toggle captions"
                  className={`grid h-10 w-10 place-items-center rounded-full ${
                    showSubs ? "bg-white text-black" : "bg-white/10 hover:bg-white/20"
                  }`}
                >
                  <Captions className="h-4 w-4" />
                </button>
                <button
                  onClick={() => setShowSettings((x) => !x)}
                  aria-label="Playback settings"
                  className="grid h-10 w-10 place-items-center rounded-full bg-white/10 hover:bg-white/20"
                >
                  <Settings2 className="h-4 w-4" />
                </button>
                <button
                  onClick={togglePiP}
                  aria-label="Picture in picture"
                  className="grid h-10 w-10 place-items-center rounded-full bg-white/10 hover:bg-white/20"
                >
                  <PictureInPicture2 className="h-4 w-4" />
                </button>
                <button
                  onClick={toggleFullscreen}
                  aria-label="Fullscreen"
                  className="grid h-10 w-10 place-items-center rounded-full bg-white/10 hover:bg-white/20"
                >
                  <Maximize className="h-4 w-4" />
                </button>
              </div>
            </div>

            {showSettings && (
              <div className="mt-3 grid gap-3 rounded-2xl bg-black/70 p-4 ring-1 ring-white/10 backdrop-blur sm:grid-cols-2">
                <div>
                  <div className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-white/60">
                    Playback speed
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {[0.5, 0.75, 1, 1.25, 1.5, 1.75, 2].map((r) => (
                      <button
                        key={r}
                        onClick={() => setRate(r)}
                        className={`rounded-full px-3 py-1 text-[12px] font-medium ${
                          rate === r ? "bg-white text-black" : "bg-white/10 hover:bg-white/20"
                        }`}
                      >
                        {r}×
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <div className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-white/60">
                    Audio boost ({Math.round(boost * 100)}%)
                  </div>
                  <input
                    type="range"
                    min={1}
                    max={3}
                    step={0.1}
                    value={boost}
                    onChange={(e) => setBoost(Number(e.target.value))}
                    className="w-full accent-white"
                    aria-label="Audio boost"
                  />
                  <div className="mt-1 text-[10px] text-white/50">
                    Up to 3× over system volume — use with care.
                  </div>
                </div>
                {audioTracks.length > 1 && (
                  <div>
                    <div className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-white/60">
                      Audio
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      {audioTracks.map((t) => (
                        <button
                          key={t.id}
                          onClick={() => switchAudio(t.id)}
                          className={`rounded-full px-3 py-1 text-[12px] font-medium ${
                            audioId === t.id ? "bg-white text-black" : "bg-white/10 hover:bg-white/20"
                          }`}
                        >
                          {t.name}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                {subTracks.length > 0 && (
                  <div className="sm:col-span-2">
                    <div className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-white/60">
                      Subtitles
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      <button
                        onClick={() => { setActiveSubIndex(-1); setShowSubs(false); }}
                        className={`rounded-full px-3 py-1 text-[12px] font-medium ${
                          !showSubs ? "bg-white text-black" : "bg-white/10 hover:bg-white/20"
                        }`}
                      >
                        Off
                      </button>
                      {subTracks.map((t) => (
                        <button
                          key={t.index}
                          onClick={() => { setActiveSubIndex(t.index); setShowSubs(true); }}
                          className={`rounded-full px-3 py-1 text-[12px] font-medium ${
                            showSubs && activeSubIndex === t.index ? "bg-white text-black" : "bg-white/10 hover:bg-white/20"
                          }`}
                        >
                          {t.label}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
