package com.gymlet.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.gymlet.web.dto.PlanDtos;

@ExtendWith(MockitoExtension.class)
class ScheduleMutationTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long PLAN_ID = 42L;
    private static final long OTHER_PLAN_ID = 99L;

    @Mock
    private WorkoutDayRepository workoutDayRepository;
    @Mock
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
        when(workoutDayRepository.findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(USER_ID, PLAN_ID, 3))
                .thenReturn(List.of());
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
        when(workoutDayRepository.findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(USER_ID, PLAN_ID, 4))
                .thenReturn(List.of(workout));
        when(workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(USER_ID, PLAN_ID, 54))
                .thenReturn(Optional.empty());
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.setRestDay(PLAN_ID, 4);

        assertNull(workout.getWeekday());
        assertEquals(2, workout.getDayNumber());
        ArgumentCaptor<WorkoutDay> captor = ArgumentCaptor.forClass(WorkoutDay.class);
        verify(workoutDayRepository, times(2)).save(captor.capture());
        WorkoutDay rest = captor.getAllValues().get(1);
        assertEquals(54, rest.getDayNumber());
        assertEquals(4, rest.getWeekday());
        assertEquals(PLAN_ID, rest.getPlanId());
        assertTrue(rest.isRestDay());
    }

    @Test
    void restoresAnExistingRestRow() {
        WorkoutPlan plan = plan(false);
        WorkoutDay rest = workoutDay(PLAN_ID, 57);
        rest.setRestDay(true);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(USER_ID, PLAN_ID, 7))
                .thenReturn(List.of(rest));
        when(workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(USER_ID, PLAN_ID, 57))
                .thenReturn(Optional.of(rest));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.setRestDay(PLAN_ID, 7);

        assertEquals(7, rest.getWeekday());
        assertEquals(57, rest.getDayNumber());
        verify(workoutDayRepository).save(rest);
    }

    @Test
    void convertsARestWeekdayIntoAWorkoutInPlace() {
        WorkoutPlan plan = plan(false);
        WorkoutDay rest = workoutDay(PLAN_ID, 53);
        setId(rest, 30L);
        rest.setWeekday(3);
        rest.setRestDay(true);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(USER_ID, PLAN_ID, 3))
                .thenReturn(List.of(rest));
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID))
                .thenReturn(List.of(rest));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanDtos.ScheduleDayDto dto = planService.createWorkoutDay(PLAN_ID, 3, "Legs");

        // Same row id: the rest row is transformed, not duplicated.
        assertEquals(30L, dto.id());
        assertEquals(3, dto.weekday());
        assertEquals(7, dto.dayNumber());
        assertEquals("Legs", dto.name());
        assertEquals(false, dto.restDay());
        assertEquals(3, rest.getWeekday());
        assertEquals(7, rest.getDayNumber());
        assertEquals(false, rest.isRestDay());
        verify(workoutDayRepository, times(1)).save(rest);
    }

    @Test
    void renamingAScheduledWorkoutDoesNotCreateADuplicate() {
        WorkoutPlan plan = plan(false);
        WorkoutDay training = workoutDay(PLAN_ID, 2);
        setId(training, 20L);
        training.setWeekday(3);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(USER_ID, PLAN_ID, 3))
                .thenReturn(List.of(training));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanDtos.ScheduleDayDto dto = planService.createWorkoutDay(PLAN_ID, 3, "Legs");

        assertEquals("Legs", training.getName());
        assertEquals(3, dto.weekday());
        assertEquals(2, dto.dayNumber());
        verify(workoutDayRepository, times(1)).save(any(WorkoutDay.class));
    }

    @Test
    void convertsAWorkoutWeekdayToRestReusingTheReservedRow() {
        WorkoutPlan plan = plan(false);
        WorkoutDay workout = workoutDay(PLAN_ID, 53);
        setId(workout, 40L);
        workout.setWeekday(3);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(workoutDayRepository.findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(USER_ID, PLAN_ID, 3))
                .thenReturn(List.of(workout));
        when(workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(USER_ID, PLAN_ID, 53))
                .thenReturn(Optional.empty());
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.setRestDay(PLAN_ID, 3);

        assertTrue(workout.isRestDay());
        assertEquals("Rest", workout.getName());
        assertEquals(3, workout.getWeekday());
        assertEquals(53, workout.getDayNumber());
        verify(workoutDayRepository, times(2)).save(workout);
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
        when(workoutDayRepository.findAllByUserIdAndPlanIdAndWeekdayOrderByIdAsc(USER_ID, PLAN_ID, 3))
                .thenReturn(List.of(current));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        planService.assignWorkoutDay(PLAN_ID, 3, 20L);

        assertNull(current.getWeekday());
        assertEquals(3, replacement.getWeekday());
        verify(workoutDayRepository).save(current);
        verify(workoutDayRepository).save(replacement);
    }

    @Test
    void doesNotDuplicateAnAlreadyCompleteSevenDaySchedule() {
        AppUser u = new AppUser();
        setId(u, USER_ID);
        u.setStartDay(1);
        List<WorkoutDay> rows = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            WorkoutDay rest = workoutDay(PLAN_ID, 50 + weekday);
            setId(rest, 100L + weekday);
            rest.setRestDay(true);
            rest.setWeekday(weekday);
            rows.add(rest);
        }
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID))
                .thenReturn(rows);

        scheduleSupport.ensureSevenDaySchedule(u, PLAN_ID);

        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
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
                .thenReturn(new ArrayList<>(List.of(workout, rest)));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduleSupport.ensureSevenDaySchedule(legacyUser, PLAN_ID);

        // An explicit weekday anywhere means the plan is treated as an explicit
        // schedule, so the legacy slot mapping must not grab a weekday.
        assertNull(workout.getWeekday());
        verify(workoutDayRepository, never()).save(workout);
    }

    @Test
    void ensureSevenDayScheduleCollapsesDuplicateWeekdaysWithoutDeleting() {
        AppUser u = new AppUser();
        setId(u, USER_ID);
        u.setStartDay(1);
        WorkoutDay workout = workoutDay(PLAN_ID, 1);
        setId(workout, 10L);
        workout.setWeekday(3);
        WorkoutDay rest = workoutDay(PLAN_ID, 53);
        setId(rest, 11L);
        rest.setWeekday(3);
        rest.setRestDay(true);
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, PLAN_ID))
                .thenReturn(new ArrayList<>(List.of(workout, rest)));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduleSupport.ensureSevenDaySchedule(u, PLAN_ID);

        assertEquals(3, workout.getWeekday());
        assertNull(rest.getWeekday());
        verify(workoutDayRepository, never()).delete(any(WorkoutDay.class));
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
