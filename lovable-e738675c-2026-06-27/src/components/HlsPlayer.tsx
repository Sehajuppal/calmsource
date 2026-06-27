import { useEffect, useRef, useState } from "react";
import { hlsConfig } from "../lib/hls-config";

type Props = {
  src: string;
  poster?: string;
  className?: string;
  controls?: boolean;
  muted?: boolean;
  autoPlay?: boolean;
};

export function HlsPlayer({
  src,
  poster,
  className,
  controls = true,
  muted = false,
  autoPlay = true,
}: Props) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    if (!src || typeof src !== "string") {
      setError("No stream URL provided");
      return;
    }

    setError(null);

    let destroyed = false;
    let hls: { destroy: () => void } | null = null;

    const isM3U8 = /\.m3u8($|\?)/i.test(src);
    const canNative = video.canPlayType("application/vnd.apple.mpegurl");

    const onVideoError = () => {
      const mediaError = video.error;
      const message =
        mediaError?.code === MediaError.MEDIA_ERR_NETWORK
          ? "Network error while loading stream"
          : mediaError?.code === MediaError.MEDIA_ERR_DECODE
            ? "Stream could not be decoded"
            : mediaError?.code === MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED
              ? "Stream format not supported"
              : "Could not play stream";
      setError(message);
    };
    video.addEventListener("error", onVideoError);

    const start = async () => {
      try {
        if (isM3U8 && !canNative) {
          let mod;
          try {
            mod = await import("hls.js");
          } catch (e) {
            console.error("[HlsPlayer] failed to load hls.js", e);
            if (!destroyed) setError("Video player failed to load");
            return;
          }
          if (destroyed) return;
          const Hls = mod.default;
          if (Hls.isSupported()) {
            const instance = new Hls(hlsConfig());
            hls = instance; // assign immediately so cleanup always disposes
            let netRetries = 0;
            let mediaRetries = 0;
            const MAX_RETRIES = 2;
            try {
              instance.loadSource(src);
              instance.attachMedia(video);
            } catch (e) {
              console.error("[HlsPlayer] attach failed", e);
              setError("Could not attach stream");
              instance.destroy();
              return;
            }
            instance.on(Hls.Events.MANIFEST_PARSED, () => {
              if (destroyed || !autoPlay) return;
              video.play().catch((e) => {
                console.warn("[HlsPlayer] autoplay rejected", e);
              });
            });
            instance.on(Hls.Events.ERROR, (_e, data) => {
              if (!data?.fatal) return;
              console.error("[HlsPlayer] fatal hls error", data);
              switch (data.type) {
                case Hls.ErrorTypes.NETWORK_ERROR:
                  if (netRetries++ < MAX_RETRIES) {
                    try { instance.startLoad(); } catch { setError("Stream network error"); }
                  } else {
                    setError("Stream network error");
                    try { instance.destroy(); } catch {/* noop */}
                  }
                  break;
                case Hls.ErrorTypes.MEDIA_ERROR:
                  if (mediaRetries++ < MAX_RETRIES) {
                    try { instance.recoverMediaError(); } catch { setError("Stream media error"); }
                  } else {
                    setError("Stream media error");
                    try { instance.destroy(); } catch {/* noop */}
                  }
                  break;
                default:
                  setError("Stream unavailable");
                  try {
                    instance.destroy();
                  } catch {/* noop */}
              }
            });
          } else {
            setError("HLS not supported in this browser");
          }
        } else {
          video.src = src;
          if (autoPlay) {
            try {
              await video.play();
            } catch (e) {
              // Autoplay rejection is non-fatal — user can press play.
              console.warn("[HlsPlayer] autoplay rejected", e);
            }
          }
        }

      } catch (e) {
        console.error("[HlsPlayer] start failed", e);
        setError("Could not load stream");
      }
    };

    start();

    return () => {
      destroyed = true;
      video.removeEventListener("error", onVideoError);
      if (hls) {
        try {
          hls.destroy();
        } catch {/* noop */}
      }
      try {
        video.removeAttribute("src");
        video.load();
      } catch {/* noop */}
    };
  }, [src, autoPlay]);

  return (
    <div className={`relative overflow-hidden bg-black ${className ?? ""}`}>
      <video
        ref={videoRef}
        poster={poster}
        controls={controls}
        muted={muted}
        playsInline
        className="h-full w-full object-contain"
      />
      {error && (
        <div
          role="alert"
          className="absolute inset-0 grid place-items-center bg-black/70 px-6 text-center text-sm text-muted-foreground"
        >
          {error}
        </div>
      )}
    </div>
  );
}
