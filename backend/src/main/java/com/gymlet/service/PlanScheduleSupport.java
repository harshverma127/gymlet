package com.gymlet.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.repository.WorkoutDayRepository;

/**
 * Resolves which workout (if any) is scheduled on a calendar weekday for a plan,
 * and applies the non-destructive 5-slot → 7-day schedule backfill.
 */
@Component
public class PlanScheduleSupport {

    /** day_number values reserved for rest-only rows in the 7-day schedule (51–57). */
    public static final int REST_DAY_NUMBER_BASE = 50;

    /** Legacy custom-workout pseudo day. */
    public static final int CUSTOM_DAY_NUMBER = 6;

    private final WorkoutDayRepository workoutDayRepository;

    public PlanScheduleSupport(WorkoutDayRepository workoutDayRepository) {
        this.workoutDayRepository = workoutDayRepository;
    }

    /**
     * Weekday (1 = Mon … 7 = Sun) for a legacy training slot {@code dayNumber} (1–5)
     * given the user's week anchor {@code startDay}.
     */
    public static int weekdayForLegacySlot(int startDay, int dayNumber) {
        if (dayNumber < 1 || dayNumber > 5) {
            throw new IllegalArgumentException("Legacy slot must be 1–5");
        }
        return ((startDay - 1 + (dayNumber - 1)) % 7) + 1;
    }

    public static int restDayNumberForWeekday(int weekday) {
        return REST_DAY_NUMBER_BASE + weekday;
    }

    /**
     * Scheduled workout for {@code weekday} on {@code planId}, or empty if rest / unassigned.
     * Falls back to the legacy startDay + day_number mapping when weekday columns are not set yet.
     */
    public Optional<WorkoutDay> scheduledWorkout(Long userId, Long planId, int weekday, int startDay) {
        validateWeekday(weekday);
        Optional<WorkoutDay> row = workoutDayRepository.findByUserIdAndPlanIdAndWeekday(userId, planId, weekday);
        if (row.isPresent()) {
            WorkoutDay d = row.get();
            if (d.isRestDay()) {
                return Optional.empty();
            }
            return Optional.of(d);
        }
        int legacySlot = legacySlotForWeekday(startDay, weekday);
        if (legacySlot < 1 || legacySlot > 5) {
            return Optional.empty();
        }
        return workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(userId, planId, legacySlot)
                .filter(d -> !d.isRestDay());
    }

