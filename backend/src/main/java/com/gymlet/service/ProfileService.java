package com.gymlet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymlet.domain.AppUser;
import com.gymlet.domain.Unit;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.BodyWeightLogRepository;
import com.gymlet.repository.ExerciseNoteRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.web.dto.Requests;
import com.gymlet.web.dto.StatsDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProfileService {

    private final UserContext userContext;
    private final BodyWeightLogRepository bodyWeightRepository;
    private final SetLogRepository setLogRepository;
    private final ExerciseNoteRepository exerciseNoteRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final StructureService structureService;
    private final ObjectMapper objectMapper;

    public ProfileService(UserContext userContext,
                          BodyWeightLogRepository bodyWeightRepository,
                          SetLogRepository setLogRepository,
                          ExerciseNoteRepository exerciseNoteRepository,
                          WorkoutSessionRepository sessionRepository,
                          StructureService structureService,
                          ObjectMapper objectMapper) {
        this.userContext = userContext;
        this.bodyWeightRepository = bodyWeightRepository;
        this.setLogRepository = setLogRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.sessionRepository = sessionRepository;
        this.structureService = structureService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public StatsDtos.ProfileDto getProfile() {
        AppUser user = userContext.getUser();
        return new StatsDtos.ProfileDto(user.getName(), user.getUnit().name(), user.getStartDay());
    }

    @Transactional
    public StatsDtos.ProfileDto updateProfile(Requests.ProfileRequest req) {
        AppUser user = userContext.getUser();
        user.setName(req.name().trim());
        user.setUnit(Unit.valueOf(req.unit()));
        user.setStartDay(req.startDay());
        return new StatsDtos.ProfileDto(user.getName(), user.getUnit().name(), user.getStartDay());
    }

    /** Exports the entire personal dataset as a JSON string. */
    @Transactional(readOnly = true)
    public String exportJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "gymlet");
        data.put("exportedAt", java.time.LocalDateTime.now().toString());
        data.put("profile", getProfile());
        data.put("workoutDays", structureService.getWorkoutDays());
        List<Map<String, Object>> sessions = new java.util.ArrayList<>();
        for (WorkoutSession s : sessionRepository.findAllByOrderByDateDescIdDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("date", s.getDate().toString());
            m.put("workoutDay", s.getWorkoutDay().getName());
            m.put("durationMinutes", s.getDurationMinutes());
            m.put("completed", s.isCompleted());
            m.put("demo", s.isDemo());
            sessions.add(m);
        }
        data.put("sessions", sessions);
        data.put("bodyWeight", bodyWeightRepository.findAllByOrderByDateAsc());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export data", e);
        }
    }

    /** Wipes all workout history and bodyweight logs, keeping the split + profile. */
    @Transactional
    public void resetData() {
        List<WorkoutSession> sessions = sessionRepository.findAll();
        if (!sessions.isEmpty()) {
            setLogRepository.deleteAll(setLogRepository.findBySessionIn(sessions));
            for (WorkoutSession s : sessions) {
                exerciseNoteRepository.deleteBySession(s);
            }
            sessionRepository.deleteAll(sessions);
        }
        bodyWeightRepository.deleteAll();
    }

    /** Removes the sample data seeded on first launch. */
    @Transactional
    public void removeDemoData() {
        List<WorkoutSession> demo = sessionRepository.findAll().stream()
                .filter(WorkoutSession::isDemo)
                .toList();
        if (!demo.isEmpty()) {
            setLogRepository.deleteAll(setLogRepository.findBySessionIn(demo));
            for (WorkoutSession s : demo) {
                exerciseNoteRepository.deleteBySession(s);
            }
            sessionRepository.deleteAll(demo);
        }
        bodyWeightRepository.deleteAll(
                bodyWeightRepository.findAllByOrderByDateAsc().stream()
                        .filter(l -> l.isDemo())
                        .toList());
    }
}
