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

/* ------------------------------------------------------------------ */
/* Cold-start handling                                                 */
/*                                                                     */
/* Render's free tier puts the backend to sleep after inactivity. When */
/* a request fails at the network level (or the proxy answers with a   */
/* 502/503/504 while an instance boots) we assume the backend is just  */
/* waking up and retry automatically instead of surfacing an error.    */
/*                                                                     */
/* Only ONE probe loop runs at a time: every request that hits a cold  */
/* start awaits the same promise, so parallel startup requests don't   */
/* multiply the retry traffic. Real responses (400/401/404/500 …) are  */
/* never retried — they are surfaced to the app as before.             */
/* ------------------------------------------------------------------ */

export type ConnectionStatus = "ok" | "waking" | "unreachable";

let connectionStatus: ConnectionStatus = "ok";
const connectionListeners = new Set<(status: ConnectionStatus) => void>();

function setConnectionStatus(status: ConnectionStatus): void {
  if (connectionStatus === status) return;
  connectionStatus = status;
  connectionListeners.forEach((fn) => fn(status));
}

export function getConnectionStatus(): ConnectionStatus {
  return connectionStatus;
}

/** Force the "unreachable" state (used when a startup request fails without a network error). */
export function markConnectionUnreachable(): void {
  setConnectionStatus("unreachable");
}

/** Subscribe to cold-start state changes ("waking" / "unreachable"). */
export function onConnectionChange(fn: (status: ConnectionStatus) => void): () => void {
  connectionListeners.add(fn);
  return () => connectionListeners.delete(fn);
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** A friendly, non-technical message used when we gave up waiting for the backend. */
export const CONNECTION_FALLBACK_MESSAGE =
  "Gymlet is taking a little longer than usual. We're still trying to connect.";

const REQUEST_TIMEOUT_MS = 15_000;
const PROBE_TIMEOUT_MS = 8_000;
// Backoff: ~2s, ~3s, then every ~5s. Long enough for Render to boot, calm enough to not spam.
const WAKE_DELAYS = [2_000, 3_000, 5_000];
const WAKE_LIMIT_MS = 90_000;
// A public, lightweight endpoint used to probe whether the backend is up.
const WAKE_PROBE = "/api/auth/status";

let wakePromise: Promise<void> | null = null;

function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  return fetch(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
}

/** True when the failure looks like an unreachable/sleeping backend, not an app error. */
function isNetworkFailure(err: unknown): boolean {
  return (
    err instanceof TypeError ||
    (typeof DOMException !== "undefined" &&
      err instanceof DOMException &&
      (err.name === "TimeoutError" || err.name === "AbortError"))
  );
}

/** Gateway responses the Render proxy can return while an instance is booting. */
function isGatewayUnavailable(status: number): boolean {
  return status === 502 || status === 503 || status === 504;
}

/**
 * Waits until the backend answers, firing a single shared probe loop with
 * backoff. Resolves when the backend is reachable (or after ~90s, in which
 * case the caller surfaces the fallback message).
 */
function waitForBackend(): Promise<void> {
  if (!wakePromise) {
    wakePromise = (async () => {
      setConnectionStatus("waking");
      const start = Date.now();
      let attempt = 0;
      let awake = false;
      while (!awake) {
        try {
          await fetchWithTimeout(`${API_BASE_URL}${WAKE_PROBE}`, { cache: "no-store" }, PROBE_TIMEOUT_MS);
          awake = true;
        } catch {
          if (Date.now() - start >= WAKE_LIMIT_MS) break;
          await sleep(WAKE_DELAYS[Math.min(attempt, WAKE_DELAYS.length - 1)]);
          attempt++;
        }
      }
      setConnectionStatus(awake ? "ok" : "unreachable");
      wakePromise = null;
    })();
  }
  return wakePromise;
}

/**
 * One fetch attempt that understands cold starts: on a network failure (or a
 * gateway response) it waits for the shared wake probe, then retries once.
 */
async function fetchWithWakeRetry(url: string, init: RequestInit): Promise<Response> {
  try {
    const res = await fetchWithTimeout(url, init, REQUEST_TIMEOUT_MS);
    if (isGatewayUnavailable(res.status)) {
      // Backend is booting — join the wake probe and try once more.
      await waitForBackend();
      return await fetchWithTimeout(url, init, REQUEST_TIMEOUT_MS);
    }
    return res;
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    await waitForBackend();
    // If the backend is still down this throws again and request() surfaces the fallback.
    return await fetchWithTimeout(url, init, REQUEST_TIMEOUT_MS);
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetchWithWakeRetry(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // Still unreachable after the wake retry — a real, current error, not a hidden bug.
    throw new ApiError(0, CONNECTION_FALLBACK_MESSAGE);
  }

  // Any HTTP response means the backend is up — leave the cold-start state.
  setConnectionStatus("ok");

  if (!res.ok) {
    if (isGatewayUnavailable(res.status)) {
      throw new ApiError(0, CONNECTION_FALLBACK_MESSAGE);
    }

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
