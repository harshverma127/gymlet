import { useEffect, useState } from "react";
import {
  getConnectionStatus,
  onConnectionChange,
  type ConnectionStatus,
} from "../api/client";
import { Button } from "./ui";

/** Live view of the client's cold-start state ("ok" | "waking" | "unreachable"). */
export function useConnection(): ConnectionStatus {
  const [status, setStatus] = useState<ConnectionStatus>(getConnectionStatus());
  useEffect(() => onConnectionChange(setStatus), []);
  return status;
}

/** Shown while the backend is waking up and requests are being retried. */
export function WakingCard() {
  return (
    <div className="wake-card">
      <div className="wake-art" aria-hidden="true">
        <span className="wake-leaf l1" />
        <span className="wake-leaf l2" />
        <span className="wake-stem" />
        <span className="wake-pot" />
      </div>
      <p className="wake-title">🌱 Waking Gymlet up…</p>
      <p className="wake-sub">Just a little moment — your gym companion is getting ready.</p>
    </div>
  );
}

/** Shown after a reasonable retry period — a gentle fallback, not an error dump. */
export function UnreachableCard({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="wake-card">
      <div className="wake-art" aria-hidden="true">
        <span className="wake-leaf l1" />
        <span className="wake-leaf l2" />
        <span className="wake-stem" />
        <span className="wake-pot" />
      </div>
      <p className="wake-title">Gymlet is taking a little longer than usual 🌱</p>
      <p className="wake-sub">We're still trying to connect.</p>
      <Button onClick={onRetry}>Retry now</Button>
    </div>
  );
}

/** Fixed overlay shown over the app whenever the backend is waking/unreachable. */
export function ConnectionOverlay({ onRetry }: { onRetry: () => void }) {
  const connection = useConnection();
  if (connection === "ok") return null;
  return (
    <div className="connection-overlay" role="status" aria-live="polite">
      {connection === "waking" ? (
        <WakingCard />
      ) : (
        <UnreachableCard onRetry={onRetry} />
      )}
    </div>
  );
}
