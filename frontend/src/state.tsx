import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, clearToken, getToken, setToken, setUnauthorizedHandler } from "./api/client";
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
}

const AuthContext = createContext<AuthState>({
  status: "loading",
  user: null,
  legacyUsername: null,
  login: async () => {},
  register: async () => {},
  claim: async () => {},
  logout: async () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthState["status"]>("loading");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [legacyUsername, setLegacyUsername] = useState<string | null>(null);

  const goOut = useCallback(() => {
    setUser(null);
    setStatus("out");
    void api
      .authStatus()
      .then((s) => setLegacyUsername(s.legacyUsername))
      .catch(() => setLegacyUsername(null));
  }, []);

  // Session persists in localStorage; verify it against the backend on load.
  useEffect(() => {
    let cancelled = false;
    const token = getToken();
    if (!token) {
      setStatus("out");
      void api
        .authStatus()
        .then((s) => !cancelled && setLegacyUsername(s.legacyUsername))
        .catch(() => {});
      return;
    }
    api
      .me()
      .then((u) => {
        if (cancelled) return;
        setUser(u);
        setStatus("in");
      })
      .catch(() => {
        if (cancelled) return;
        clearToken();
        setUser(null);
        setStatus("out");
        void api
          .authStatus()
          .then((s) => !cancelled && setLegacyUsername(s.legacyUsername))
          .catch(() => {});
      });
    return () => {
      cancelled = true;
    };
  }, []);

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
    <AuthContext.Provider value={{ status, user, legacyUsername, login, register, claim, logout }}>
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
