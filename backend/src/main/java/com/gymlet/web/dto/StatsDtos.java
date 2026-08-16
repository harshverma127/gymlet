package com.gymlet.web.dto;

import java.util.List;

/** DTOs for the analytics endpoints. */
public final class StatsDtos {

    private StatsDtos() {
    }

    public record StrengthPointDto(String date, Double topWeight, Integer bestReps, Double est1Rm,
                                   Double volume, Integer setsCompleted) {
    }

    public record BestSetDto(String date, Double weight, Integer reps, Double est1Rm) {
    }

    public record ExerciseStrengthDto(Long exerciseId, String name, String muscleGroup,
                                      List<StrengthPointDto> sessions, BestSetDto bestSet,
                                      Double best1Rm, Double totalVolume) {
    }

    public record MuscleDto(String muscleGroup, Integer weeklySets, Double weeklyVolume,
                            Integer frequency) {
    }

    public record ExercisePrDto(String name, Double highestWeight, Integer bestReps, Double best1Rm) {
    }

    public record PrSummaryDto(List<ExercisePrDto> exercises, Double bestSessionVolume,
                               String bestSessionVolumeDate, Long totalWorkouts,
                               Integer currentStreak, Integer bestStreak) {
    }

    public record CalendarDayDto(String date, String status, Integer dayNumber, Long sessionId,
                                 String workoutDayName) {
        // status: WORKOUT | MISSED | REST | FUTURE
    }

    public record BodyWeightDto(Long id, String date, Double weightKg) {
    }

    public record BodyWeightSummaryDto(Double current, Double weeklyAverage, Double changeThisWeek,
                                       List<BodyWeightDto> history) {
    }

    public record ProfileDto(String name, String unit, Integer startDay) {
    }
}
