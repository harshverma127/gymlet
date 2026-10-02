package com.gymlet.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gymlet.domain.WorkoutDay;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutSessionRepository;

/**
 * Idempotent, non-destructive repair of malformed weekly schedules.
 *
 * <p>The intended invariant for each {@code user + plan + weekday} is <b>at most
 * one</b> schedule row, where {@code rest_day} (not the name) is the source of
 * truth. Historical rows created before {@code rest_day} existed encoded "rest"
 * purely in the name, so {@code ALTER TABLE ... ADD COLUMN rest_day DEFAULT FALSE}
 * stamped them as workouts named "Rest". Weekend/legacy rows could also leave a
 * plan with a missing weekday or two rows on one weekday. None of that is a
 * supported state, and nothing else in the app repairs it.
 *
 * <p>This reconciler repairs that data <b>without deleting anything the user
 * created</b>:
 * <ul>
 *   <li>A duplicate weekday collapses to a deterministic keeper: a row with
 *       sessions beats one with exercises, which beats a real named workout,
 *       which beats the lowest id.</li>
 *   <li>A losing duplicate that still owns history/exercises is <em>detached</em>
 *       (weekday cleared) and kept; only a dependency-free placeholder is
 *       removed, and only inside this controlled migration.</li>
 *   <li>Placeholder "Rest" rows that lost {@code rest_day} are normalised to
 *       {@code rest_day = true} — but only when they occupy the reserved rest
 *       slot ({@code day_number = 50 + weekday}) for their own weekday, so the
 *       word "Rest" alone is never treated as proof.</li>
 *   <li>A genuine workout that drifted into the reserved rest day_number range
 *       is renumbered into the training range (row id, exercises and history are
 *       preserved).</li>
 *   <li>Missing weekdays are backfilled as rest rows for plans that already
 *       carry an explicit weekday schedule.</li>
 * </ul>
 *
 * <p>Every step is safe to run repeatedly and never deletes sessions, sets,
 * exercises, notes or plans.
 *
 * <p>The repair is split into explicit phases that each {@code flush()} before
 * the next begins. Hibernate executes INSERTs before UPDATEs and DELETEs inside
 * a single flush, so without those boundaries a newly inserted rest row can
 * transiently collide with a row that is only parked, renumbered or deleted
 * later in the same flush — the production
 * {@code duplicate key ... (user_id, plan_id, day_number)=(2, 4, 51)} failure.
 * With the flushes, every phase allocates day numbers against the database's
 * real state, so the reserved-slot allocator is collision-safe and the repair
 * is deterministic and idempotent.
 */
@Component
public class ScheduleReconciler {

    private static final Logger log = LoggerFactory.getLogger(ScheduleReconciler.class);

    private static final int REST_DAY_NUMBER_BASE = PlanScheduleSupport.REST_DAY_NUMBER_BASE;
    private static final int CUSTOM_DAY_NUMBER = PlanScheduleSupport.CUSTOM_DAY_NUMBER;
    private static final int MIN_TRAINING_DAY_NUMBER = CUSTOM_DAY_NUMBER + 1;

    private final WorkoutDayRepository workoutDayRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;

    public ScheduleReconciler(WorkoutDayRepository workoutDayRepository,
                              WorkoutSessionRepository sessionRepository,
                              WorkoutExerciseRepository workoutExerciseRepository) {
        this.workoutDayRepository = workoutDayRepository;
        this.sessionRepository = sessionRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
    }

    /** Reconciles every user+plan that owns at least one workout day. Returns the number of repaired rows. */
    @Transactional
    public int reconcileAll() {
        Map<PlanKey, List<WorkoutDay>> byPlan = new LinkedHashMap<>();
        for (WorkoutDay day : workoutDayRepository.findAll()) {
            if (day.getUserId() == null || day.getPlanId() == null) {
                continue;
            }
            byPlan.computeIfAbsent(new PlanKey(day.getUserId(), day.getPlanId()), k -> new ArrayList<>()).add(day);
        }
        int changes = 0;
        for (PlanKey key : byPlan.keySet()) {
            changes += reconcilePlan(key.userId(), key.planId());
        }
        if (changes > 0) {
            log.warn("Schedule reconciliation repaired {} malformed workout_day row(s)", changes);
        }
        return changes;
    }

