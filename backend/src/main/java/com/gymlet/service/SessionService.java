package com.gymlet.service;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.Exercise;
import com.gymlet.domain.ExerciseNote;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private static final double INCREMENT_KG = 2.5;

    private final WorkoutSessionRepository sessionRepository;
    private final SetLogRepository setLogRepository;
    private final ExerciseNoteRepository exerciseNoteRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserContext userContext;

    public SessionService(WorkoutSessionRepository sessionRepository,
                          SetLogRepository setLogRepository,
                          ExerciseNoteRepository exerciseNoteRepository,
                          WorkoutDayRepository workoutDayRepository,
                          WorkoutExerciseRepository workoutExerciseRepository,
                          ExerciseRepository exerciseRepository,
                          UserContext userContext) {
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.workoutDayRepository = workoutDayRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.userContext = userContext;
    }

    // -------------------------------------------------------------- start

    /** Starts today's workout in one click, pre-filling each set from last time. */
    @Transactional
    public WorkoutDtos.SessionDto startToday() {
        AppUser user = userContext.getUser();
        LocalDate today = LocalDate.now();
        int idx = (today.getDayOfWeek().getValue() - user.getStartDay() + 7) % 7;
        if (idx >= 5) {
            throw new IllegalArgumentException("It's a rest day — enjoy the break!");
        }
        if (sessionRepository.findFirstByDate(today).isPresent()) {
            throw new IllegalArgumentException("You already have a session for today");
        }

        WorkoutDay day = workoutDayRepository.findByDayNumber(idx + 1)
                .orElseThrow(() -> new NoSuchElementException("Workout day not found"));

        WorkoutSession session = new WorkoutSession();
        session.setDate(today);
        session.setWorkoutDay(day);
        session.setStartedAt(LocalDateTime.now());
        session.setCompleted(false);
        session.setDemo(false);
        sessionRepository.save(session);

        // Pre-fill weight/reps from the last completed session for this day.
        Map<Long, Map<Integer, Double[]>> prefill = new HashMap<>();
        WorkoutSession last = previousSessionOfDay(day, null);
        if (last != null) {
            for (SetLog sl : setLogRepository.findBySession(last)) {
                if (sl.getWeight() != null && sl.getReps() != null) {
                    prefill.computeIfAbsent(sl.getExercise().getId(), k -> new HashMap<>())
                            .put(sl.getSetNumber(), new Double[]{sl.getWeight(), (double) sl.getReps()});
                }
            }
        }

        for (WorkoutExercise we : workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(day.getId())) {
            Map<Integer, Double[]> prev = prefill.get(we.getExercise().getId());
            for (int n = 1; n <= we.getSets(); n++) {
                SetLog sl = new SetLog();
                sl.setSession(session);
                sl.setExercise(we.getExercise());
                sl.setSetNumber(n);
                sl.setCompleted(false);
                if (prev != null && prev.containsKey(n)) {
                    Double[] vals = prev.get(n);
                    sl.setWeight(vals[0]);
                    sl.setReps(vals[1].intValue());
                }
                setLogRepository.save(sl);
            }
        }
        return getSession(session.getId());
    }

    // -------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public WorkoutDtos.SessionDto getSession(Long sessionId) {
        WorkoutSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Workout session not found"));
        return toSessionDto(s);
    }

    @Transactional(readOnly = true)
    public List<WorkoutDtos.HistoryItemDto> getHistory() {
        List<WorkoutSession> sessions = sessionRepository.findAllByOrderByDateDescIdDesc();
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SetLog>> setsBySession = setLogRepository.findBySessionIn(sessions)
                .stream().collect(Collectors.groupingBy(sl -> sl.getSession().getId()));
        List<WorkoutDtos.HistoryItemDto> items = new ArrayList<>();
        for (WorkoutSession s : sessions) {
            List<SetLog> sets = setsBySession.getOrDefault(s.getId(), List.of());
            int completed = (int) sets.stream().filter(SetLog::isCompleted).count();
            double volume = volumeOf(sets);
            int exercises = (int) sets.stream().filter(SetLog::isCompleted)
                    .map(sl -> sl.getExercise().getId()).distinct().count();
            items.add(new WorkoutDtos.HistoryItemDto(
                    s.getId(), s.getDate().toString(), s.getWorkoutDay().getName(),
                    s.isCompleted(), s.isDemo(), completed, s.getDurationMinutes(), exercises,
                    round1(volume)));
        }
        return items;
    }

    // -------------------------------------------------------------- updates

    @Transactional
    public WorkoutDtos.SessionDto updateSet(Long sessionId, Long setId, WorkoutDtos.SetUpdateRequest req) {
        WorkoutSession s = requireSession(sessionId);
        if (s.isCompleted()) {
            throw new IllegalArgumentException("This workout is already finished");
        }
        SetLog sl = setLogRepository.findById(setId)
                .orElseThrow(() -> new NoSuchElementException("Set not found"));
        if (!sl.getSession().getId().equals(sessionId)) {
            throw new IllegalArgumentException("Set does not belong to this session");
        }
        boolean completed = req.completed() != null && req.completed();
        if (completed) {
            if (req.weight() == null || req.weight() <= 0 || req.reps() == null || req.reps() <= 0) {
                throw new IllegalArgumentException("Add weight and reps to complete a set");
            }
        }
        sl.setWeight(req.weight());
        sl.setReps(req.reps());
        sl.setRir(req.rir());
        sl.setCompleted(completed);
        setLogRepository.save(sl);
        return getSession(sessionId);
    }

    @Transactional
    public WorkoutDtos.SessionDto upsertNote(Long sessionId, Long exerciseId, WorkoutDtos.NoteRequest req) {
        WorkoutSession s = requireSession(sessionId);
        if (s.isCompleted()) {
            throw new IllegalArgumentException("This workout is already finished");
        }
        String note = req.note() == null ? "" : req.note().trim();
        ExerciseNote en = exerciseNoteRepository.findBySessionAndExerciseId(s, exerciseId)
                .orElse(null);
        if (note.isEmpty()) {
            if (en != null) {
                exerciseNoteRepository.delete(en);
            }
        } else {
            if (en == null) {
                en = new ExerciseNote();
                en.setSession(s);
                en.setExercise(exerciseRepository.findById(exerciseId)
                        .orElseThrow(() -> new NoSuchElementException("Exercise not found")));
            }
            en.setNote(note);
            exerciseNoteRepository.save(en);
        }
        return getSession(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        WorkoutSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Workout session not found"));
        setLogRepository.deleteBySession(s);
        exerciseNoteRepository.deleteBySession(s);
        sessionRepository.delete(s);
    }

    // -------------------------------------------------------------- finish

    @Transactional
    public WorkoutDtos.FinishSummaryDto finish(Long sessionId, WorkoutDtos.FinishRequest req) {
        WorkoutSession s = requireSession(sessionId);
        if (s.isCompleted()) {
            throw new IllegalArgumentException("This workout is already finished");
        }
        s.setCompleted(true);
        s.setFinishedAt(LocalDateTime.now());
        int duration = req.durationMinutes() != null && req.durationMinutes() > 0
                ? req.durationMinutes()
                : Math.max(1, (int) Math.ceil(Duration.between(s.getStartedAt(), s.getFinishedAt()).toMinutes()));
        s.setDurationMinutes(duration);
        sessionRepository.save(s);

        List<SetLog> sets = setLogRepository.findBySessionOrderByExerciseIdAscSetNumberAsc(s);
        int total = sets.size();
        List<SetLog> done = sets.stream().filter(SetLog::isCompleted).toList();
        int completed = done.size();
        double volume = volumeOf(done);
        int exercisesCompleted = (int) done.stream()
                .map(sl -> sl.getExercise().getId()).distinct().count();

        List<WorkoutDtos.PrDto> prs = computePrs(s, done, volume);
        String message = buildMessage(s, done, completed, total);

        return new WorkoutDtos.FinishSummaryDto(s.getId(), s.getDurationMinutes(), total, completed,
                round1(volume), exercisesCompleted, prs, message);
    }

    private List<WorkoutDtos.PrDto> computePrs(WorkoutSession s, List<SetLog> done, double sessionVolume) {
        // Historical bests (excluding this session).
        Map<Long, double[]> best = new HashMap<>(); // exerciseId -> {highestWeight, bestReps, best1RM}
        List<WorkoutSession> others = sessionRepository.findByCompletedTrueOrderByDateAsc().stream()
                .filter(o -> !o.getId().equals(s.getId()))
                .toList();
        if (!others.isEmpty()) {
            for (SetLog sl : setLogRepository.findBySessionIn(others)) {
                if (sl.isCompleted() && sl.getWeight() != null && sl.getWeight() > 0
                        && sl.getReps() != null && sl.getReps() > 0) {
                    double[] b = best.computeIfAbsent(sl.getExercise().getId(), k -> new double[]{0, 0, 0});
                    b[0] = Math.max(b[0], sl.getWeight());
                    b[1] = Math.max(b[1], sl.getReps());
                    b[2] = Math.max(b[2], est1Rm(sl));
                }
            }
        }

        double bestOtherVolume = 0;
        for (WorkoutSession o : others) {
            bestOtherVolume = Math.max(bestOtherVolume, volumeOf(setLogRepository.findBySession(o)));
        }

        List<WorkoutDtos.PrDto> prs = new ArrayList<>();
        if (sessionVolume > bestOtherVolume && sessionVolume > 0) {
            prs.add(new WorkoutDtos.PrDto(null, "VOLUME", "Biggest session volume",
                    formatVolume(sessionVolume) + " kg total"));
        }

        for (SetLog sl : done) {
            if (sl.getWeight() == null || sl.getWeight() <= 0 || sl.getReps() == null || sl.getReps() <= 0) {
                continue;
            }
            double[] b = best.getOrDefault(sl.getExercise().getId(), new double[]{0, 0, 0});
            String name = sl.getExercise().getName();
            if (sl.getWeight() > b[0] + 0.001) {
                prs.add(new WorkoutDtos.PrDto(name, "WEIGHT", "Heaviest lift",
                        formatWeight(sl.getWeight()) + " kg × " + sl.getReps()));
            }
            double est = est1Rm(sl);
            if (est > b[2] + 0.001) {
                prs.add(new WorkoutDtos.PrDto(name, "ONERM", "Best estimated 1RM",
                        formatWeight(est) + " kg est."));
            }
            if (sl.getReps() > b[1]) {
                prs.add(new WorkoutDtos.PrDto(name, "REPS", "Most reps",
                        formatWeight(sl.getWeight()) + " kg × " + sl.getReps()));
            }
        }
        return prs.stream().limit(8).toList();
    }

    private String buildMessage(WorkoutSession s, List<SetLog> done, int completed, int total) {
        WorkoutSession prev = previousSessionOfDay(s.getWorkoutDay(), s);
        if (prev != null) {
            Map<Long, Integer> prevReps = repTotals(setLogRepository.findBySession(prev));
            Map<Long, Integer> nowReps = repTotals(done);
            int bestDelta = 0;
            String bestExercise = null;
            for (Map.Entry<Long, Integer> e : nowReps.entrySet()) {
                Integer before = prevReps.get(e.getKey());
                if (before != null) {
                    int delta = e.getValue() - before;
                    if (delta > bestDelta) {
                        bestDelta = delta;
                        bestExercise = exerciseRepository.findById(e.getKey()).map(Exercise::getName).orElse(null);
                    }
                }
            }
            if (bestExercise != null && bestDelta > 0) {
                return "Nice session! You added " + bestDelta + " reps to " + bestExercise
                        + " compared with last time.";
            }
            if (completed == total) {
                return "Perfect session — all " + total + " sets completed. Consistency is the magic.";
            }
            return "Solid session — " + completed + " sets logged. See you next time!";
        }
        if (completed == total) {
            return "First time running this one — you knocked out all " + total + " sets. Great start!";
        }
        return "First time running this one — " + completed + " sets logged. Welcome!";
    }

    private WorkoutSession previousSessionOfDay(WorkoutDay day, WorkoutSession exclude) {
        List<WorkoutSession> sessions = sessionRepository
                .findByWorkoutDayIdAndCompletedTrueOrderByDateDesc(day.getId());
        for (WorkoutSession s : sessions) {
            if (exclude == null || !s.getId().equals(exclude.getId())) {
                return s;
            }
        }
        return null;
    }

    private Map<Long, Integer> repTotals(List<SetLog> sets) {
        Map<Long, Integer> totals = new HashMap<>();
        for (SetLog sl : sets) {
            if (sl.isCompleted() && sl.getReps() != null && sl.getReps() > 0) {
                totals.merge(sl.getExercise().getId(), sl.getReps(), Integer::sum);
            }
        }
        return totals;
    }

    // -------------------------------------------------------------- helpers

    private WorkoutSession requireSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workout session not found"));
    }

    private WorkoutDtos.SessionDto toSessionDto(WorkoutSession s) {
        List<SetLog> sets = setLogRepository.findBySession(s);
        Map<Long, Integer> order = new HashMap<>();
        int i = 0;
        for (WorkoutExercise we : workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(s.getWorkoutDay().getId())) {
            order.put(we.getExercise().getId(), i++);
        }
        sets.sort(Comparator
                .comparingInt((SetLog sl) -> order.getOrDefault(sl.getExercise().getId(), 999))
                .thenComparingInt(SetLog::getSetNumber));

        List<WorkoutDtos.SetDto> setDtos = sets.stream()
                .map(sl -> new WorkoutDtos.SetDto(sl.getId(), sl.getExercise().getId(),
                        sl.getExercise().getName(), sl.getSetNumber(), sl.getWeight(), sl.getReps(),
                        sl.getRir(), sl.isCompleted()))
                .toList();
        List<WorkoutDtos.NoteDto> notes = exerciseNoteRepository.findBySession(s).stream()
                .map(n -> new WorkoutDtos.NoteDto(n.getExercise().getId(), n.getNote()))
                .toList();
        int completed = (int) sets.stream().filter(SetLog::isCompleted).count();
        return new WorkoutDtos.SessionDto(s.getId(), s.getDate().toString(), s.getWorkoutDay().getId(),
                s.getWorkoutDay().getName(), s.getDurationMinutes(), s.isCompleted(), s.isDemo(),
                setDtos, notes, sets.size(), completed, round1(volumeOf(sets)));
    }

    private double volumeOf(List<SetLog> sets) {
        return sets.stream()
                .filter(sl -> sl.isCompleted() && sl.getWeight() != null && sl.getWeight() > 0
                        && sl.getReps() != null && sl.getReps() > 0)
                .mapToDouble(sl -> sl.getWeight() * sl.getReps())
                .sum();
    }

    private double est1Rm(SetLog sl) {
        return sl.getWeight() * (1 + sl.getReps() / 30.0);
    }

    private String formatWeight(double w) {
        double r = Math.round(w * 10) / 10.0;
        if (Math.abs(r - Math.round(r)) < 0.001) {
            return String.valueOf(Math.round(r));
        }
        return String.valueOf(r);
    }

    private String formatVolume(double v) {
        return String.format("%,.0f", v);
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
