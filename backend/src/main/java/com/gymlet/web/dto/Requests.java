package com.gymlet.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for profile / structure editing. */
public final class Requests {

    private Requests() {
    }

    public record ProfileRequest(@NotBlank @Size(max = 40) String name,
                                 @NotNull String unit,
                                 @Min(1) @Max(7) Integer startDay) {
    }

    public record ExerciseRequest(@NotBlank @Size(max = 60) String name,
                                  @NotBlank String muscleGroup,
                                  @NotNull @Min(1) Integer repMin,
                                  @NotNull @Min(1) Integer repMax,
                                  Boolean compound) {
    }

    public record AddExerciseToDayRequest(@NotNull Long exerciseId,
                                          @NotNull @Min(1) Integer sets,
                                          @Min(0) Integer setOrder) {
    }

    public record UpdateExerciseInDayRequest(@Min(1) Integer sets,
                                             @Min(0) Integer setOrder) {
    }

    public record BodyWeightRequest(@NotNull String date,
                                    @NotNull Double weightKg) {
    }
}
