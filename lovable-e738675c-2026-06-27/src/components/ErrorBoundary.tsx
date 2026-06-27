import { Component, type ErrorInfo, type ReactNode } from "react";
import { reportLovableError } from "../lib/lovable-error-reporting";

type Props = {
  children: ReactNode;
  fallback?: ReactNode;
  label?: string;
  onReset?: () => void;
};

type State = { error: Error | null };

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[ErrorBoundary]", this.props.label ?? "", error, info);
    try {
      reportLovableError(error, {
        boundary: this.props.label ?? "react_error_boundary",
        componentStack: info.componentStack ?? undefined,
      });
    } catch {
      /* ignore */
    }
  }

  reset = () => {
    this.props.onReset?.();
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      if (this.props.fallback) return this.props.fallback;
      return (
        <div
          role="alert"
          className="mx-auto my-12 max-w-md rounded-2xl border border-white/10 bg-card/60 p-6 text-center backdrop-blur-md"
        >
          <h2 className="font-display text-lg font-semibold tracking-tight text-foreground">
            Something went wrong
          </h2>
          <p className="mt-1.5 text-sm text-muted-foreground">
            This section couldn't load. You can try again.
          </p>
          <button
            type="button"
            onClick={this.reset}
            className="mt-4 inline-flex items-center justify-center rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-background transition hover:bg-foreground/90"
          >
            Try again
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
