import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "./api/client";
import type { Profile, Unit } from "./types";

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
