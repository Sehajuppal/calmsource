import { useEffect, useRef, useState } from "react";
import { Lock, X } from "lucide-react";

export function PinDialog({
  title = "Enter PIN",
  description = "This profile is locked. Enter the 4-digit PIN to continue.",
  expected,
  onClose,
  onSuccess,
}: {
  title?: string;
  description?: string;
  expected: string;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (pin === expected) {
      onSuccess();
    } else {
      setError("Incorrect PIN");
      setPin("");
    }
  };

  return (
    <div
      className="fixed inset-0 z-[200] grid place-items-center bg-black/75 p-4 backdrop-blur-md animate-in fade-in duration-200"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <form
        onSubmit={submit}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-sm rounded-3xl glass-strong p-7 shadow-2xl animate-in zoom-in-95 duration-200"
      >
        <div className="flex items-start justify-between">
          <div className="grid h-11 w-11 place-items-center rounded-2xl bg-white/10">
            <Lock className="h-5 w-5" />
          </div>
          <button type="button" onClick={onClose} aria-label="Close" className="grid h-8 w-8 place-items-center rounded-full text-muted-foreground hover:bg-white/10">
            <X className="h-4 w-4" />
          </button>
        </div>
        <h3 className="mt-4 font-display text-2xl font-semibold tracking-tight">{title}</h3>
        <p className="mt-1 text-[13.5px] font-light text-muted-foreground">{description}</p>
        <input
          ref={inputRef}
          type="password"
          inputMode="numeric"
          autoComplete="off"
          pattern="[0-9]{4}"
          maxLength={4}
          value={pin}
          onChange={(e) => { setPin(e.target.value.replace(/\D/g, "").slice(0, 4)); setError(null); }}
          aria-label="PIN"
          className="mt-5 w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-center font-mono text-2xl tracking-[0.6em] focus:border-white/30 focus:outline-none"
          placeholder="••••"
        />
        {error && <p className="mt-2 text-center text-[12px] text-red-300">{error}</p>}
        <button
          type="submit"
          disabled={pin.length !== 4}
          className="mt-5 w-full rounded-full bg-foreground py-3 text-[13px] font-semibold text-background transition hover:bg-white disabled:opacity-40"
        >
          Unlock
        </button>
      </form>
    </div>
  );
}
