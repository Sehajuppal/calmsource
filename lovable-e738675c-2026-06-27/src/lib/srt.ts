// Convert SRT subtitle text into WebVTT and produce a blob URL the
// <track> element can consume. WebVTT differs from SRT only by header
// and a `.` → `,` swap for milliseconds.
export function srtToVtt(srt: string): string {
  const body = srt
    .replace(/\r+/g, "")
    .replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, "$1.$2");
  return `WEBVTT\n\n${body.trim()}\n`;
}

export function subtitleToBlobUrl(text: string, kind: "srt" | "vtt"): string {
  const vtt = kind === "srt" ? srtToVtt(text) : text;
  const blob = new Blob([vtt], { type: "text/vtt" });
  return URL.createObjectURL(blob);
}
