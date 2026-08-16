import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../state";
import { ApiError } from "../api/client";
import { Button } from "../components/ui";
import { DumbbellIcon, SparkleIcon } from "../components/Icons";

type Mode = "login" | "signup" | "claim";

function AuthLogo() {
  return (
    <div className="auth-logo">
      <span className="logo-icon">
        <DumbbellIcon size={18} />
      </span>
      <span className="logo-text">GYMLET</span>
    </div>
  );
}

export function AuthScreen() {
  const { legacyUsername, login, register, claim } = useAuth();
  const initial: Mode = legacyUsername ? "claim" : "login";
  const [mode, setMode] = useState<Mode>(initial);
  const [username, setUsername] = useState("");
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A legacy account may be discovered after the first render — switch to the
  // one-time claim flow once we know it exists.
  useEffect(() => {
    if (legacyUsername && mode === "login") {
      setMode("claim");
      setError(null);
    }
  }, [legacyUsername, mode]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const u = username.trim();
    if (!u) {
      setError("Enter your username");
      return;
    }
    if (!/^\d{4}$/.test(pin)) {
      setError("PIN must contain exactly 4 digits");
      return;
    }
    setError(null);
    setBusy(true);
    try {
      if (mode === "signup") await register(u, pin);
      else if (mode === "claim") await claim(u, pin);
      else await login(u, pin);
    } catch (err) {
      if (err instanceof ApiError && err.status === 0) {
        setError("Can't reach the Gymlet server. Check your connection and try again.");
      } else {
        setError(err instanceof ApiError ? err.message : "Something went wrong");
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-screen">
      <div className="auth-card-wrap">
        <AuthLogo />

        <div className="auth-card">
          {mode === "claim" && (
            <div className="claim-banner">
              <SparkleIcon size={15} />
              <span>
                Existing account found — set a PIN for <strong>“{legacyUsername}”</strong> to keep your data.
              </span>
            </div>
          )}

          <h1 className="auth-title">
            {mode === "login" && "Welcome back 👋"}
            {mode === "signup" && "Create your account"}
            {mode === "claim" && "Claim your account"}
          </h1>
          <p className="auth-sub">
            {mode === "login" && "Quick log in — then straight to today's workout."}
            {mode === "signup" && "Pick a username and a 4-digit PIN."}
            {mode === "claim" && "One-time setup. Your workouts stay exactly as they are."}
          </p>

          <form className="auth-form" onSubmit={(e) => void submit(e)}>
            <label className="field">
              <span className="field-label">Username</span>
              <input
                type="text"
                autoComplete="username"
                value={username}
                maxLength={30}
                autoFocus
                placeholder="e.g. harsh"
                onChange={(e) => setUsername(e.target.value)}
              />
            </label>

            <label className="field">
              <span className="field-label">4-digit PIN</span>
              <input
                type="password"
                inputMode="numeric"
                autoComplete="current-password"
                value={pin}
                maxLength={4}
                placeholder="••••"
                onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
              />
            </label>

            {error && <p className="auth-error">{error}</p>}

            <Button size="lg" type="submit" disabled={busy}>
              {busy
                ? "One moment…"
                : mode === "login"
                  ? "Login"
                  : mode === "signup"
                    ? "Create account"
                    : "Claim & enter"}
            </Button>
          </form>

          <div className="auth-switch">
            {mode === "login" ? (
              <>
                <span>New to Gymlet?</span>
                <button type="button" className="auth-link" onClick={() => { setMode("signup"); setError(null); }}>
                  Create account
                </button>
              </>
            ) : (
              <button
                type="button"
                className="auth-link"
                onClick={() => { setMode(legacyUsername ? "claim" : "login"); setError(null); }}
              >
                ← Back to login
              </button>
            )}
          </div>
        </div>

        <p className="auth-foot">My cute gym companion · your data stays yours</p>
      </div>
    </div>
  );
}
