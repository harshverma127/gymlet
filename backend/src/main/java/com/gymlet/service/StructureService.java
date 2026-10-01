package com.gymlet.service;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.Exercise;
import com.gymlet.domain.SetLog;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutExercise;
import com.gymlet.domain.WorkoutPlan;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.ExerciseNoteRepository;
import com.gymlet.repository.ExerciseRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutPlanRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.web.dto.WorkoutDtos;
import com.gymlet.web.dto.Requests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class StructureService {

    private final WorkoutDayRepository workoutDayRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final SetLogRepository setLogRepository;
    private final ExerciseNoteRepository exerciseNoteRepository;
    private final WorkoutPlanRepository planRepository;
    private final UserContext userContext;
    private final PlanService planService;
    private final PlanScheduleSupport scheduleSupport;

    public StructureService(WorkoutDayRepository workoutDayRepository,
                            WorkoutExerciseRepository workoutExerciseRepository,
                            ExerciseRepository exerciseRepository,
                            WorkoutSessionRepository sessionRepository,
                            SetLogRepository setLogRepository,
                            ExerciseNoteRepository exerciseNoteRepository,
                            WorkoutPlanRepository planRepository,
                            UserContext userContext,
                            PlanService planService,
                            PlanScheduleSupport scheduleSupport) {
        this.workoutDayRepository = workoutDayRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.planRepository = planRepository;
        this.userContext = userContext;
        this.planService = planService;
        this.scheduleSupport = scheduleSupport;
    }

    // ---------------------------------------------------------------- structure

    @Transactional(readOnly = true)
    public List<WorkoutDtos.WorkoutDayDto> getWorkoutDays() {
        AppUser user = userContext.getUser();
        Long planId = planService.requireActivePlanId(user);
        return scheduleSupport.selectableTrainingDays(user.getId(), planId).stream()
                .map(this::toWorkoutDayDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkoutDtos.WorkoutDayDto getWorkoutDay(Long id) {
        WorkoutDay day = requireTrainingDay(id);
        return toWorkoutDayDto(day);
    }

    @Transactional(readOnly = true)
    public WorkoutDtos.WorkoutDayDto getWorkoutDayByNumber(int dayNumber) {
        AppUser user = userContext.getUser();
        Long planId = planService.requireActivePlanId(user);
        return toWorkoutDayDto(workoutDayRepository.findByUserIdAndPlanIdAndDayNumber(user.getId(), planId, dayNumber)
                .orElseThrow(() -> new NoSuchElementException("Workout day not found")));
    }

    // ------------------------------------------------------------------- today

    @Transactional(readOnly = true)
    public WorkoutDtos.TodayDto getToday() {
        AppUser user = userContext.getUser();
        Long planId = planService.requireActivePlanId(user);
        WorkoutPlan plan = planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Active plan not found"));

        LocalDate today = LocalDate.now();
        int weekday = today.getDayOfWeek().getValue();

        Optional<WorkoutDay> scheduled = scheduleSupport.scheduledWorkout(
                user.getId(), planId, weekday, user.getStartDay());

        List<WorkoutSession> sessionsToday = sessionRepository
                .findAllByUserIdAndDateOrderByStartedAtDesc(user.getId(), today);

        WorkoutSession activeIncomplete = sessionsToday.stream()
                .filter(s -> !s.isCompleted())
                .findFirst()
                .orElse(null);

        List<WorkoutDtos.TodaySessionSummaryDto> sessionSummaries = sessionsToday.stream()
                .map(s -> new WorkoutDtos.TodaySessionSummaryDto(
                        s.getId(),
                        sessionDisplayName(s),
                        s.isCompleted(),
                        s.getStartedAt().toString()))
                .toList();

        WorkoutDay previewDay = null;
        WorkoutSession anchorSession = activeIncomplete;
        if (anchorSession != null) {
            previewDay = anchorSession.getWorkoutDay();
        } else if (scheduled.isPresent()) {
            previewDay = scheduled.get();
        }

        boolean restDay = scheduled.isEmpty() && anchorSession == null;

        // On a rest day "up next" is the next scheduled workout. A freshly created
        // plan may still be entirely rest days — that is a valid state, so this
        // must never throw: the client shows a rest day with nothing queued yet.
        WorkoutDay nextDay = restDay ? nextTrainingDayAfterOrNull(today, user, planId) : null;
        if (restDay) {
            previewDay = nextDay;
        }

        return buildTodayDto(
                user, plan, previewDay, today, restDay, anchorSession, nextDay,
                scheduled.orElse(null), sessionSummaries);
    }

    private WorkoutDay nextTrainingDayAfterOrNull(LocalDate from, AppUser user, Long planId) {
        LocalDate d = from.plusDays(1);
        int startDay = user.getStartDay();
        while (d.isBefore(from.plusDays(8))) {
            int weekday = d.getDayOfWeek().getValue();
            Optional<WorkoutDay> w = scheduleSupport.scheduledWorkout(user.getId(), planId, weekday, startDay);
            if (w.isPresent()) {
                return w.get();
            }
            d = d.plusDays(1);
        }
        return scheduleSupport.selectableTrainingDays(user.getId(), planId).stream()
                .findFirst()
                .orElse(null);
    }

    private WorkoutDtos.TodayDto buildTodayDto(AppUser user, WorkoutPlan plan, WorkoutDay day, LocalDate today,
                                               boolean restDay, WorkoutSession activeSession, WorkoutDay nextDay,
                                               WorkoutDay scheduledDay,
                                               List<WorkoutDtos.TodaySessionSummaryDto> sessionsToday) {
        List<WorkoutDtos.TodayExerciseDto> exercises = new ArrayList<>();
        if (day != null) {
            List<WorkoutExercise> wes = workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(day.getId());
            for (WorkoutExercise we : wes) {
                Exercise ex = we.getExercise();
                LastSessionData last = lastSessionForExercise(ex.getId());
                List<WorkoutDtos.LastSetDto> lastSets = last.sets().stream()
                        .filter(s -> s.isCompleted() && s.getWeight() != null && s.getReps() != null)
                        .map(s -> new WorkoutDtos.LastSetDto(s.getWeight(), s.getReps()))
                        .toList();
                WorkoutDtos.SuggestionDto suggestion = computeSuggestion(ex, last.sets());
                String lastNote = last.note();
                exercises.add(new WorkoutDtos.TodayExerciseDto(
                        ex.getId(), ex.getName(), ex.getMuscleGroup().name(), ex.getRepMin(), ex.getRepMax(),
                        ex.isCompound(), we.getSets(), lastSets, suggestion, lastNote));
            }
        }

        Long planId = plan.getId();
        List<WorkoutDtos.WorkoutDaySummaryDto> availableDays = scheduleSupport
                .selectableTrainingDays(user.getId(), planId).stream()
                .map(d -> new WorkoutDtos.WorkoutDaySummaryDto(d.getId(), d.getDayNumber(), d.getName()))
                .toList();

        return new WorkoutDtos.TodayDto(
                restDay,
                day != null ? day.getDayNumber() : null,
                day != null ? day.getId() : null,
                day != null ? day.getName() : null,
                exercises,
                activeSession != null ? activeSession.getId() : null,
                activeSession != null && activeSession.isCompleted(),
                nextDay != null ? nextDay.getId() : null,
                nextDay != null ? nextDay.getName() : null,
                nextDay != null ? nextDay.getDayNumber() : null,
                availableDays,
                plan.getId(),
                plan.getName(),
                scheduledDay != null ? scheduledDay.getId() : null,
                scheduledDay != null ? scheduledDay.getName() : null,
                sessionsToday);
    }

    static String sessionDisplayName(WorkoutSession s) {
        if (s.getWorkoutDayNameSnapshot() != null && !s.getWorkoutDayNameSnapshot().isBlank()) {
            return s.getWorkoutDayNameSnapshot();
        }
        return s.getWorkoutDay().getName();
    }

    private record LastSessionData(List<SetLog> sets, String note) {
    }

    private LastSessionData lastSessionForExercise(Long exerciseId) {
        List<WorkoutSession> completed = sessionRepository.findByUserIdAndCompletedTrueOrderByDateDesc(userContext.getUserId());
        for (WorkoutSession s : completed) {
            List<SetLog> logs = setLogRepository.findBySessionIdAndExerciseIdOrderBySetNumberAsc(s.getId(), exerciseId);
            if (!logs.isEmpty()) {
                String note = exerciseNoteRepository.findBySessionAndExerciseId(s, exerciseId)
                        .map(n -> n.getNote())
                        .orElse(null);
                return new LastSessionData(logs, note);
            }
        }
        return new LastSessionData(List.of(), null);
    }

    private WorkoutDtos.SuggestionDto computeSuggestion(Exercise ex, List<SetLog> lastSets) {
        List<SetLog> done = lastSets.stream()
                .filter(s -> s.isCompleted() && s.getWeight() != null && s.getWeight() > 0
                        && s.getReps() != null && s.getReps() > 0)
                .toList();
        if (done.isEmpty()) {
            return null;
        }
        double topWeight = done.stream().mapToDouble(SetLog::getWeight).max().orElse(0);
        List<SetLog> topSets = done.stream()
                .filter(s -> Math.abs(s.getWeight() - topWeight) < 0.001)
                .toList();
        double avgReps = topSets.stream().mapToInt(SetLog::getReps).average().orElse(0);
        if (avgReps >= ex.getRepMax()) {
            double next = Math.round((topWeight + 2.5) * 10) / 10.0;
            return new WorkoutDtos.SuggestionDto("INCREASE", next,
                    "Hit the top of the rep range. Try " + formatWeight(next) + " next time.");
        }
        if (avgReps <= ex.getRepMin() - 1) {
            return new WorkoutDtos.SuggestionDto("KEEP", topWeight,
                    "Didn't reach the rep range. Keep the same weight and build reps.");
        }
        return new WorkoutDtos.SuggestionDto("KEEP", topWeight,
                "Right in the rep range. Keep the same weight.");
    }

    private String formatWeight(double w) {
        if (Math.abs(w - Math.round(w)) < 0.001) {
            return String.valueOf(Math.round(w));
        }
        return String.valueOf(w);
    }

    // ------------------------------------------------------------ exercise CRUD

    @Transactional(readOnly = true)
    public List<WorkoutDtos.ExerciseDto> getExercises() {
        return exerciseRepository.findAllByUserIdOrderByNameAsc(userContext.getUserId()).stream()
                .map(this::toExerciseDto).toList();
    }

    @Transactional
    public WorkoutDtos.ExerciseDto createExercise(Requests.ExerciseRequest req) {
        Exercise ex = new Exercise();
        ex.setUserId(userContext.getUserId());
        applyExerciseRequest(ex, req);
        return toExerciseDto(exerciseRepository.save(ex));
    }

    @Transactional
    public WorkoutDtos.ExerciseDto updateExercise(Long id, Requests.ExerciseRequest req) {
        Exercise ex = exerciseRepository.findByIdAndUserId(id, userContext.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Exercise not found"));
        applyExerciseRequest(ex, req);
        return toExerciseDto(exerciseRepository.save(ex));
    }

    private void applyExerciseRequest(Exercise ex, Requests.ExerciseRequest req) {
        if (req.repMax() < req.repMin()) {
            throw new IllegalArgumentException("Rep max must be at least rep min");
        }
        ex.setName(req.name().trim());
        ex.setMuscleGroup(com.gymlet.domain.MuscleGroup.valueOf(req.muscleGroup()));
        ex.setRepMin(req.repMin());
        ex.setRepMax(req.repMax());
        ex.setCompound(req.compound() != null && req.compound());
    }

    @Transactional
    public void deleteExercise(Long id) {
        if (!workoutExerciseRepository.findByExerciseIdAndExerciseUserId(id, userContext.getUserId()).isEmpty()) {
            throw new IllegalArgumentException("This exercise is used in the workout split. Remove it from all days first.");
        }
        exerciseRepository.findByIdAndUserId(id, userContext.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Exercise not found"));
        exerciseRepository.deleteById(id);
    }

    // ------------------------------------------------------ structure editing

    @Transactional
    public WorkoutDtos.WorkoutDayDto addExerciseToDay(Long workoutDayId, Requests.AddExerciseToDayRequest req) {
        WorkoutDay day = requireTrainingDay(workoutDayId);
        Exercise ex = exerciseRepository.findByIdAndUserId(req.exerciseId(), userContext.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Exercise not found"));
        List<WorkoutExercise> existing = workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(workoutDayId);
        if (existing.stream().anyMatch(we -> we.getExercise().getId().equals(req.exerciseId()))) {
            throw new IllegalArgumentException("That exercise is already in this workout");
        }
        WorkoutExercise we = new WorkoutExercise();
        we.setWorkoutDay(day);
        we.setExercise(ex);
        we.setSets(req.sets());
        we.setSetOrder(req.setOrder() != null ? req.setOrder() : existing.size());
        workoutExerciseRepository.save(we);
        return getWorkoutDay(workoutDayId);
    }

    @Transactional
    public WorkoutDtos.WorkoutDayDto updateExerciseInDay(Long id, Requests.UpdateExerciseInDayRequest req) {
        WorkoutExercise we = workoutExerciseRepository.findByIdAndWorkoutDayUserId(id, userContext.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Workout exercise not found"));
        if (req.sets() != null) {
            if (req.sets() < 1) {
                throw new IllegalArgumentException("Sets must be at least 1");
            }
            we.setSets(req.sets());
        }
        if (req.setOrder() != null) {
            we.setSetOrder(req.setOrder());
        }
        workoutExerciseRepository.save(we);
        return getWorkoutDay(we.getWorkoutDay().getId());
    }

    @Transactional
    public WorkoutDtos.WorkoutDayDto removeExerciseFromDay(Long id) {
        WorkoutExercise we = workoutExerciseRepository.findByIdAndWorkoutDayUserId(id, userContext.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Workout exercise not found"));
        Long dayId = we.getWorkoutDay().getId();
        workoutExerciseRepository.delete(we);
        renumberOrders(dayId);
        return getWorkoutDay(dayId);
    }

    private void renumberOrders(Long workoutDayId) {
        List<WorkoutExercise> list = workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(workoutDayId);
        for (int i = 0; i < list.size(); i++) {
            WorkoutExercise we = list.get(i);
            if (we.getSetOrder() != i) {
                we.setSetOrder(i);
                workoutExerciseRepository.save(we);
            }
        }
    }

    @Transactional
    public void swapDayNumbers(Long dayId1, Long dayId2) {
        WorkoutDay d1 = requireTrainingDay(dayId1);
        WorkoutDay d2 = requireTrainingDay(dayId2);
        Integer dayNumber1 = d1.getDayNumber();
        Integer dayNumber2 = d2.getDayNumber();
        Integer weekday1 = d1.getWeekday();
        Integer weekday2 = d2.getWeekday();
        if (java.util.Objects.equals(dayNumber1, dayNumber2)
                && java.util.Objects.equals(weekday1, weekday2)) {
            return;
        }
        // Park d1 on a temporary day_number and clear both weekdays first, so the
        // unique keys (user_id, plan_id, day_number) and (user_id, plan_id, weekday)
        // are never transiently violated while the two rows exchange their slots.
        d1.setDayNumber(-1000 - (dayId1 == null ? 0 : (int) (dayId1 % 1000)));
        d1.setWeekday(null);
        d2.setWeekday(null);
        workoutDayRepository.saveAndFlush(d1);
        workoutDayRepository.saveAndFlush(d2);

        d1.setDayNumber(dayNumber2);
        d1.setWeekday(weekday2);
        d2.setDayNumber(dayNumber1);
        d2.setWeekday(weekday1);
        workoutDayRepository.saveAndFlush(d1);
        workoutDayRepository.saveAndFlush(d2);
    }

    private WorkoutDay requireTrainingDay(Long id) {
        AppUser user = userContext.getUser();
        Long planId = planService.requireActivePlanId(user);
        WorkoutDay day = workoutDayRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Workout day not found"));
        if (day.isRestDay()) {
            throw new IllegalArgumentException("Rest days cannot be edited as workouts");
        }
        if (day.getPlanId() != null && !day.getPlanId().equals(planId)) {
            throw new NoSuchElementException("Workout day not found");
        }
        return day;
    }

    // ---------------------------------------------------------------- mapping

    public WorkoutDtos.WorkoutDayDto toWorkoutDayDto(WorkoutDay day) {
        List<WorkoutDtos.WorkoutExerciseDto> exercises = workoutExerciseRepository
                .findByWorkoutDayIdOrderBySetOrderAsc(day.getId()).stream()
                .map(this::toWorkoutExerciseDto)
                .toList();
        return new WorkoutDtos.WorkoutDayDto(day.getId(), day.getDayNumber(), day.getName(),
                day.getWeekday(), day.isRestDay(), exercises);
    }

    public WorkoutDtos.WorkoutExerciseDto toWorkoutExerciseDto(WorkoutExercise we) {
        Exercise ex = we.getExercise();
        return new WorkoutDtos.WorkoutExerciseDto(we.getId(), ex.getId(), ex.getName(),
                ex.getMuscleGroup().name(), ex.getRepMin(), ex.getRepMax(), ex.isCompound(),
                we.getSets(), we.getSetOrder());
    }

    public WorkoutDtos.ExerciseDto toExerciseDto(Exercise ex) {
        return new WorkoutDtos.ExerciseDto(ex.getId(), ex.getName(), ex.getMuscleGroup().name(),
                ex.getRepMin(), ex.getRepMax(), ex.isCompound());
    }
}