    /** Repairs one plan's schedule. Idempotent: a clean plan produces zero changes. */
    @Transactional
    public int reconcilePlan(Long userId, Long planId) {
        List<WorkoutDay> rows = new ArrayList<>(
                workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(userId, planId));
        if (rows.isEmpty()) {
            return 0;
        }
        // A plan that already carries any explicit weekday is treated as an
        // explicit 7-day schedule; legacy plans (no weekday anywhere) are left
        // for ensureSevenDaySchedule's legacy slot mapping.
        boolean explicitSchedule = rows.stream().anyMatch(d -> d.getWeekday() != null);
        int changes = 0;

        // (a) A weekday outside 1..7 is not a schedule slot at all.
        for (WorkoutDay day : rows) {
            Integer weekday = day.getWeekday();
            if (weekday != null && (weekday < 1 || weekday > 7)) {
                day.setWeekday(null);
                workoutDayRepository.save(day);
                changes++;
                log.warn("Cleared out-of-range weekday {} on workout_day id={} (user={}, plan={})",
                        weekday, day.getId(), userId, planId);
            }
        }

        // (b) At most one row per weekday: keep the most valuable one.
        Map<Integer, List<WorkoutDay>> byWeekday = new LinkedHashMap<>();
        for (WorkoutDay day : rows) {
            if (day.getWeekday() != null && day.getWeekday() >= 1 && day.getWeekday() <= 7) {
                byWeekday.computeIfAbsent(day.getWeekday(), k -> new ArrayList<>()).add(day);
            }
        }
        for (Map.Entry<Integer, List<WorkoutDay>> entry : byWeekday.entrySet()) {
            List<WorkoutDay> group = entry.getValue();
            if (group.size() <= 1) {
                continue;
            }
            WorkoutDay keeper = pickKeeper(group);
            for (WorkoutDay loser : group) {
                if (Objects.equals(loser.getId(), keeper.getId())) {
                    continue;
                }
                changes += reconcileLoser(loser, keeper, rows, entry.getKey());
            }
        }

        // (c) A rest placeholder that lost its rest_day flag: reserved slot for its
        // own weekday AND named "Rest". The day_number convention disambiguates,
        // so a real workout that merely happens to be named "Rest" is untouched.
        for (WorkoutDay day : rows) {
            if (!day.isRestDay() && isReservedRestPlaceholder(day)) {
                day.setRestDay(true);
                day.setName("Rest");
                workoutDayRepository.save(day);
                changes++;
                log.info("Normalised malformed rest row id={} (weekday {}, day_number {}) to rest_day=true",
                        day.getId(), day.getWeekday(), day.getDayNumber());
            }
        }

        // Flush the structural fixes before any day_number is reassigned. A row
        // deleted or detached above still holds its old day_number in the
        // database until flushed, and Hibernate executes INSERTs before
        // UPDATEs and DELETEs within a single flush, so a later phase could pick
        // a number that is not actually free yet.
        workoutDayRepository.flush();

        // (d) A real workout must not occupy the reserved rest day_number range.
        for (WorkoutDay day : rows) {
            Integer dayNumber = day.getDayNumber();
            if (!day.isRestDay() && dayNumber != null && dayNumber != CUSTOM_DAY_NUMBER
                    && dayNumber >= REST_DAY_NUMBER_BASE) {
                int old = dayNumber;
                int free = nextFreeTrainingNumber(rows, day);
                day.setDayNumber(free);
                workoutDayRepository.save(day);
                changes++;
                log.info("Renumbered workout_day id={} out of the reserved rest range ({} -> {})",
                        day.getId(), old, free);
            }
        }

        // Apply the workout renumbering (phase d) before the reserved rest
        // slots are assigned, so a training number freed here is visible to
        // phase (e) and the backfill in phase (g).
        workoutDayRepository.flush();

        // (e) Rest rows use their reserved day_number when it is free.
        for (WorkoutDay day : rows) {
            if (!day.isRestDay() || day.getWeekday() == null) {
                continue;
            }
            if (!"Rest".equals(day.getName())) {
                day.setName("Rest");
                workoutDayRepository.save(day);
                changes++;
            }
            int desired = REST_DAY_NUMBER_BASE + day.getWeekday();
            if (!Objects.equals(day.getDayNumber(), desired) && isDayNumberFree(rows, desired, day)) {
                day.setDayNumber(desired);
                workoutDayRepository.save(day);
                changes++;
            }
        }

        // Apply the reserved-slot assignment (phase e) before phase (g) inserts
        // the missing-weekday rest rows, so an INSERT can never race a pending
        // UPDATE for the same (user_id, plan_id, day_number).
        workoutDayRepository.flush();

        // (f) A workout day needs a meaningful name.
        for (WorkoutDay day : rows) {
            if (!day.isRestDay() && (day.getName() == null || day.getName().isBlank())) {
                day.setName("Workout " + day.getDayNumber());
                workoutDayRepository.save(day);
                changes++;
            }
        }

        // (g) Backfill missing weekdays as rest rows, only for explicit schedules.
        if (explicitSchedule) {
            Set<Integer> covered = new HashSet<>();
            for (WorkoutDay day : rows) {
                if (day.getWeekday() != null) {
                    covered.add(day.getWeekday());
                }
            }
            for (int weekday = 1; weekday <= 7; weekday++) {
                if (covered.contains(weekday)) {
                    continue;
                }
                WorkoutDay rest = new WorkoutDay();
                rest.setUserId(userId);
                rest.setPlanId(planId);
                rest.setWeekday(weekday);
                rest.setRestDay(true);
                rest.setName("Rest");
                rest.setDayNumber(firstFreeRestNumber(rows, REST_DAY_NUMBER_BASE + weekday));
                rows.add(rest);
                workoutDayRepository.save(rest);
                changes++;
                log.info("Backfilled missing weekday {} as a rest day (user={}, plan={})", weekday, userId, planId);
            }
        }
        return changes;
    }

