package com.gymlet.service;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.ExerciseNoteRepository;
import com.gymlet.repository.ExerciseRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutSessionRepository;

@ExtendWith(MockitoExtension.class)
class SessionServicePlanTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 42L;
    private static final long OTHER_PLAN_ID = 99L;

    @Mock
    private WorkoutSessionRepository sessionRepository;
    @Mock
    private SetLogRepository setLogRepository;
    @Mock
    private ExerciseNoteRepository exerciseNoteRepository;
    @Mock
    private WorkoutDayRepository workoutDayRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private PlanService planService;
    @Mock
    private PlanScheduleSupport scheduleSupport;
    @Mock
    private AppUser user;

    private final UserContext userContext = new UserContext();
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        userContext.set(user);
        when(user.getId()).thenReturn(USER_ID);
        lenient().when(user.getStartDay()).thenReturn(1);
        lenient().when(planService.requireActivePlanId(user)).thenReturn(PLAN_ID);
        lenient().when(setLogRepository.findBySession(any())).thenReturn(new ArrayList<>());
        lenient().when(exerciseNoteRepository.findBySession(any())).thenReturn(new ArrayList<>());
        lenient().when(workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(anyLong()))
            .thenReturn(new ArrayList<>());
        sessionService = new SessionService(sessionRepository, setLogRepository, exerciseNoteRepository,
                workoutDayRepository, workoutExerciseRepository, exerciseRepository, userContext, planService,
                scheduleSupport);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void startTodayUsesTheActivePlansScheduledDay() {
        WorkoutDay day = day(PLAN_ID, 2);
        when(scheduleSupport.scheduledWorkout(eq(USER_ID), eq(PLAN_ID), anyInt(), eq(1)))
                .thenReturn(Optional.of(day));
        when(sessionRepository.findByIdAndUserId(any(), eq(USER_ID))).thenReturn(Optional.of(readSession(day)));

        sessionService.startToday();

        ArgumentCaptor<WorkoutSession> captor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(day, captor.getValue().getWorkoutDay());
        assertEquals(PLAN_ID, captor.getValue().getWorkoutDay().getPlanId());
    }

    @Test
    void startCustomSessionCreatesTheCustomDayInsideTheActivePlan() {
        when(workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(USER_ID, PLAN_ID, 6))
                .thenReturn(Optional.empty());
        when(workoutDayRepository.save(any(WorkoutDay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WorkoutDay readDay = day(PLAN_ID, 6);
        when(sessionRepository.findByIdAndUserId(any(), eq(USER_ID))).thenReturn(Optional.of(readSession(readDay)));

        sessionService.startCustomSession();

        ArgumentCaptor<WorkoutDay> dayCaptor = ArgumentCaptor.forClass(WorkoutDay.class);
        verify(workoutDayRepository).save(dayCaptor.capture());
        assertEquals(PLAN_ID, dayCaptor.getValue().getPlanId());
        assertEquals(6, dayCaptor.getValue().getDayNumber());
    }

    @Test
    void startSessionForDayRejectsADayFromAnotherPlanWithoutChangingIt() {
        WorkoutDay day = day(OTHER_PLAN_ID, 1);
        when(workoutDayRepository.findByIdAndUserId(123L, USER_ID)).thenReturn(Optional.of(day));

        assertThrows(java.util.NoSuchElementException.class, () -> sessionService.startSessionForDay(123L));

        verify(sessionRepository, never()).save(any(WorkoutSession.class));
        assertEquals(OTHER_PLAN_ID, day.getPlanId());
    }

    private WorkoutDay day(long planId, int dayNumber) {
        WorkoutDay day = new WorkoutDay();
        day.setUserId(USER_ID);
        day.setPlanId(planId);
        day.setDayNumber(dayNumber);
        day.setName("Workout " + dayNumber);
        return day;
    }

    private WorkoutSession readSession(WorkoutDay day) {
        WorkoutSession session = new WorkoutSession();
        session.setUserId(USER_ID);
        session.setWorkoutDay(day);
        session.setDate(java.time.LocalDate.now());
        session.setStartedAt(java.time.LocalDateTime.now());
        session.setCompleted(false);
        session.setDemo(false);
        return session;
    }
}