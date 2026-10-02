package com.gymlet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutSessionRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-database (H2 + JPA) coverage for {@link ScheduleReconciler}.
 *
 * <p>The Mockito tests assert the repair logic; these assert that the repair
 * actually satisfies the {@code uk_workout_day_user_plan_day} unique constraint
 * under Hibernate's flush ordering (INSERTs are executed before UPDATEs and
 * DELETEs). Before this test existed the reconciler failed in production with
 * {@code duplicate key ... (user_id, plan_id, day_number)=(2, 4, 51)} because a
 * new weekday-1 Rest row was inserted with day_number 51 while the existing
 * workout row still held 51 in the database, pending its renumber to 7.
 */
@DataJpaTest
@Import(ScheduleReconciler.class)
class ScheduleReconcilerIntegrationTest {

    private static final long USER_ID = 2L;
    private static final long PLAN_ID = 4L;

    @Autowired
    private WorkoutDayRepository workoutDayRepository;

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private ScheduleReconciler reconciler;

    /**
     * The exact production rows 58–64 plus the legacy training slots 1–5.
     * Fails with a unique-constraint violation before the collision-safe
     * renumbering fix, and passes afterwards.
     */
    @Test
    void repairsProductionCollisionWithoutViolatingUniqueDayNumber() {
        persist(day(1, null, "Back + Chest A", false));
        persist(day(2, null, "Shoulders + Arms A", false));
        persist(day(3, null, "Legs + Abs", false));
        persist(day(4, null, "Back + Chest B", false));
        persist(day(5, null, "Shoulders + Arms B", false));
        WorkoutDay back = persist(day(51, 3, "Back", false));
        WorkoutDay restWeekday2 = persist(day(52, 2, "Rest", false));
        WorkoutDay duplicateRest = persist(day(53, 3, "Rest", false));
        persist(day(54, 4, "Rest", true));
        persist(day(55, 5, "Rest", true));
        persist(day(56, 6, "Rest", true));
        persist(day(57, 7, "Rest", true));
        workoutDayRepository.flush();

        assertDoesNotThrow(() -> reconciler.reconcilePlan(USER_ID, PLAN_ID));

        List<WorkoutDay> repaired = workoutDayRepository
                .findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID);
        Map<Integer, WorkoutDay> byWeekday = repaired.stream()
                .filter(d -> d.getWeekday() != null)
                .collect(Collectors.toMap(WorkoutDay::getWeekday, Function.identity()));

        assertEquals(7, byWeekday.size(), "exactly one row per weekday");
        assertEquals("Rest", byWeekday.get(1).getName());
        assertEquals(51, byWeekday.get(1).getDayNumber());
        assertTrue(byWeekday.get(1).isRestDay());
        assertTrue(byWeekday.get(2).isRestDay());
        assertEquals(52, byWeekday.get(2).getDayNumber());
        assertEquals("Back", byWeekday.get(3).getName());
        assertEquals(7, byWeekday.get(3).getDayNumber());
        assertTrue(!byWeekday.get(3).isRestDay());
        // The dependency-free duplicate weekday-3 placeholder is the only row removed.
        assertTrue(repaired.stream().noneMatch(d -> d.getId().equals(duplicateRest.getId())),
                "the redundant weekday-3 placeholder must be deleted");

