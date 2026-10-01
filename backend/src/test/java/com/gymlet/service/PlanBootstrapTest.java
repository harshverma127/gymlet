package com.gymlet.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
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

/**
 * The boot path and the registration path must both leave a user with an
 * active plan and a coherent schedule — otherwise a brand-new account has
 * "No active workout plan" until the next server restart.
 */
@ExtendWith(MockitoExtension.class)
class PlanBootstrapTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 42L;

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
        user.setStartDay(1);
        userContext.set(user);
        planService = new PlanService(userContext, userRepository, planRepository, workoutDayRepository,
                workoutExerciseRepository, sessionRepository, scheduleSupport);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createsMySplitAndAttachesOrphanDaysWhenNoActivePlan() {
        when(planRepository.findByUserIdAndNameIgnoreCase(USER_ID, "My Split")).thenReturn(Optional.empty());
        when(planRepository.save(any(WorkoutPlan.class))).thenAnswer(invocation -> {
            WorkoutPlan plan = invocation.getArgument(0);
            setId(plan, PLAN_ID);
            return plan;
        });
        WorkoutDay orphan = new WorkoutDay();
        setId(orphan, 5L);
        orphan.setUserId(USER_ID);
        when(workoutDayRepository.findByUserIdAndPlanIdIsNull(USER_ID)).thenReturn(List.of(orphan));

        planService.ensurePlanForUser(user);

        assertEquals(PLAN_ID, user.getActivePlanId());
        assertEquals(PLAN_ID, orphan.getPlanId());
        verify(workoutDayRepository).save(orphan);
        verify(scheduleSupport).ensureSevenDaySchedule(user, PLAN_ID);
    }

    @Test
    void keepsAnExistingActivePlanAndOnlyReconcilesTheSchedule() {
        user.setActivePlanId(PLAN_ID);
        WorkoutPlan plan = org.mockito.Mockito.mock(WorkoutPlan.class);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));

        planService.ensurePlanForUser(user);

        assertEquals(PLAN_ID, user.getActivePlanId());
        verify(scheduleSupport).ensureSevenDaySchedule(user, PLAN_ID);
        verify(planRepository, org.mockito.Mockito.never()).save(any(WorkoutPlan.class));
        verify(workoutDayRepository, org.mockito.Mockito.never()).findByUserIdAndPlanIdIsNull(any());
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
