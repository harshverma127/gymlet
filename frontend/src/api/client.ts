import type {
  AuthStatus,
  AuthUser,
  BodyWeightEntry,
  BodyWeightSummary,
  CalendarDay,
  Exercise,
  ExerciseStrength,
  FinishSummary,
  HistoryItem,
  LoginResponse,
  MuscleVolume,
  PrSummary,
  Profile,
  Session,
  Today,
  WorkoutDay,
} from "../types";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// Backend deployed on Render. Override locally with VITE_API_URL (e.g. http://localhost:8080)
// in .env.local — the value is embedded at build time.
const API_BASE_URL = import.meta.env.VITE_API_URL ?? "https://gymlet.onrender.com";

const TOKEN_KEY = "gymlet.token";

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    /* storage unavailable — session only works in memory */
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
}

/** Called whenever the backend rejects the session (401) so the app can go back to login. */
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(fn: (() => void) | null): void {
  onUnauthorized = fn;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response;

  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;

  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "Can't reach the Gymlet server. Is it running?");
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;

    try {
      const data = await res.json();
      if (data && typeof data.error === "string") {
        message = data.error;
      }
    } catch {
      /* keep default */
    }

    if (res.status === 401) {
      clearToken();
      onUnauthorized?.();
    }

    throw new ApiError(res.status, message);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  return (await res.json()) as T;
}

export const api = {
  // auth
  register: (username: string, pin: string) =>
    request<LoginResponse>("POST", "/api/auth/register", { username, pin }),

  login: (username: string, pin: string) =>
    request<LoginResponse>("POST", "/api/auth/login", { username, pin }),

  claim: (username: string, pin: string) =>
    request<LoginResponse>("POST", "/api/auth/claim", { username, pin }),

  logout: () =>
    request<{ ok: string }>("POST", "/api/auth/logout"),

  me: () =>
    request<AuthUser>("GET", "/api/auth/me"),

  authStatus: () =>
    request<AuthStatus>("GET", "/api/auth/status"),

  // today + structure
  today: () => request<Today>("GET", "/api/today"),

  workoutDays: () =>
    request<WorkoutDay[]>("GET", "/api/workout-days"),

  workoutDay: (id: number) =>
    request<WorkoutDay>("GET", `/api/workout-days/${id}`),

  exercises: () =>
    request<Exercise[]>("GET", "/api/exercises"),

  createExercise: (body: Omit<Exercise, "id">) =>
    request<Exercise>("POST", "/api/exercises", body),

  updateExercise: (id: number, body: Omit<Exercise, "id">) =>
    request<Exercise>("PUT", `/api/exercises/${id}`, body),

  deleteExercise: (id: number) =>
    request<void>("DELETE", `/api/exercises/${id}`),

  addExerciseToDay: (dayId: number, exerciseId: number, sets: number) =>
    request<WorkoutDay>(
      "POST",
      `/api/workout-days/${dayId}/exercises`,
      { exerciseId, sets }
    ),

  updateExerciseInDay: (id: number, sets?: number, setOrder?: number) =>
    request<WorkoutDay>(
      "PUT",
      `/api/workout-exercises/${id}`,
      { sets, setOrder }
    ),

  removeExerciseFromDay: (id: number) =>
    request<WorkoutDay>(
      "DELETE",
      `/api/workout-exercises/${id}`
    ),

  // sessions
  startSession: () =>
    request<Session>("POST", "/api/sessions"),

  session: (id: number) =>
    request<Session>("GET", `/api/sessions/${id}`),

  history: () =>
    request<HistoryItem[]>("GET", "/api/sessions"),

  updateSet: (
    sessionId: number,
    setId: number,
    body: {
      weight: number | null;
      reps: number | null;
      rir: number | null;
      completed: boolean;
    }
  ) =>
    request<Session>(
      "PUT",
      `/api/sessions/${sessionId}/sets/${setId}`,
      body
    ),

  saveNote: (
    sessionId: number,
    exerciseId: number,
    note: string
  ) =>
    request<Session>(
      "POST",
      `/api/sessions/${sessionId}/notes/${exerciseId}`,
      { note }
    ),

  finishSession: (
    sessionId: number,
    durationMinutes?: number
  ) =>
    request<FinishSummary>(
      "POST",
      `/api/sessions/${sessionId}/finish`,
      { durationMinutes }
    ),

  deleteSession: (id: number) =>
    request<void>("DELETE", `/api/sessions/${id}`),

  // stats
  strength: () =>
    request<ExerciseStrength[]>("GET", "/api/stats/strength"),

  muscles: () =>
    request<MuscleVolume[]>("GET", "/api/stats/muscles"),

  prs: () =>
    request<PrSummary>("GET", "/api/stats/prs"),

  calendar: (year: number, month: number) =>
    request<CalendarDay[]>(
      "GET",
      `/api/stats/calendar?year=${year}&month=${month}`
    ),

  // bodyweight
  bodyWeight: () =>
    request<BodyWeightEntry[]>("GET", "/api/bodyweight"),

  bodyWeightSummary: () =>
    request<BodyWeightSummary>(
      "GET",
      "/api/bodyweight/summary"
    ),

  addBodyWeight: (date: string, weightKg: number) =>
    request<BodyWeightEntry>(
      "POST",
      "/api/bodyweight",
      { date, weightKg }
    ),

  deleteBodyWeight: (id: number) =>
    request<void>("DELETE", `/api/bodyweight/${id}`),

  // profile + data
  profile: () =>
    request<Profile>("GET", "/api/profile"),

  updateProfile: (body: Profile) =>
    request<Profile>("PUT", "/api/profile", body),

  resetData: () =>
    request<{ ok: string }>("POST", "/api/data/reset"),

  removeDemo: () =>
    request<{ ok: string }>("POST", "/api/demo/remove"),

  exportData: () =>
    request<Record<string, unknown>>("GET", "/api/export"),
};
