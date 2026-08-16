import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { ApiError, api, clearToken, getToken, markConnectionUnreachable, setToken, setUnauthorizedHandler } from "./api/client";
import type { AuthUser, Profile, Unit } from "./types";

/* ------------------------------ auth state ------------------------------ */

interface AuthState {
  status: "loading" | "out" | "in";
  user: AuthUser | null;
  /** A pre-auth legacy account waiting to be claimed (existing data from before multi-user). */
  legacyUsername: string | null;
  login: (username: string, pin: string) => Promise<void>;
  register: (username: string, pin: string) => Promise<void>;
  claim: (username: string, pin: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Re-runs the startup session check (used by the fallback "Retry now" button). */
  retryBootstrap: () => void;
}

const AuthContext = createContext<AuthState>({
  status: "loading",
  user: null,
  legacyUsername: null,
  login: async () => {},
  register: async () => {},
  claim: async () => {},
  logout: async () => {},
  retryBootstrap: () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthState["status"]>("loading");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [legacyUsername, setLegacyUsername] = useState<string | null>(null);
  const [bootstrapTick, setBootstrapTick] = useState(0);

  const goOut = useCallback(() => {
    setUser(null);
    setStatus("out");
    void api
      .authStatus()
      .then((s) => setLegacyUsername(s.legacyUsername))
      .catch(() => setLegacyUsername(null));
  }, []);

  const retryBootstrap = useCallback(() => {
    setBootstrapTick((t) => t + 1);
  }, []);

  // Session persists in localStorage; verify it against the backend on load.
  // A temporarily unreachable backend must NOT log the user out — only a real
  // 401 response means the session is gone. Network failures keep the app in
  // the "loading" state, where the waking/fallback card is shown instead.
  useEffect(() => {
    let cancelled = false;
    const token = getToken();
    if (!token) {
      void api
        .authStatus()
        .then((s) => {
          if (cancelled) return;
          setLegacyUsername(s.legacyUsername);
          setStatus("out");
        })
        .catch(() => {
          if (cancelled) return;
          // Backend unreachable — keep loading so the waking card shows.
          markConnectionUnreachable();
        });
      return;
    }
    api
      .me()
      .then((u) => {
        if (cancelled) return;
        setUser(u);
        setStatus("in");
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) {
          // The backend explicitly rejected the session.
          clearToken();
          setUser(null);
          setStatus("out");
          void api
            .authStatus()
            .then((s) => !cancelled && setLegacyUsername(s.legacyUsername))
            .catch(() => {});
        } else {
          // Unreachable or transient error — do NOT log out or clear the token.
          markConnectionUnreachable();
        }
      });
    return () => {
      cancelled = true;
    };
  }, [bootstrapTick]);

  // Any 401 anywhere in the app means the session is gone -> back to login.
  useEffect(() => {
    setUnauthorizedHandler(goOut);
    return () => setUnauthorizedHandler(null);
  }, [goOut]);

  const enter = useCallback((token: string, u: AuthUser) => {
    setToken(token);
    setUser(u);
    setLegacyUsername(null);
    setStatus("in");
  }, []);

  const login = useCallback(
    async (username: string, pin: string) => {
      const res = await api.login(username, pin);
      enter(res.token, { username: res.username, name: res.name });
    },
    [enter],
  );

  const register = useCallback(
    async (username: string, pin: string) => {
      const res = await api.register(username, pin);
      enter(res.token, { username: res.username, name: res.name });
    },
    [enter],
  );

  const claim = useCallback(
    async (username: string, pin: string) => {
      const res = await api.claim(username, pin);
      enter(res.token, { username: res.username, name: res.name });
    },
    [enter],
  );

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      /* session may already be gone */
    }
    clearToken();
    setUser(null);
    setStatus("out");
    void api
      .authStatus()
      .then((s) => setLegacyUsername(s.legacyUsername))
      .catch(() => {});
  }, []);

  return (
    <AuthContext.Provider
      value={{ status, user, legacyUsername, login, register, claim, logout, retryBootstrap }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  return useContext(AuthContext);
}

/* ----------------------------- profile state ----------------------------- */

interface ProfileState {
  profile: Profile | null;
  unit: Unit;
  reloadProfile: () => Promise<void>;
  saveProfile: (p: Profile) => Promise<void>;
}

const ProfileContext = createContext<ProfileState>({
  profile: null,
  unit: "KG",
  reloadProfile: async () => {},
  saveProfile: async () => {},
});

export function ProfileProvider({ children }: { children: ReactNode }) {
  const [profile, setProfile] = useState<Profile | null>(null);

  const reloadProfile = useCallback(async () => {
    try {
      setProfile(await api.profile());
    } catch {
      /* the app shows a connection error elsewhere */
    }
  }, []);

  useEffect(() => {
    void reloadProfile();
  }, [reloadProfile]);

  const saveProfile = useCallback(async (p: Profile) => {
    setProfile(await api.updateProfile(p));
  }, []);

  return (
    <ProfileContext.Provider
      value={{ profile, unit: profile?.unit ?? "KG", reloadProfile, saveProfile }}
    >
      {children}
    </ProfileContext.Provider>
  );
}

export function useProfile(): ProfileState {
  return useContext(ProfileContext);
}
