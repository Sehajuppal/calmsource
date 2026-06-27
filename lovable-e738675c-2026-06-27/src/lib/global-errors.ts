// Global window-level error handlers — captures errors that bypass
// React's tree-walking ErrorBoundary (event listeners, async callbacks,
// rejected promises from server-fn calls, etc.).

import { reportLovableError } from "./lovable-error-reporting";

let installed = false;

export function installGlobalErrorHandlers() {
  if (installed || typeof window === "undefined") return;
  installed = true;

  window.addEventListener("error", (event) => {
    const error = event.error instanceof Error ? event.error : new Error(String(event.message ?? "Unknown error"));
    console.error("[global:error]", error);
    try {
      reportLovableError(error, { boundary: "window_error", source: event.filename ?? undefined });
    } catch { /* ignore */ }
  });

  window.addEventListener("unhandledrejection", (event) => {
    const reason = event.reason;
    const error = reason instanceof Error ? reason : new Error(typeof reason === "string" ? reason : "Unhandled promise rejection");
    console.error("[global:unhandledrejection]", error, reason);
    try {
      reportLovableError(error, { boundary: "unhandled_rejection" });
    } catch { /* ignore */ }
  });
}
