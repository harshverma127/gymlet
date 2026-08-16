package com.gymlet.service;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.Exercise;
import com.gymlet.domain.MuscleGroup;
import com.gymlet.domain.SetLog;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutExercise;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.ExerciseNoteRepository;
import com.gymlet.repository.ExerciseRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.web.dto.WorkoutDtos;
import com.gymlet.web.dto.Requests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class StructureService {

    private final WorkoutDayRepository workoutDayRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final SetLogRepository setLogRepository;
    private final ExerciseNoteRepository exerciseNoteRepository;
    private final UserContext userContext;

    public StructureService(WorkoutDayRepository workoutDayRepository,
                            WorkoutExerciseRepository workoutExerciseRepository,
                            ExerciseRepository exerciseRepository,
                            WorkoutSessionRepository sessionRepository,
                            SetLogRepository setLogRepository,
                            ExerciseNoteRepository exerciseNoteRepository,
                            UserContext userContext) {
        this.workoutDayRepository = workoutDayRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.userContext = userContext;
    }

    // ---------------------------------------------------------------- structure

    @Transactional(readOnly = true)
    public List<WorkoutDtos.WorkoutDayDto> getWorkoutDays() {
        return workoutDayRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDayNumber(), b.getDayNumber()))
                .map(this::toWorkoutDayDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkoutDtos.WorkoutDayDto getWorkoutDay(Long id) {
        return toWorkoutDayDto(workoutDayRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workout day not found")));
    }

    @Transactional(readOnly = true)
    public WorkoutDtos.WorkoutDayDto getWorkoutDayByNumber(int dayNumber) {
        return toWorkoutDayDto(workoutDayRepository.findByDayNumber(dayNumber)
                .orElseThrow(() -> new NoSuchElementException("Workout day not found")));
    }

    // ------------------------------------------------------------------- today

    /**
     * The core "what am I doing today" payload.
     * Days 1..5 map onto the week starting from the user's chosen start day;
     * the two remaining slots are rest days.
     */
    @Transactional(readOnly = true)
    public WorkoutDtos.TodayDto getToday() {
        AppUser user = userContext.getUser();
        LocalDate today = LocalDate.now();
        int idx = (today.getDayOfWeek().getValue() - user.getStartDay() + 7) % 7; // 0..6
        boolean restDay = idx >= 5;

        WorkoutSession existing = sessionRepository.findFirstByDate(today).orElse(null);
        if (existing != null) {
            WorkoutDay day = existing.getWorkoutDay();
            return buildTodayDto(day, today, existing, null);
        }

        if (restDay) {
            WorkoutDay next = nextTrainingDayAfter(today, user.getStartDay());
            return buildTodayDto(next, today, null, next);
        }

        WorkoutDay day = workoutDayRepository.findByDayNumber(idx + 1)
                .orElseThrow(() -> new NoSuchElementException("Workout day not found"));
        return buildTodayDto(day, today, null, null);
    }

    private WorkoutDay nextTrainingDayAfter(LocalDate from, int startDay) {
        LocalDate d = from.plusDays(1);
        while (true) {
            int i = (d.getDayOfWeek().getValue() - startDay + 7) % 7;
            if (i < 5) {
                int dayNumber = i + 1;
                return workoutDayRepository.findByDayNumber(dayNumber)
                        .orElseThrow(() -> new NoSuchElementException("Workout day not found"));
            }
            d = d.plusDays(1);
        }
    }

    private WorkoutDtos.TodayDto buildTodayDto(WorkoutDay day, LocalDate today,
                                               WorkoutSession existing, WorkoutDay nextDay) {
        List<WorkoutExercise> wes = workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(day.getId());
        List<WorkoutDtos.TodayExerciseDto> exercises = new ArrayList<>();
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
        return new WorkoutDtos.TodayDto(
                existing == null && (today.getDayOfWeek().getValue() - userContext.getUser().getStartDay() + 7) % 7 >= 5,
                day.getDayNumber(), day.getId(), day.getName(), exercises,
                existing != null ? existing.getId() : null,
                existing != null && existing.isCompleted(),
                nextDay != null ? nextDay.getId() : null,
                nextDay != null ? nextDay.getName() : null,
                nextDay != null ? nextDay.getDayNumber() : null);
    }

    private record LastSessionData(List<SetLog> sets, String note) {
    }

    /** Last completed session's sets (and optional note) for an exercise, across all history. */
    private LastSessionData lastSessionForExercise(Long exerciseId) {
        List<WorkoutSession> completed = sessionRepository.findByCompletedTrueOrderByDateDesc();
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

    /** Simple, understandable progression rule based on the previous session. */
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
        return exerciseRepository.findAllByOrderByNameAsc().stream().map(this::toExerciseDto).toList();
    }

    @Transactional
    public WorkoutDtos.ExerciseDto createExercise(Requests.ExerciseRequest req) {
        Exercise ex = new Exercise();
        applyExerciseRequest(ex, req);
        return toExerciseDto(exerciseRepository.save(ex));
    }

    @Transactional
    public WorkoutDtos.ExerciseDto updateExercise(Long id, Requests.ExerciseRequest req) {
        Exercise ex = exerciseRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Exercise not found"));
        applyExerciseRequest(ex, req);
        return toExerciseDto(exerciseRepository.save(ex));
    }

    private void applyExerciseRequest(Exercise ex, Requests.ExerciseRequest req) {
        if (req.repMax() < req.repMin()) {
            throw new IllegalArgumentException("Rep max must be at least rep min");
        }
        ex.setName(req.name().trim());
        ex.setMuscleGroup(MuscleGroup.valueOf(req.muscleGroup()));
        ex.setRepMin(req.repMin());
        ex.setRepMax(req.repMax());
        ex.setCompound(req.compound() != null && req.compound());
    }

    @Transactional
    public void deleteExercise(Long id) {
        if (!workoutExerciseRepository.findByExerciseId(id).isEmpty()) {
            throw new IllegalArgumentException("This exercise is used in the workout split. Remove it from all days first.");
        }
        exerciseRepository.deleteById(id);
    }

    // ------------------------------------------------------ structure editing

    @Transactional
    public WorkoutDtos.WorkoutDayDto addExerciseToDay(Long workoutDayId, Requests.AddExerciseToDayRequest req) {
        WorkoutDay day = workoutDayRepository.findById(workoutDayId)
                .orElseThrow(() -> new NoSuchElementException("Workout day not found"));
        Exercise ex = exerciseRepository.findById(req.exerciseId())
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
        WorkoutExercise we = workoutExerciseRepository.findById(id)
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
        WorkoutExercise we = workoutExerciseRepository.findById(id)
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

    // ---------------------------------------------------------------- mapping

    public WorkoutDtos.WorkoutDayDto toWorkoutDayDto(WorkoutDay day) {
        List<WorkoutDtos.WorkoutExerciseDto> exercises = workoutExerciseRepository
                .findByWorkoutDayIdOrderBySetOrderAsc(day.getId()).stream()
                .map(this::toWorkoutExerciseDto)
                .toList();
        return new WorkoutDtos.WorkoutDayDto(day.getId(), day.getDayNumber(), day.getName(), exercises);
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
