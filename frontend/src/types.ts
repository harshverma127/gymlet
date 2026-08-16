export type Unit = "KG" | "LB";

export interface AuthUser {
  username: string;
  name: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  name: string;
}

export interface AuthStatus {
  legacyUsername: string | null;
}
export type MuscleGroup =
  | "CHEST"
  | "BACK"
  | "SHOULDERS"
  | "BICEPS"
  | "TRICEPS"
  | "FOREARMS"
  | "LEGS"
  | "ABS";

export interface Profile {
  name: string;
  unit: Unit;
  startDay: number; // 1 = Monday .. 7 = Sunday
}

export interface Exercise {
  id: number;
  name: string;
  muscleGroup: MuscleGroup;
  repMin: number;
  repMax: number;
  compound: boolean;
}

export interface WorkoutExercise {
  id: number;
  exerciseId: number;
  name: string;
  muscleGroup: MuscleGroup;
  repMin: number;
  repMax: number;
  compound: boolean;
  sets: number;
  setOrder: number;
}

export interface WorkoutDay {
  id: number;
  dayNumber: number;
  name: string;
  exercises: WorkoutExercise[];
}

export interface LastSet {
  weight: number;
  reps: number;
}

export interface Suggestion {
  action: "INCREASE" | "KEEP";
  weight: number;
  message: string;
}

export interface TodayExercise {
  exerciseId: number;
  name: string;
  muscleGroup: MuscleGroup;
  repMin: number;
  repMax: number;
  compound: boolean;
  sets: number;
  lastSets: LastSet[];
  suggestion: Suggestion | null;
  lastNote: string | null;
}

export interface Today {
  isRestDay: boolean;
  dayNumber: number;
  workoutDayId: number;
  workoutDayName: string;
  exercises: TodayExercise[];
  activeSessionId: number | null;
  completed: boolean;
  nextWorkoutDayId: number | null;
  nextWorkoutDayName: string | null;
  nextDayNumber: number | null;
}

export interface SetLog {
  id: number;
  exerciseId: number;
  exerciseName: string;
  setNumber: number;
  weight: number | null;
  reps: number | null;
  rir: number | null;
  completed: boolean;
}

export interface SessionNote {
  exerciseId: number;
  note: string;
}

export interface Session {
  id: number;
  date: string;
  workoutDayId: number;
  workoutDayName: string;
  durationMinutes: number | null;
  completed: boolean;
  demo: boolean;
  sets: SetLog[];
  notes: SessionNote[];
  totalSets: number;
  completedSets: number;
  volume: number;
}

export interface Pr {
  exerciseName: string | null;
  type: "WEIGHT" | "REPS" | "ONERM" | "VOLUME";
  label: string;
  value: string;
}

export interface FinishSummary {
  sessionId: number;
  durationMinutes: number;
  totalSets: number;
  completedSets: number;
  totalVolume: number;
  exercisesCompleted: number;
  prs: Pr[];
  message: string;
}

export interface HistoryItem {
  id: number;
  date: string;
  workoutDayName: string;
  completed: boolean;
  demo: boolean;
  setsCompleted: number;
  durationMinutes: number | null;
  exercisesCompleted: number;
  volume: number;
}

export interface StrengthPoint {
  date: string;
  topWeight: number;
  bestReps: number;
  est1Rm: number;
  volume: number;
  setsCompleted: number;
}

export interface BestSet {
  date: string;
  weight: number;
  reps: number;
  est1Rm: number;
}

export interface ExerciseStrength {
  exerciseId: number;
  name: string;
  muscleGroup: MuscleGroup;
  sessions: StrengthPoint[];
  bestSet: BestSet | null;
  best1Rm: number;
  totalVolume: number;
}

export interface MuscleVolume {
  muscleGroup: MuscleGroup;
  weeklySets: number;
  weeklyVolume: number;
  frequency: number;
}

export interface ExercisePr {
  name: string;
  highestWeight: number;
  bestReps: number;
  best1Rm: number;
}

export interface PrSummary {
  exercises: ExercisePr[];
  bestSessionVolume: number;
  bestSessionVolumeDate: string | null;
  totalWorkouts: number;
  currentStreak: number;
  bestStreak: number;
}

export type CalendarStatus = "WORKOUT" | "MISSED" | "REST" | "FUTURE";

export interface CalendarDay {
  date: string;
  status: CalendarStatus;
  dayNumber: number | null;
  sessionId: number | null;
  workoutDayName: string | null;
}

export interface BodyWeightEntry {
  id: number;
  date: string;
  weightKg: number;
}

export interface BodyWeightSummary {
  current: number | null;
  weeklyAverage: number | null;
  changeThisWeek: number | null;
  history: BodyWeightEntry[];
}
