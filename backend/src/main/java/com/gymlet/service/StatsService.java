package com.gymlet.service;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.Exercise;
import com.gymlet.domain.SetLog;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.web.dto.StatsDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final WorkoutSessionRepository sessionRepository;
    private final SetLogRepository setLogRepository;
    private final UserContext userContext;

    public StatsService(WorkoutSessionRepository sessionRepository,
                        SetLogRepository setLogRepository,
                        UserContext userContext) {
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.userContext = userContext;
    }

    // ------------------------------------------------------------- strength

    @Transactional(readOnly = true)
    public List<StatsDtos.ExerciseStrengthDto> strengthProgress() {
        List<WorkoutSession> completed = sessionRepository.findByCompletedTrueOrderByDateAsc();
        if (completed.isEmpty()) {
            return List.of();
        }
        Map<Long, Exercise> exercises = new HashMap<>();
        Map<Long, List<SessionSet>> byExercise = new LinkedHashMap<>();
        Map<Long, WorkoutSession> sessionById = completed.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, s -> s));
        for (SetLog sl : setLogRepository.findBySessionIn(completed)) {
            if (sl.isCompleted() && sl.getWeight() != null && sl.getWeight() > 0
                    && sl.getReps() != null && sl.getReps() > 0) {
                exercises.put(sl.getExercise().getId(), sl.getExercise());
                byExercise.computeIfAbsent(sl.getExercise().getId(), k -> new ArrayList<>())
                        .add(new SessionSet(sl.getSession().getId(), sl));
            }
        }
        List<StatsDtos.ExerciseStrengthDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<SessionSet>> e : byExercise.entrySet()) {
            Exercise ex = exercises.get(e.getKey());
            List<SessionSet> sets = e.getValue();
            // group by session, ordered by date
            Map<Long, List<SessionSet>> bySession = sets.stream()
                    .collect(Collectors.groupingBy(ss -> ss.sessionId, LinkedHashMap::new, Collectors.toList()));

            List<StatsDtos.StrengthPointDto> points = new ArrayList<>();
            double totalVolume = 0;
            StatsDtos.BestSetDto bestSet = null;
            double best1Rm = 0;
            for (Map.Entry<Long, List<SessionSet>> en : bySession.entrySet()) {
                WorkoutSession s = sessionById.get(en.getKey());
                List<SetLog> sessionSets = en.getValue().stream().map(ss -> ss.set).toList();
                double topWeight = sessionSets.stream().mapToDouble(SetLog::getWeight).max().orElse(0);
                int bestReps = sessionSets.stream().mapToInt(SetLog::getReps).max().orElse(0);
                SetLog best = sessionSets.stream()
                        .max(Comparator.comparingDouble(this::est1Rm))
                        .orElse(sessionSets.get(0));
                double est = est1Rm(best);
                double volume = sessionSets.stream().mapToDouble(sl -> sl.getWeight() * sl.getReps()).sum();
                totalVolume += volume;
                points.add(new StatsDtos.StrengthPointDto(s.getDate().toString(), round1(topWeight),
                        bestReps, round1(est), round1(volume), sessionSets.size()));
                if (est > best1Rm) {
                    best1Rm = est;
                    bestSet = new StatsDtos.BestSetDto(s.getDate().toString(), best.getWeight(),
                            best.getReps(), round1(est));
                }
            }
            List<StatsDtos.StrengthPointDto> last8 = points.size() > 8
                    ? points.subList(points.size() - 8, points.size())
                    : points;
            result.add(new StatsDtos.ExerciseStrengthDto(ex.getId(), ex.getName(),
                    ex.getMuscleGroup().name(), last8, bestSet, round1(best1Rm), round1(totalVolume)));
        }
        result.sort(Comparator.comparing(StatsDtos.ExerciseStrengthDto::name));
        return result;
    }

    private record SessionSet(Long sessionId, SetLog set) {
    }

    // -------------------------------------------------------------- muscles

    @Transactional(readOnly = true)
    public List<StatsDtos.MuscleDto> muscleVolume() {
        LocalDate from = LocalDate.now().minusDays(6);
        List<WorkoutSession> sessions = sessionRepository.findByCompletedTrueAndDateBetweenOrderByDateAsc(from, LocalDate.now());
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<String, int[]> sets = new HashMap<>();   // muscle -> {sets}
        Map<String, Double> volume = new HashMap<>();
        Map<String, Map<LocalDate, Boolean>> dates = new HashMap<>();
        for (WorkoutSession s : sessions) {
            for (SetLog sl : setLogRepository.findBySession(s)) {
                if (!sl.isCompleted() || sl.getWeight() == null || sl.getWeight() <= 0
                        || sl.getReps() == null || sl.getReps() <= 0) {
                    continue;
                }
                String muscle = sl.getExercise().getMuscleGroup().name();
                sets.computeIfAbsent(muscle, k -> new int[1])[0]++;
                volume.merge(muscle, sl.getWeight() * sl.getReps(), Double::sum);
                dates.computeIfAbsent(muscle, k -> new HashMap<>()).put(s.getDate(), true);
            }
        }
        List<StatsDtos.MuscleDto> result = new ArrayList<>();
        for (String muscle : sets.keySet()) {
            result.add(new StatsDtos.MuscleDto(muscle, sets.get(muscle)[0], round1(volume.get(muscle)),
                    dates.get(muscle).size()));
        }
        result.sort(Comparator.comparingDouble(StatsDtos.MuscleDto::weeklyVolume).reversed());
        return result;
    }

    // ------------------------------------------------------------------ prs

    @Transactional(readOnly = true)
    public StatsDtos.PrSummaryDto prSummary() {
        List<WorkoutSession> completed = sessionRepository.findByCompletedTrueOrderByDateAsc();
        Map<Long, Exercise> exercises = new HashMap<>();
        Map<Long, double[]> best = new HashMap<>(); // exerciseId -> {highestWeight, bestReps, best1RM}
        double bestSessionVolume = 0;
        LocalDate bestVolumeDate = null;
        Map<Long, List<SetLog>> bySession = completed.isEmpty() ? Map.of()
                : setLogRepository.findBySessionIn(completed).stream()
                .collect(Collectors.groupingBy(sl -> sl.getSession().getId()));
        for (WorkoutSession s : completed) {
            double vol = 0;
            for (SetLog sl : bySession.getOrDefault(s.getId(), List.of())) {
                if (sl.isCompleted() && sl.getWeight() != null && sl.getWeight() > 0
                        && sl.getReps() != null && sl.getReps() > 0) {
                    vol += sl.getWeight() * sl.getReps();
                    exercises.put(sl.getExercise().getId(), sl.getExercise());
                    double[] b = best.computeIfAbsent(sl.getExercise().getId(), k -> new double[]{0, 0, 0});
                    b[0] = Math.max(b[0], sl.getWeight());
                    b[1] = Math.max(b[1], sl.getReps());
                    b[2] = Math.max(b[2], est1Rm(sl));
                }
            }
            if (vol > bestSessionVolume) {
                bestSessionVolume = vol;
                bestVolumeDate = s.getDate();
            }
        }
        List<StatsDtos.ExercisePrDto> exercisePrs = exercises.entrySet().stream()
                .map(e -> new StatsDtos.ExercisePrDto(e.getValue().getName(),
                        best.get(e.getKey())[0], (int) best.get(e.getKey())[1],
                        round1(best.get(e.getKey())[2])))
                .sorted(Comparator.comparing(StatsDtos.ExercisePrDto::name))
                .toList();

        int currentStreak = currentStreak();
        int bestStreak = bestStreak(completed);

        return new StatsDtos.PrSummaryDto(exercisePrs, round1(bestSessionVolume),
                bestVolumeDate == null ? null : bestVolumeDate.toString(),
                (long) completed.size(), currentStreak, bestStreak);
    }

    private int currentStreak() {
        LocalDate d = LocalDate.now();
        if (sessionRepository.findFirstByDateAndCompletedTrue(d).isEmpty()) {
            d = d.minusDays(1);
        }
        int streak = 0;
        while (sessionRepository.findFirstByDateAndCompletedTrue(d).isPresent()) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    private int bestStreak(List<WorkoutSession> completed) {
        LocalDate prev = null;
        int current = 0;
        int best = 0;
        for (WorkoutSession s : completed) {
            LocalDate d = s.getDate();
            if (prev != null && d.equals(prev.plusDays(1))) {
                current++;
            } else {
                current = 1;
            }
            best = Math.max(best, current);
            prev = d;
        }
        return best;
    }

    // -------------------------------------------------------------- calendar

    @Transactional(readOnly = true)
    public List<StatsDtos.CalendarDayDto> calendar(int year, int month) {
        AppUser user = userContext.getUser();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate today = LocalDate.now();
        List<StatsDtos.CalendarDayDto> days = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            WorkoutSession s = sessionRepository.findFirstByDate(date).orElse(null);
            String status;
            Integer dayNumber = null;
            Long sessionId = null;
            String name = null;
            if (s != null) {
                status = "WORKOUT";
                sessionId = s.getId();
                dayNumber = s.getWorkoutDay().getDayNumber();
                name = s.getWorkoutDay().getName();
            } else if (date.isAfter(today)) {
                status = "FUTURE";
            } else {
                int idx = (date.getDayOfWeek().getValue() - user.getStartDay() + 7) % 7;
                if (idx >= 5) {
                    status = "REST";
                } else {
                    status = "MISSED";
                    dayNumber = idx + 1;
                }
            }
            days.add(new StatsDtos.CalendarDayDto(date.toString(), status, dayNumber, sessionId, name));
        }
        return days;
    }

    // -------------------------------------------------------------- helpers

    private double est1Rm(SetLog sl) {
        return sl.getWeight() * (1 + sl.getReps() / 30.0);
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