    // ------------------------------------------------------------- keeper logic

    /**
     * Deterministic keeper for a duplicated weekday:
     * sessions &gt; exercises &gt; real named workout &gt; lowest id.
     */
    private WorkoutDay pickKeeper(List<WorkoutDay> group) {
        Comparator<WorkoutDay> preference = Comparator
                .comparingInt((WorkoutDay d) -> sessionCount(d) > 0 ? 0 : 1)
                .thenComparingInt(d -> exerciseCount(d) > 0 ? 0 : 1)
                .thenComparingInt(d -> isRealWorkout(d) ? 0 : 1)
                .thenComparingLong(d -> d.getId() == null ? Long.MAX_VALUE : d.getId());
        return group.stream().min(preference).orElse(group.get(0));
    }

    private int reconcileLoser(WorkoutDay loser, WorkoutDay keeper, List<WorkoutDay> rows, int weekday) {
        if (sessionCount(loser) > 0 || exerciseCount(loser) > 0) {
            loser.setWeekday(null);
            workoutDayRepository.save(loser);
            log.warn("Detached duplicate weekday {} row id={} kept for its history/exercises (keeper id={})",
                    weekday, loser.getId(), keeper.getId());
            return 1;
        }
        rows.remove(loser);
        workoutDayRepository.delete(loser);
        log.info("Removed redundant duplicate weekday {} placeholder row id={} (keeper id={})",
                weekday, loser.getId(), keeper.getId());
        return 1;
    }

    private boolean isRealWorkout(WorkoutDay day) {
        return !day.isRestDay() && day.getName() != null && !day.getName().isBlank()
                && !"Rest".equalsIgnoreCase(day.getName().trim());
    }

    /** A row that occupies {@code 50 + weekday} for its own weekday but is flagged as a workout, yet named "Rest". */
    private boolean isReservedRestPlaceholder(WorkoutDay day) {
        Integer weekday = day.getWeekday();
        Integer dayNumber = day.getDayNumber();
        if (weekday == null || weekday < 1 || weekday > 7 || dayNumber == null) {
            return false;
        }
        return dayNumber == REST_DAY_NUMBER_BASE + weekday
                && "Rest".equalsIgnoreCase(day.getName() == null ? "" : day.getName().trim());
    }

    // ------------------------------------------------------------- number helpers

    private int nextFreeTrainingNumber(List<WorkoutDay> rows, WorkoutDay exclude) {
        Set<Integer> used = usedDayNumbers(rows, exclude);
        int candidate = MIN_TRAINING_DAY_NUMBER;
        while (used.contains(candidate)) {
            candidate++;
        }
        if (candidate >= REST_DAY_NUMBER_BASE) {
            throw new IllegalStateException("Plan has no free workout day_number below " + REST_DAY_NUMBER_BASE);
        }
        return candidate;
    }

    private int firstFreeRestNumber(List<WorkoutDay> rows, int desired) {
        Set<Integer> used = usedDayNumbers(rows, null);
        if (!used.contains(desired)) {
            return desired;
        }
        int candidate = REST_DAY_NUMBER_BASE + 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private boolean isDayNumberFree(List<WorkoutDay> rows, int dayNumber, WorkoutDay exclude) {
        for (WorkoutDay day : rows) {
            if (day != exclude && Objects.equals(day.getDayNumber(), dayNumber)) {
                return false;
            }
        }
        return true;
    }

    private Set<Integer> usedDayNumbers(List<WorkoutDay> rows, WorkoutDay exclude) {
        Set<Integer> used = new HashSet<>();
        for (WorkoutDay day : rows) {
            if (day != exclude && day.getDayNumber() != null) {
                used.add(day.getDayNumber());
            }
        }
        return used;
    }

    private long sessionCount(WorkoutDay day) {
        return day.getId() == null ? 0 : sessionRepository.countByWorkoutDayId(day.getId());
    }

    private long exerciseCount(WorkoutDay day) {
        return day.getId() == null ? 0 : workoutExerciseRepository.countByWorkoutDayId(day.getId());
    }

    private record PlanKey(Long userId, Long planId) {
    }
}
