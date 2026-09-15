package com.gymlet.service;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutPlan;
import com.gymlet.repository.AppUserRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutPlanRepository;
import com.gymlet.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class ScheduleMutationTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long PLAN_ID = 42L;
    private static final long OTHER_PLAN_ID = 99L;

    @Mock
    private WorkoutDayRepository workoutDayRepository;
    @org.mockito.Mock
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Mock
    private WorkoutPlanRepository planRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private WorkoutSessionRepository sessionRepository;

    private final UserContext userContext = new UserContext();
    private final AppUser user = new AppUser();
    private PlanScheduleSupport scheduleSupport;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        setId(user, USER_ID);
        userContext.set(user);
        scheduleSupport = new PlanScheduleSupport(workoutDayRepository);
        planService = new PlanService(userContext, userRepository, planRepository, workoutDayRepository,
            workoutExerciseRepository, sessionRepository, scheduleSupport);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void assignsAWorkoutDayToAnIsoWeekday() {
        WorkoutPlan plan = plan(false);
        WorkoutDay day = workoutDay(PLAN_ID, 2);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findByIdAndUserId(20L, USER_ID)).thenReturn(Optional.of(day));
        when(workoutDayRepository.findByUserIdAndPlanIdAndWeekday(USER_ID, PLAN_ID, 3))
                .thenReturn(Optional.empty());
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.assignWorkoutDay(PLAN_ID, 3, 20L);

        assertEquals(3, day.getWeekday());
        assertEquals(2, day.getDayNumber());
        assertEquals(PLAN_ID, day.getPlanId());
        verify(workoutDayRepository).save(day);
    }

    @Test
    void createsARestDayWithoutChangingWorkoutData() {
        WorkoutPlan plan = plan(false);
        WorkoutDay workout = workoutDay(PLAN_ID, 2);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findByUserIdAndPlanIdAndWeekday(USER_ID, PLAN_ID, 4))
                .thenReturn(Optional.of(workout));
        when(workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(USER_ID, PLAN_ID, 54))
                .thenReturn(Optional.empty());
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.setRestDay(PLAN_ID, 4);

        assertEquals(null, workout.getWeekday());
        assertEquals(2, workout.getDayNumber());
        ArgumentCaptor<WorkoutDay> captor = ArgumentCaptor.forClass(WorkoutDay.class);
        verify(workoutDayRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        WorkoutDay rest = captor.getAllValues().get(1);
        assertEquals(54, rest.getDayNumber());
        assertEquals(4, rest.getWeekday());
        assertEquals(PLAN_ID, rest.getPlanId());
        assertEquals(true, rest.isRestDay());
    }

    @Test
    void restoresAnExistingRestRow() {
        WorkoutPlan plan = plan(false);
        WorkoutDay rest = workoutDay(PLAN_ID, 57);
        rest.setRestDay(true);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findByUserIdAndPlanIdAndWeekday(USER_ID, PLAN_ID, 7))
                .thenReturn(Optional.of(rest));
        when(workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(USER_ID, PLAN_ID, 57))
                .thenReturn(Optional.of(rest));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.setRestDay(PLAN_ID, 7);

        assertEquals(7, rest.getWeekday());
        assertEquals(57, rest.getDayNumber());
        verify(workoutDayRepository).save(rest);
    }

    @Test
    void rejectsWeekdaysOutsideIsoRange() {
        assertThrows(IllegalArgumentException.class,
                () -> scheduleSupport.setRestDay(USER_ID, PLAN_ID, 8));
        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
    }

    @Test
    void rejectsAWorkoutDayFromAnotherPlan() {
        WorkoutPlan plan = plan(false);
        WorkoutDay day = workoutDay(OTHER_PLAN_ID, 1);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findByIdAndUserId(20L, USER_ID)).thenReturn(Optional.of(day));

        assertThrows(java.util.NoSuchElementException.class,
                () -> planService.assignWorkoutDay(PLAN_ID, 2, 20L));
        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
    }

    @Test
    void rejectsAnotherUsersPlan() {
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> planService.setRestDay(PLAN_ID, 2));
        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
    }

    @Test
    void rejectsArchivedPlans() {
        WorkoutPlan plan = plan(true);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));

        assertThrows(IllegalArgumentException.class,
                () -> planService.setRestDay(PLAN_ID, 2));
        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
    }

    @Test
    void clearsAnOccupiedWeekdayBeforeAssigningWithoutCreatingADuplicate() {
        WorkoutPlan plan = plan(false);
        WorkoutDay current = workoutDay(PLAN_ID, 1);
        WorkoutDay replacement = workoutDay(PLAN_ID, 2);
        setId(current, 10L);
        setId(replacement, 20L);
        current.setWeekday(3);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findByIdAndUserId(20L, USER_ID)).thenReturn(Optional.of(replacement));
        when(workoutDayRepository.findByUserIdAndPlanIdAndWeekday(USER_ID, PLAN_ID, 3))
                .thenReturn(Optional.of(current));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.assignWorkoutDay(PLAN_ID, 3, 20L);

        assertEquals(null, current.getWeekday());
        assertEquals(3, replacement.getWeekday());
        verify(workoutDayRepository).save(current);
        verify(workoutDayRepository).save(replacement);
    }

    @Test
    void explicitRestRowsPreventLegacyBackfillFromReassigningAWorkout() {
        AppUser legacyUser = new AppUser();
        setId(legacyUser, USER_ID);
        legacyUser.setStartDay(1);
        WorkoutDay workout = workoutDay(PLAN_ID, 1);
        WorkoutDay rest = workoutDay(PLAN_ID, 51);
        rest.setWeekday(1);
        rest.setRestDay(true);
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID))
                .thenReturn(java.util.List.of(workout, rest));
        when(workoutDayRepository.findByUserIdAndPlanIdAndWeekday(USER_ID, PLAN_ID, 1))
                .thenReturn(Optional.of(rest));

        scheduleSupport.ensureSevenDaySchedule(legacyUser, PLAN_ID);

        assertEquals(null, workout.getWeekday());
        verify(workoutDayRepository, never()).save(workout);
    }

    private WorkoutPlan plan(boolean archived) {
        WorkoutPlan plan = org.mockito.Mockito.mock(WorkoutPlan.class);
        org.mockito.Mockito.lenient().when(plan.getId()).thenReturn(PLAN_ID);
        when(plan.isArchived()).thenReturn(archived);
        return plan;
    }

    private WorkoutDay workoutDay(long planId, int dayNumber) {
        WorkoutDay day = new WorkoutDay();
        day.setUserId(USER_ID);
        day.setPlanId(planId);
        day.setDayNumber(dayNumber);
        day.setName("Workout " + dayNumber);
        return day;
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