        // No two rows share a day_number and no workout sits in the reserved rest range.
        assertEquals(repaired.size(),
                repaired.stream().map(WorkoutDay::getDayNumber).collect(Collectors.toSet()).size());
        for (WorkoutDay d : repaired) {
            if (!d.isRestDay()) {
                assertTrue(d.getDayNumber() < 50, "workout must not use a reserved rest number: " + d.getDayNumber());
            }
        }
        assertEquals(7, back.getDayNumber(), "the workout must leave the reserved rest range");
        assertTrue(restWeekday2.isRestDay());
    }

    @Test
    void repeatedReconciliationMakesNoFurtherChanges() {
        persist(day(1, null, "Back + Chest A", false));
        persist(day(2, null, "Shoulders + Arms A", false));
        persist(day(3, null, "Legs + Abs", false));
        persist(day(4, null, "Back + Chest B", false));
        persist(day(5, null, "Shoulders + Arms B", false));
        persist(day(51, 3, "Back", false));
        persist(day(52, 2, "Rest", false));
        persist(day(53, 3, "Rest", false));
        persist(day(54, 4, "Rest", true));
        persist(day(55, 5, "Rest", true));
        persist(day(56, 6, "Rest", true));
        persist(day(57, 7, "Rest", true));
        workoutDayRepository.flush();

        reconciler.reconcilePlan(USER_ID, PLAN_ID);
        List<Integer> snapshot = workoutDayRepository
                .findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID)
                .stream().map(WorkoutDay::getDayNumber).toList();

        int changes = assertDoesNotThrow(() -> reconciler.reconcilePlan(USER_ID, PLAN_ID));
        assertEquals(0, changes, "a repaired schedule must be a no-op on the next run");
        assertEquals(snapshot, workoutDayRepository
                .findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID)
                .stream().map(WorkoutDay::getDayNumber).toList());
    }

    /**
     * A duplicate weekday row that still owns history must be detached, never
     * deleted, and must not block the canonical schedule numbers.
     */
    @Test
    void detachesDependencyBearingDuplicateInsteadOfDeleting() {
        WorkoutDay restWithHistory = persist(day(53, 3, "Rest", false));
        WorkoutDay workout = persist(day(7, 3, "Legs", false));
        persist(day(51, 1, "Rest", true));
        persist(day(52, 2, "Rest", true));
        persist(day(54, 4, "Rest", true));
        persist(day(55, 5, "Rest", true));
        persist(day(56, 6, "Rest", true));
        persist(day(57, 7, "Rest", true));
        workoutDayRepository.flush();

        // Give both rows history, and the workout more, so the workout is the
        // deterministic keeper and the rest row (which owns a session) must be
        // detached instead of deleted.
        sessionRepository.save(session(workout));
        sessionRepository.save(session(workout));
        sessionRepository.save(session(restWithHistory));
        sessionRepository.flush();

        long before = workoutDayRepository.count();
        assertDoesNotThrow(() -> reconciler.reconcilePlan(USER_ID, PLAN_ID));

        assertEquals(before, workoutDayRepository.count(), "no dependency-bearing row may be deleted");
        assertNotNull(workoutDayRepository.findById(restWithHistory.getId()).orElse(null));
        assertEquals(3, workout.getWeekday());
        assertNull(restWithHistory.getWeekday());

        // Every weekday is still covered exactly once and no day_number is shared.
        List<WorkoutDay> repaired = workoutDayRepository
                .findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID);
        assertEquals(7, repaired.stream().filter(d -> d.getWeekday() != null).count());
        assertEquals(repaired.size(),
                repaired.stream().map(WorkoutDay::getDayNumber).collect(Collectors.toSet()).size());
    }

    @Test
    void collapsesDuplicateWeekdayAndBackfillsEveryMissingWeekday() {
        WorkoutDay monday = persist(day(7, 1, "Push", false));
        persist(day(53, 1, "Rest", false));
        persist(day(54, 3, "Rest", true));
        workoutDayRepository.flush();

        assertDoesNotThrow(() -> reconciler.reconcilePlan(USER_ID, PLAN_ID));

        List<WorkoutDay> repaired = workoutDayRepository
                .findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID);
        Map<Integer, WorkoutDay> byWeekday = repaired.stream()
                .filter(d -> d.getWeekday() != null)
                .collect(Collectors.toMap(WorkoutDay::getWeekday, Function.identity()));
        assertEquals(7, byWeekday.size());
        assertEquals("Push", byWeekday.get(1).getName());
        assertTrue(!byWeekday.get(1).isRestDay());
        assertEquals("Push", monday.getName());
    }

    private WorkoutDay persist(WorkoutDay day) {
        return workoutDayRepository.save(day);
    }

    private WorkoutDay day(int dayNumber, Integer weekday, String name, boolean restDay) {
        WorkoutDay d = new WorkoutDay();
        d.setUserId(USER_ID);
        d.setPlanId(PLAN_ID);
        d.setDayNumber(dayNumber);
        d.setWeekday(weekday);
        d.setName(name);
        d.setRestDay(restDay);
        return d;
    }

    private WorkoutSession session(WorkoutDay day) {
        WorkoutSession s = new WorkoutSession();
        s.setUserId(USER_ID);
        s.setWorkoutDay(day);
        s.setWorkoutDayNameSnapshot(day.getName());
        s.setDate(LocalDate.of(2026, 1, 5));
        s.setStartedAt(LocalDateTime.of(2026, 1, 5, 18, 0));
        s.setCompleted(true);
        s.setDemo(false);
        return s;
    }
}
