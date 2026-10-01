package com.gymlet.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.WorkoutPlan;
import com.gymlet.repository.ExerciseNoteRepository;
import com.gymlet.repository.ExerciseRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutPlanRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.web.dto.WorkoutDtos;

@ExtendWith(MockitoExtension.class)
class StructureServiceTodayTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 42L;

    @Mock
    private WorkoutDayRepository workoutDayRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private WorkoutSessionRepository sessionRepository;
    @Mock
    private SetLogRepository setLogRepository;
    @Mock
    private ExerciseNoteRepository exerciseNoteRepository;
    @Mock
    private WorkoutPlanRepository planRepository;
    @Mock
    private PlanService planService;
    @Mock
    private PlanScheduleSupport scheduleSupport;

    private final UserContext userContext = new UserContext();
    private AppUser user;
    private StructureService structureService;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        setId(user, USER_ID);
        user.setStartDay(1);
        userContext.set(user);

        WorkoutPlan plan = org.mockito.Mockito.mock(WorkoutPlan.class);
        when(plan.getId()).thenReturn(PLAN_ID);
        when(plan.getName()).thenReturn("My Split");
        when(planService.requireActivePlanId(user)).thenReturn(PLAN_ID);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(scheduleSupport.scheduledWorkout(eq(USER_ID), eq(PLAN_ID), anyInt(), eq(1)))
                .thenReturn(Optional.empty());
        when(sessionRepository.findAllByUserIdAndDateOrderByStartedAtDesc(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(List.of());
        when(scheduleSupport.selectableTrainingDays(USER_ID, PLAN_ID)).thenReturn(List.of());

        structureService = new StructureService(workoutDayRepository, workoutExerciseRepository,
                exerciseRepository, sessionRepository, setLogRepository, exerciseNoteRepository,
                planRepository, userContext, planService, scheduleSupport);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void allRestPlanReturnsARestDayInsteadOfThrowing() {
        WorkoutDtos.TodayDto today = structureService.getToday();

        assertTrue(today.isRestDay());
        assertNull(today.workoutDayId());
        assertNull(today.workoutDayName());
        assertNull(today.dayNumber());
        assertTrue(today.exercises().isEmpty());
        assertTrue(today.availableDays().isEmpty());
        assertEquals(PLAN_ID, today.activePlanId());
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