    /** Assigns an existing plan workout to one weekday without touching its exercises or history. */
    public WorkoutDay assignWorkoutDay(Long userId, Long planId, int weekday, Long workoutDayId) {
        validateWeekday(weekday);
        WorkoutDay day = workoutDayRepository.findByIdAndUserId(workoutDayId, userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Workout day not found"));
        if (!planId.equals(day.getPlanId())) {
            throw new java.util.NoSuchElementException("Workout day not found");
        }
        if (day.isRestDay()) {
            throw new IllegalArgumentException("Rest days cannot be assigned as workouts");
        }
        if (day.getDayNumber() != null && day.getDayNumber() == CUSTOM_DAY_NUMBER) {
            throw new IllegalArgumentException("Custom workouts cannot be scheduled");
        }

        workoutDayRepository.findByUserIdAndPlanIdAndWeekday(userId, planId, weekday)
                .filter(existing -> !java.util.Objects.equals(existing.getId(), day.getId()))
                .ifPresent(existing -> {
                    existing.setWeekday(null);
                    workoutDayRepository.save(existing);
                });
        day.setWeekday(weekday);
        day.setRestDay(false);
        return workoutDayRepository.save(day);
    }

    /** Creates or restores the reserved rest row for one weekday. */
    public WorkoutDay setRestDay(Long userId, Long planId, int weekday) {
        validateWeekday(weekday);
        workoutDayRepository.findByUserIdAndPlanIdAndWeekday(userId, planId, weekday)
                .filter(day -> !day.isRestDay())
                .ifPresent(day -> {
                    day.setWeekday(null);
                    workoutDayRepository.save(day);
                });

        WorkoutDay rest = workoutDayRepository
                .findByUserIdAndPlanIdAndDayNumber(userId, planId, restDayNumberForWeekday(weekday))
                .orElseGet(() -> {
                    WorkoutDay created = new WorkoutDay();
                    created.setUserId(userId);
                    created.setPlanId(planId);
                    created.setDayNumber(restDayNumberForWeekday(weekday));
                    return created;
                });
        rest.setName("Rest");
        rest.setWeekday(weekday);
        rest.setRestDay(true);
        return workoutDayRepository.save(rest);
    }

    private void validateWeekday(int weekday) {
        if (weekday < 1 || weekday > 7) {
            throw new IllegalArgumentException("Weekday must be between 1 and 7");
        }
    }

    /** Inverse of {@link #weekdayForLegacySlot}. Returns 0 if that weekday was a rest day in the old model. */
    public static int legacySlotForWeekday(int startDay, int weekday) {
        int idx = (weekday - startDay + 7) % 7;
        if (idx >= 5) {
            return 0;
        }
        return idx + 1;
    }

    /**
     * Idempotent backfill: attach weekdays to existing training days and insert rest rows
     * for weekdays not covered. Never deletes or rewrites sessions.
     */
    public void ensureSevenDaySchedule(AppUser user, Long planId) {
        if (planId == null) {
            return;
        }
        Long userId = user.getId();
        int startDay = user.getStartDay() != null ? user.getStartDay() : 1;
        List<WorkoutDay> inPlan = workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(userId, planId);
        Set<Integer> weekdaysTaken = new HashSet<>();

        for (WorkoutDay day : inPlan) {
            if (day.getDayNumber() != null && day.getDayNumber() == CUSTOM_DAY_NUMBER) {
                continue;
            }
            if (day.isRestDay()) {
                if (day.getWeekday() != null) {
                    weekdaysTaken.add(day.getWeekday());
                }
                continue;
            }
            if (day.getDayNumber() != null && day.getDayNumber() >= 1 && day.getDayNumber() <= 5) {
                if (day.getWeekday() == null) {
                    int legacyWeekday = weekdayForLegacySlot(startDay, day.getDayNumber());
                    if (workoutDayRepository.findByUserIdAndPlanIdAndWeekday(userId, planId, legacyWeekday)
                            .isEmpty()) {
                        day.setWeekday(legacyWeekday);
                        workoutDayRepository.save(day);
                    }
                }
                if (day.getWeekday() != null) {
                    weekdaysTaken.add(day.getWeekday());
                }
            }
        }

        for (int weekday = 1; weekday <= 7; weekday++) {
            if (weekdaysTaken.contains(weekday)) {
                continue;
            }
            if (workoutDayRepository.findByUserIdAndPlanIdAndWeekday(userId, planId, weekday).isPresent()) {
                continue;
            }
            WorkoutDay rest = new WorkoutDay();
            rest.setUserId(userId);
            rest.setPlanId(planId);
            rest.setWeekday(weekday);
            rest.setRestDay(true);
            rest.setName("Rest");
            rest.setDayNumber(restDayNumberForWeekday(weekday));
            workoutDayRepository.save(rest);
        }
    }

    /** Training workouts available for the day picker (non-rest, with legacy slots or assigned weekday). */
    public List<WorkoutDay> selectableTrainingDays(Long userId, Long planId) {
        return workoutDayRepository.findAllByUserIdAndPlanIdAndRestDayFalseOrderByWeekdayAscDayNumberAsc(userId, planId)
                .stream()
                .filter(d -> d.getDayNumber() == null || d.getDayNumber() != CUSTOM_DAY_NUMBER)
                .filter(d -> d.getDayNumber() == null || d.getDayNumber() < REST_DAY_NUMBER_BASE)
                .toList();
    }
}
