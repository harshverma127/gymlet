package com.gymlet.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gymlet.domain.WorkoutDay;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutSessionRepository;

/**
 * Replays the real corrupted schedule reported for user_id=2, plan_id=4 and
 * asserts the exact expected repair, plus idempotency and keeper priority.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleReconcilerTest {

    private static final long USER_ID = 2L;
    private static final long PLAN_ID = 4L;

    @Mock
    private WorkoutDayRepository workoutDayRepository;
    @Mock
    private WorkoutSessionRepository sessionRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;

    @Test
    void repairsTheKnownCorruptedSchedule() {
        WorkoutDay trainingDay1 = day(1L, 1, null, "Back + Chest A", false);
        WorkoutDay trainingDay2 = day(2L, 2, null, "Shoulders + Arms A", false);
        WorkoutDay trainingDay3 = day(3L, 3, null, "Legs + Abs", false);
        WorkoutDay trainingDay4 = day(4L, 4, null, "Back + Chest B", false);
        WorkoutDay trainingDay5 = day(5L, 5, null, "Shoulders + Arms B", false);
        WorkoutDay back = day(58L, 51, 3, "Back", false);
        WorkoutDay restWeekday2 = day(59L, 52, 2, "Rest", false);
        WorkoutDay duplicateRest = day(60L, 53, 3, "Rest", false);
        WorkoutDay rest4 = day(61L, 54, 4, "Rest", true);
        WorkoutDay rest5 = day(62L, 55, 5, "Rest", true);
        WorkoutDay rest6 = day(63L, 56, 6, "Rest", true);
        WorkoutDay rest7 = day(64L, 57, 7, "Rest", true);

        List<WorkoutDay> rows = new ArrayList<>(List.of(
                trainingDay1, trainingDay2, trainingDay3, trainingDay4, trainingDay5,
                back, restWeekday2, duplicateRest, rest4, rest5, rest6, rest7));
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID)).thenReturn(rows);
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleReconciler reconciler = new ScheduleReconciler(
                workoutDayRepository, sessionRepository, workoutExerciseRepository);
        int changes = reconciler.reconcilePlan(USER_ID, PLAN_ID);

        assertTrue(changes > 0);

        // The dependency-free duplicate placeholder (id 60) is the only row removed.
        ArgumentCaptor<WorkoutDay> deleted = ArgumentCaptor.forClass(WorkoutDay.class);
        verify(workoutDayRepository, times(1)).delete(deleted.capture());
        assertEquals(60L, deleted.getValue().getId());

        // Weekday 3 keeps the real named workout, moved out of the rest range.
        assertEquals(3, back.getWeekday());
        assertEquals(7, back.getDayNumber());
        assertFalse(back.isRestDay());
        assertEquals("Back", back.getName());

        // The malformed "Rest"/rest_day=false row for weekday 2 is normalised.
        assertEquals(2, restWeekday2.getWeekday());
        assertEquals(52, restWeekday2.getDayNumber());
        assertTrue(restWeekday2.isRestDay());
        assertEquals("Rest", restWeekday2.getName());

        // Weekday 1 is backfilled as a rest day, taking the now-free reserved slot.
        ArgumentCaptor<WorkoutDay> saved = ArgumentCaptor.forClass(WorkoutDay.class);
        verify(workoutDayRepository, atLeast(1)).save(saved.capture());
        WorkoutDay backfilled = saved.getAllValues().stream()
                .filter(d -> Integer.valueOf(1).equals(d.getWeekday()))
                .findFirst().orElse(null);
        assertNotNull(backfilled);
        assertTrue(backfilled.isRestDay());
        assertEquals("Rest", backfilled.getName());
        assertEquals(51, backfilled.getDayNumber());

        // Exactly one row per weekday, and only weekday 3 is a workout.
        List<WorkoutDay> repaired = List.of(trainingDay1, trainingDay2, trainingDay3, trainingDay4, trainingDay5,
                back, restWeekday2, rest4, rest5, rest6, rest7, backfilled);
        for (int weekday = 1; weekday <= 7; weekday++) {
            int finalWeekday = weekday;
            long count = repaired.stream()
                    .filter(d -> Integer.valueOf(finalWeekday).equals(d.getWeekday()))
                    .count();
            assertEquals(1, count, "weekday " + weekday + " should have exactly one row");
        }
        assertEquals("Rest", nameOfWeekday(repaired, 1));
        assertEquals("Rest", nameOfWeekday(repaired, 2));
        assertEquals("Back", nameOfWeekday(repaired, 3));
        assertEquals("Rest", nameOfWeekday(repaired, 4));
    }

    @Test
    void isIdempotentOnAnAlreadyCleanSchedule() {
        List<WorkoutDay> clean = new ArrayList<>(List.of(
                day(1L, 1, null, "Back + Chest A", false),
                day(2L, 2, null, "Shoulders + Arms A", false),
                day(51L, 51, 1, "Rest", true),
                day(52L, 52, 2, "Rest", true),
                day(58L, 7, 3, "Back", false),
                day(54L, 54, 4, "Rest", true),
                day(55L, 55, 5, "Rest", true),
                day(56L, 56, 6, "Rest", true),
                day(57L, 57, 7, "Rest", true)));
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID)).thenReturn(clean);

        ScheduleReconciler reconciler = new ScheduleReconciler(
                workoutDayRepository, sessionRepository, workoutExerciseRepository);
        int changes = reconciler.reconcilePlan(USER_ID, PLAN_ID);

        assertEquals(0, changes);
        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
        verify(workoutDayRepository, never()).delete(any(WorkoutDay.class));
    }

    @Test
    void keepsTheDuplicateRowThatOwnsHistory() {
        // Same weekday 3 twice: a placeholder "Rest" (id 60) that owns a session beats
        // the named placeholder (id 58) with no history.
        WorkoutDay back = day(58L, 7, 3, "Back", false);
        WorkoutDay restWithHistory = day(60L, 53, 3, "Rest", false);
        List<WorkoutDay> rows = new ArrayList<>(List.of(back, restWithHistory));
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID)).thenReturn(rows);
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(sessionRepository.countByWorkoutDayId(60L)).thenReturn(2L);

        ScheduleReconciler reconciler = new ScheduleReconciler(
                workoutDayRepository, sessionRepository, workoutExerciseRepository);
        reconciler.reconcilePlan(USER_ID, PLAN_ID);

        // The history-bearing row is kept (normalised to a rest placeholder).
        assertEquals(3, restWithHistory.getWeekday());
        assertTrue(restWithHistory.isRestDay());
        // The other row has no dependencies, so it is removed rather than detached.
        verify(workoutDayRepository, times(1)).delete(back);
    }

    @Test
    void clearsOutOfRangeWeekdays() {
        WorkoutDay stray = day(70L, 20, 9, "Odd", false);
        List<WorkoutDay> rows = new ArrayList<>(List.of(stray));
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID)).thenReturn(rows);
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduleReconciler reconciler = new ScheduleReconciler(
                workoutDayRepository, sessionRepository, workoutExerciseRepository);
        reconciler.reconcilePlan(USER_ID, PLAN_ID);

        assertNull(stray.getWeekday());
    }

    private String nameOfWeekday(List<WorkoutDay> rows, int weekday) {
        return rows.stream()
                .filter(d -> Integer.valueOf(weekday).equals(d.getWeekday()))
                .map(WorkoutDay::getName)
                .findFirst().orElse(null);
    }

    private WorkoutDay day(long id, int dayNumber, Integer weekday, String name, boolean restDay) {
        WorkoutDay d = new WorkoutDay();
        setId(d, id);
        d.setUserId(USER_ID);
        d.setPlanId(PLAN_ID);
        d.setDayNumber(dayNumber);
        d.setWeekday(weekday);
        d.setName(name);
        d.setRestDay(restDay);
        return d;
    }

    private void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
