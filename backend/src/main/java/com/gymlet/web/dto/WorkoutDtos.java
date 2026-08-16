package com.gymlet.web.dto;

import java.util.List;

/** DTOs describing the workout split structure. */
public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public record ExerciseDto(Long id, String name, String muscleGroup, Integer repMin, Integer repMax,
                              boolean compound) {
    }

    public record WorkoutExerciseDto(Long id, Long exerciseId, String name, String muscleGroup,
                                     Integer repMin, Integer repMax, boolean compound,
                                     Integer sets, Integer setOrder) {
    }

    public record WorkoutDayDto(Long id, Integer dayNumber, String name,
                                List<WorkoutExerciseDto> exercises) {
    }

    /** Suggestion for the next session, computed from the previous completed session. */
    public record SuggestionDto(String action, Double weight, String message) {
        // action: INCREASE | KEEP
    }

    /** A set as it appeared last time this exercise was performed. */
    public record LastSetDto(Double weight, Integer reps) {
    }

    /** One exercise inside the Today payload, with everything the logging UI needs. */
    public record TodayExerciseDto(Long exerciseId, String name, String muscleGroup, Integer repMin,
                                   Integer repMax, boolean compound, Integer sets,
                                   List<LastSetDto> lastSets, SuggestionDto suggestion, String lastNote) {
    }

    public record TodayDto(Boolean isRestDay, Integer dayNumber, Long workoutDayId, String workoutDayName,
                           List<TodayExerciseDto> exercises, Long activeSessionId, Boolean completed,
                           Long nextWorkoutDayId, String nextWorkoutDayName, Integer nextDayNumber) {
    }

    public record SetDto(Long id, Long exerciseId, String exerciseName, Integer setNumber,
                         Double weight, Integer reps, Integer rir, boolean completed) {
    }

    public record NoteDto(Long exerciseId, String note) {
    }

    public record SessionDto(Long id, String date, Long workoutDayId, String workoutDayName,
                             Integer durationMinutes, boolean completed, boolean demo,
                             List<SetDto> sets, List<NoteDto> notes,
                             Integer totalSets, Integer completedSets, Double volume) {
    }

    public record SetUpdateRequest(Double weight, Integer reps, Integer rir, Boolean completed) {
    }

    public record NoteRequest(String note) {
    }

    public record FinishRequest(Integer durationMinutes) {
    }

    public record PrDto(String exerciseName, String type, String label, String value) {
        // type: WEIGHT | REPS | ONERM | VOLUME
    }

    public record FinishSummaryDto(Long sessionId, Integer durationMinutes, Integer totalSets,
                                   Integer completedSets, Double totalVolume, Integer exercisesCompleted,
                                   List<PrDto> prs, String message) {
    }

    public record HistoryItemDto(Long id, String date, String workoutDayName, boolean completed,
                                 boolean demo, Integer setsCompleted, Integer durationMinutes,
                                 Integer exercisesCompleted, Double volume) {
    }
}
