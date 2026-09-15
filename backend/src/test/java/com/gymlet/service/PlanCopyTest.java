package com.gymlet.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import com.gymlet.domain.Exercise;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutExercise;
import com.gymlet.domain.WorkoutPlan;
import com.gymlet.repository.AppUserRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutPlanRepository;
import com.gymlet.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class PlanCopyTest {

    private static final long USER_ID = 7L;
    private static final long SOURCE_PLAN_ID = 42L;
    private static final long COPIED_PLAN_ID = 84L;

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private WorkoutPlanRepository planRepository;
    @Mock
    private WorkoutDayRepository workoutDayRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Mock
    private WorkoutSessionRepository sessionRepository;
    @Mock
    private PlanScheduleSupport scheduleSupport;

    private final UserContext userContext = new UserContext();
    private AppUser user;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        setId(user, USER_ID);
        user.setActivePlanId(SOURCE_PLAN_ID);
        userContext.set(user);
        planService = new PlanService(userContext, userRepository, planRepository, workoutDayRepository,
                workoutExerciseRepository, sessionRepository, scheduleSupport);
        org.mockito.Mockito.lenient().when(sessionRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void copiesPlanDaysExercisesAndScheduleWithoutHistory() {
        WorkoutPlan source = sourcePlan("Strength", false);
        WorkoutDay training = day(100L, SOURCE_PLAN_ID, 1, "Upper", 2, false);
        WorkoutDay rest = day(101L, SOURCE_PLAN_ID, 51, "Rest", 7, true);
        WorkoutExercise sourceExercise = exercise(training, 3, 1);
        when(planRepository.findByIdAndUserId(SOURCE_PLAN_ID, USER_ID)).thenReturn(Optional.of(source));
        when(planRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Strength copy"))
                .thenReturn(Optional.empty());
        when(planRepository.save(any(WorkoutPlan.class))).thenAnswer(invocation -> {
            WorkoutPlan copy = invocation.getArgument(0);
            setId(copy, COPIED_PLAN_ID);
            return copy;
        });
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, SOURCE_PLAN_ID))
                .thenReturn(List.of(training, rest));
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(new org.mockito.stubbing.Answer<WorkoutDay>() {
            private long nextId = 200L;

            @Override
            public WorkoutDay answer(org.mockito.invocation.InvocationOnMock invocation) {
                WorkoutDay copy = invocation.getArgument(0);
                setId(copy, nextId++);
                return copy;
            }
        });
        when(workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(100L))
                .thenReturn(List.of(sourceExercise));
        when(workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(101L))
                .thenReturn(List.of());
        when(workoutExerciseRepository.save(any(WorkoutExercise.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        planService.copyPlan(SOURCE_PLAN_ID);

        ArgumentCaptor<WorkoutPlan> planCaptor = ArgumentCaptor.forClass(WorkoutPlan.class);
        verify(planRepository).save(planCaptor.capture());
        assertNotSame(source, planCaptor.getValue());
        assertEquals(COPIED_PLAN_ID, planCaptor.getValue().getId());
        assertEquals("Strength copy", planCaptor.getValue().getName());

        ArgumentCaptor<WorkoutDay> dayCaptor = ArgumentCaptor.forClass(WorkoutDay.class);
        verify(workoutDayRepository, org.mockito.Mockito.times(2)).save(dayCaptor.capture());
        WorkoutDay copiedTraining = dayCaptor.getAllValues().get(0);
        WorkoutDay copiedRest = dayCaptor.getAllValues().get(1);
        assertNotSame(training, copiedTraining);
        assertEquals(COPIED_PLAN_ID, copiedTraining.getPlanId());
        assertEquals(1, copiedTraining.getDayNumber());
        assertEquals(2, copiedTraining.getWeekday());
        assertEquals(7, copiedRest.getWeekday());
        assertEquals(51, copiedRest.getDayNumber());
        assertEquals(true, copiedRest.isRestDay());

        ArgumentCaptor<WorkoutExercise> exerciseCaptor = ArgumentCaptor.forClass(WorkoutExercise.class);
        verify(workoutExerciseRepository).save(exerciseCaptor.capture());
        WorkoutExercise copiedExercise = exerciseCaptor.getValue();
        assertNotSame(sourceExercise, copiedExercise);
        assertEquals(copiedTraining, copiedExercise.getWorkoutDay());
        assertEquals(sourceExercise.getExercise(), copiedExercise.getExercise());
        assertEquals(3, copiedExercise.getSets());
        verify(sessionRepository).findAllByUserId(USER_ID);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createsAUniqueCopyNameWithoutChangingTheSource() {
        WorkoutPlan source = sourcePlan("Strength", false);
        when(planRepository.findByIdAndUserId(SOURCE_PLAN_ID, USER_ID)).thenReturn(Optional.of(source));
        WorkoutPlan existingCopy = sourcePlan("Strength copy", false);
        when(planRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Strength copy"))
            .thenReturn(Optional.of(existingCopy));
        when(planRepository.findByUserIdAndNameIgnoreCase(USER_ID, "Strength copy 2"))
                .thenReturn(Optional.empty());
        when(planRepository.save(any(WorkoutPlan.class))).thenAnswer(invocation -> {
            WorkoutPlan copy = invocation.getArgument(0);
            setId(copy, COPIED_PLAN_ID);
            return copy;
        });
        when(workoutDayRepository.findAllByUserIdAndPlanIdOrderByDayNumberAsc(USER_ID, SOURCE_PLAN_ID))
                .thenReturn(List.of());

        planService.copyPlan(SOURCE_PLAN_ID);

        ArgumentCaptor<WorkoutPlan> captor = ArgumentCaptor.forClass(WorkoutPlan.class);
        verify(planRepository).save(captor.capture());
        assertEquals("Strength copy 2", captor.getValue().getName());
        assertEquals("Strength", source.getName());
    }

    @Test
    void rejectsAnotherUsersPlan() {
        when(planRepository.findByIdAndUserId(SOURCE_PLAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> planService.copyPlan(SOURCE_PLAN_ID));

        verify(planRepository, never()).save(any(WorkoutPlan.class));
        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
    }

    @Test
    void rejectsArchivedPlans() {
        WorkoutPlan source = sourcePlan("Old plan", true);
        when(planRepository.findByIdAndUserId(SOURCE_PLAN_ID, USER_ID)).thenReturn(Optional.of(source));

        assertThrows(IllegalArgumentException.class, () -> planService.copyPlan(SOURCE_PLAN_ID));

        verify(planRepository, never()).save(any(WorkoutPlan.class));
        verify(workoutDayRepository, never()).save(any(WorkoutDay.class));
    }

    private WorkoutPlan sourcePlan(String name, boolean archived) {
        WorkoutPlan plan = org.mockito.Mockito.mock(WorkoutPlan.class);
        org.mockito.Mockito.lenient().when(plan.getId()).thenReturn(SOURCE_PLAN_ID);
        org.mockito.Mockito.lenient().when(plan.getName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(plan.getDescription()).thenReturn("A focused plan");
        org.mockito.Mockito.lenient().when(plan.getGoal()).thenReturn("Build strength");
        org.mockito.Mockito.lenient().when(plan.isArchived()).thenReturn(archived);
        return plan;
    }

    private WorkoutDay day(long id, long planId, int dayNumber, String name, int weekday, boolean restDay) {
        WorkoutDay day = new WorkoutDay();
        setId(day, id);
        day.setUserId(USER_ID);
        day.setPlanId(planId);
        day.setDayNumber(dayNumber);
        day.setName(name);
        day.setWeekday(weekday);
        day.setRestDay(restDay);
        return day;
    }

    private WorkoutExercise exercise(WorkoutDay day, int sets, int order) {
        WorkoutExercise workoutExercise = new WorkoutExercise();
        workoutExercise.setWorkoutDay(day);
        workoutExercise.setExercise(new Exercise());
        workoutExercise.setSets(sets);
        workoutExercise.setSetOrder(order);
        workoutExercise.setTargetRir(2);
        workoutExercise.setRestSeconds(90);
        return workoutExercise;
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
