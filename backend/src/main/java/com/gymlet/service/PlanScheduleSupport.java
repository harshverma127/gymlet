package com.gymlet.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        List<WorkoutDay> rows = workoutDayRepository
                .findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(userId, planId, weekday);
        Optional<WorkoutDay> training = rows.stream().filter(d -> !d.isRestDay()).findFirst();
        if (training.isPresent()) {
            return training;
        }
        if (!rows.isEmpty()) {
            // Explicit rest row(s) for this weekday — no legacy fallback.
            return Optional.empty();
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

        for (WorkoutDay existing : workoutDayRepository
                .findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(userId, planId, weekday)) {
            if (!Objects.equals(existing.getId(), day.getId())) {
                existing.setWeekday(null);
                workoutDayRepository.save(existing);
            }
        }
        day.setWeekday(weekday);
        day.setRestDay(false);
        return workoutDayRepository.save(day);
    }

    /**
     * Turns one weekday into a real, editable training day (Rest → Workout day).
     *
     * - If the weekday already holds a training day, it is simply renamed.
     * - Otherwise the scheduled rest row (if any) is detached and left intact as
     *   an unscheduled rest row, and a brand-new training day is created for the
     *   weekday with the next free day_number (< {@link #REST_DAY_NUMBER_BASE}).
     *
     * Exercises and history are never touched. Idempotent: calling it twice for
     * the same weekday only renames the workout created the first time.
     */
    public WorkoutDay createWorkoutDay(Long userId, Long planId, int weekday, String name) {
        validateWeekday(weekday);
        String label = name == null ? "" : name.trim();
        if (label.length() > 60) {
            throw new IllegalArgumentException("Workout name must be 60 characters or fewer");
        }

        List<WorkoutDay> occupants = workoutDayRepository
                .findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(userId, planId, weekday);

        // A training day already owns the weekday: rename it and merge duplicate
        // occupants away instead of creating a second schedule row.
        Optional<WorkoutDay> existingTraining = occupants.stream().filter(d -> !d.isRestDay()).findFirst();
        if (existingTraining.isPresent()) {
            WorkoutDay training = existingTraining.get();
            detachOtherOccupants(occupants, training);
            if (!label.isEmpty() && !label.equals(training.getName())) {
                training.setName(label);
                return workoutDayRepository.save(training);
            }
            return training;
        }

        // Only a rest row occupies the weekday: transform that same row in place
        // (row id — and therefore any historical sessions — survive the change)
        // and move it out of the reserved rest day_number range so it becomes a
        // selectable workout. No duplicate rest row is left behind.
        Optional<WorkoutDay> rest = occupants.stream().filter(WorkoutDay::isRestDay).findFirst();
        if (rest.isPresent()) {
            WorkoutDay day = rest.get();
            detachOtherOccupants(occupants, day);
            int dayNumber = day.getDayNumber() == null || day.getDayNumber() >= REST_DAY_NUMBER_BASE
                    ? nextTrainingDayNumber(userId, planId)
                    : day.getDayNumber();
            day.setWeekday(weekday);
            day.setRestDay(false);
            day.setDayNumber(dayNumber);
            day.setName(label.isEmpty() ? "Workout " + dayNumber : label);
            return workoutDayRepository.save(day);
        }

        int dayNumber = nextTrainingDayNumber(userId, planId);
        WorkoutDay day = new WorkoutDay();
        day.setUserId(userId);
        day.setPlanId(planId);
        day.setWeekday(weekday);
        day.setRestDay(false);
        day.setDayNumber(dayNumber);
        day.setName(label.isEmpty() ? "Workout " + dayNumber : label);
        return workoutDayRepository.save(day);
    }

    /** Smallest unused day_number at or above {@link #CUSTOM_DAY_NUMBER}+1, staying below {@link #REST_DAY_NUMBER_BASE}. */
    private int nextTrainingDayNumber(Long userId, Long planId) {
        Set<Integer> used = new HashSet<>();
        for (WorkoutDay day : workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(userId, planId)) {
            if (day.getDayNumber() != null) {
                used.add(day.getDayNumber());
            }
        }
        int candidate = CUSTOM_DAY_NUMBER + 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        if (candidate >= REST_DAY_NUMBER_BASE) {
            throw new IllegalStateException("This plan already has the maximum number of workout days");
        }
        return candidate;
    }

    /** Creates or restores the reserved rest row for one weekday. */
    public WorkoutDay setRestDay(Long userId, Long planId, int weekday) {
        validateWeekday(weekday);
        int reservedNumber = restDayNumberForWeekday(weekday);
        List<WorkoutDay> occupants = workoutDayRepository
                .findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(userId, planId, weekday);

        List<WorkoutDay> detached = new ArrayList<>();
        for (WorkoutDay day : occupants) {
            if (!day.isRestDay()) {
                day.setWeekday(null);
                workoutDayRepository.save(day);
                detached.add(day);
            }
        }

        WorkoutDay rest = workoutDayRepository
                .findByUserIdAndPlanIdAndDayNumber(userId, planId, reservedNumber)
                .orElseGet(() -> detached.stream()
                        .filter(d -> Objects.equals(d.getDayNumber(), reservedNumber))
                        .findFirst()
                        .orElseGet(() -> {
                            WorkoutDay created = new WorkoutDay();
                            created.setUserId(userId);
                            created.setPlanId(planId);
                            created.setDayNumber(reservedNumber);
                            return created;
                        }));
        rest.setName("Rest");
        rest.setWeekday(weekday);
        rest.setRestDay(true);
        return workoutDayRepository.save(rest);
    }

    /** Detaches every occupant of a weekday except the keeper, so only one row keeps the weekday. */
    private void detachOtherOccupants(List<WorkoutDay> occupants, WorkoutDay keeper) {
        for (WorkoutDay other : occupants) {
            if (!Objects.equals(other.getId(), keeper.getId()) && other.getWeekday() != null) {
                other.setWeekday(null);
                workoutDayRepository.save(other);
            }
        }
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

        // A plan that already carries any explicit weekday is an explicit 7-day
        // schedule. Legacy plans (no weekday set anywhere) are mapped below.
        boolean explicitSchedule = inPlan.stream().anyMatch(d -> d.getWeekday() != null);
        Set<Integer> weekdaysTaken = new HashSet<>();

        // At most one keeper per weekday; duplicates are detached, never deleted.
        Map<Integer, WorkoutDay> byWeekday = new LinkedHashMap<>();
        for (WorkoutDay day : inPlan) {
            Integer weekday = day.getWeekday();
            if (weekday == null || weekday < 1 || weekday > 7) {
                continue;
            }
            WorkoutDay existing = byWeekday.get(weekday);
            if (existing == null) {
                byWeekday.put(weekday, day);
                weekdaysTaken.add(weekday);
                continue;
            }
            WorkoutDay keeper = preferKeeper(existing, day);
            WorkoutDay loser = keeper == existing ? day : existing;
            byWeekday.put(weekday, keeper);
            if (!Objects.equals(loser.getId(), keeper.getId())) {
                loser.setWeekday(null);
                workoutDayRepository.save(loser);
            }
        }

        // Legacy plans map training slots 1..5 onto weekdays via the week anchor.
        if (!explicitSchedule) {
            for (WorkoutDay day : inPlan) {
                if (day.getWeekday() != null || day.isRestDay()) {
                    continue;
                }
                Integer dayNumber = day.getDayNumber();
                if (dayNumber == null || dayNumber == CUSTOM_DAY_NUMBER || dayNumber < 1 || dayNumber > 5) {
                    continue;
                }
                int legacyWeekday = weekdayForLegacySlot(startDay, dayNumber);
                if (!weekdaysTaken.contains(legacyWeekday)) {
                    day.setWeekday(legacyWeekday);
                    workoutDayRepository.save(day);
                    weekdaysTaken.add(legacyWeekday);
                }
            }
        }

        for (int weekday = 1; weekday <= 7; weekday++) {
            if (weekdaysTaken.contains(weekday)) {
                continue;
            }
            int dayNumber = restDayNumberForWeekday(weekday);
            while (dayNumberTaken(inPlan, dayNumber)) {
                dayNumber++;
            }
            WorkoutDay rest = new WorkoutDay();
            rest.setUserId(userId);
            rest.setPlanId(planId);
            rest.setWeekday(weekday);
            rest.setRestDay(true);
            rest.setName("Rest");
            rest.setDayNumber(dayNumber);
            workoutDayRepository.save(rest);
            inPlan.add(rest);
            weekdaysTaken.add(weekday);
        }
    }

    /** Prefers a training day over a rest row, then the lowest id. */
    private WorkoutDay preferKeeper(WorkoutDay a, WorkoutDay b) {
        if (a.isRestDay() != b.isRestDay()) {
            return a.isRestDay() ? b : a;
        }
        long aid = a.getId() == null ? Long.MAX_VALUE : a.getId();
        long bid = b.getId() == null ? Long.MAX_VALUE : b.getId();
        return aid <= bid ? a : b;
    }

    private boolean dayNumberTaken(List<WorkoutDay> rows, int dayNumber) {
        for (WorkoutDay day : rows) {
            if (Objects.equals(day.getDayNumber(), dayNumber)) {
                return true;
            }
        }
        return false;
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
