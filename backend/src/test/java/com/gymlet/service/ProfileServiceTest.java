package com.gymlet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymlet.domain.AppUser;
import com.gymlet.domain.Unit;
import com.gymlet.domain.WorkoutPlan;
import com.gymlet.repository.AppUserRepository;
import com.gymlet.repository.BodyWeightLogRepository;
import com.gymlet.repository.ExerciseNoteRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutPlanRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.web.dto.Requests;
import com.gymlet.web.dto.StatsDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 42L;

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private BodyWeightLogRepository bodyWeightRepository;
    @Mock
    private SetLogRepository setLogRepository;
    @Mock
    private ExerciseNoteRepository exerciseNoteRepository;
    @Mock
    private WorkoutSessionRepository sessionRepository;
    @Mock
    private WorkoutPlanRepository planRepository;
    @Mock
    private StructureService structureService;

    private final UserContext userContext = new UserContext();
    private AppUser user;
    private WorkoutPlan plan;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        user = org.mockito.Mockito.mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getName()).thenReturn("Athlete");
        when(user.getUnit()).thenReturn(Unit.KG);
        when(user.getStartDay()).thenReturn(1);
        when(user.getActivePlanId()).thenReturn(PLAN_ID);
        UserContext.set(user);

        plan = org.mockito.Mockito.mock(WorkoutPlan.class);
        when(plan.getName()).thenReturn("My Split");
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));

        profileService = new ProfileService(
                userContext,
                userRepository,
                bodyWeightRepository,
                setLogRepository,
                exerciseNoteRepository,
                sessionRepository,
                planRepository,
                structureService,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void getProfileIncludesActivePlanMetadata() {
        StatsDtos.ProfileDto profile = profileService.getProfile();

        assertEquals(PLAN_ID, profile.activePlanId());
        assertEquals("My Split", profile.activePlanName());
    }

    @Test
    void updateProfileIncludesActivePlanMetadata() {
        StatsDtos.ProfileDto profile = profileService.updateProfile(
                new Requests.ProfileRequest("Updated Athlete", "LB", 2));

        verify(user).setName("Updated Athlete");
        verify(user).setUnit(Unit.LB);
        verify(user).setStartDay(2);
        verify(userRepository).save(user);
        assertEquals(PLAN_ID, profile.activePlanId());
        assertEquals("My Split", profile.activePlanName());
    }
}
