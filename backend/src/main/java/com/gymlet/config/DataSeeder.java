package com.gymlet.config;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.BodyWeightLog;
import com.gymlet.domain.Exercise;
import com.gymlet.domain.MuscleGroup;
import com.gymlet.domain.SetLog;
import com.gymlet.domain.Unit;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutExercise;
import com.gymlet.domain.WorkoutSession;
import com.gymlet.repository.AppUserRepository;
import com.gymlet.repository.BodyWeightLogRepository;
import com.gymlet.repository.ExerciseRepository;
import com.gymlet.repository.SetLogRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the shared default 5-day split as a template (rows with user_id NULL).
 * Every new account gets an independent copy of it when it registers.
 *
 * With gymlet.seed-legacy-demo=true (dev/testing only) it also recreates the
 * pre-multi-user single-user world (legacy user + sample history) so the
 * migration in {@link SchemaMigration} can be exercised end-to-end.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository userRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final SetLogRepository setLogRepository;
    private final BodyWeightLogRepository bodyWeightRepository;

    private final boolean seedLegacyDemo;

    public DataSeeder(AppUserRepository userRepository,
                      WorkoutDayRepository workoutDayRepository,
                      ExerciseRepository exerciseRepository,
                      WorkoutExerciseRepository workoutExerciseRepository,
                      WorkoutSessionRepository sessionRepository,
                      SetLogRepository setLogRepository,
                      BodyWeightLogRepository bodyWeightRepository,
                      @Value("${gymlet.seed-legacy-demo:false}") boolean seedLegacyDemo) {
        this.userRepository = userRepository;
        this.workoutDayRepository = workoutDayRepository;
        this.exerciseRepository = exerciseRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.bodyWeightRepository = bodyWeightRepository;
        this.seedLegacyDemo = seedLegacyDemo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (exerciseRepository.countByUserIdIsNull() == 0) {
            seedTemplate();
            log.info("Seeded the default 5-day workout template");
        }
        if (seedLegacyDemo && userRepository.count() == 0) {
            seedLegacyDemoWorld();
            log.info("Seeded legacy single-user demo world (gymlet.seed-legacy-demo=true)");
        }
    }

    // ------------------------------------------------------------- template

    private record Seed(String name, MuscleGroup group, int repMin, int repMax, boolean compound, int sets) {
    }

    private void seedTemplate() {
        Map<Integer, List<Seed>> days = Map.of(
                1, List.of(
                        new Seed("Lat Pulldown", MuscleGroup.BACK, 6, 10, true, 3),
                        new Seed("Mid-Back Row", MuscleGroup.BACK, 8, 12, true, 2),
                        new Seed("Upper-Back Row", MuscleGroup.BACK, 8, 12, true, 2),
                        new Seed("Dumbbell Pullover", MuscleGroup.BACK, 10, 15, true, 2),
                        new Seed("Shrugs", MuscleGroup.BACK, 8, 15, false, 3),
                        new Seed("Rear Delt Fly", MuscleGroup.SHOULDERS, 12, 20, false, 2),
                        new Seed("Incline Bench", MuscleGroup.CHEST, 6, 10, true, 3),
                        new Seed("Flat Bench", MuscleGroup.CHEST, 6, 10, true, 3),
                        new Seed("Fly", MuscleGroup.CHEST, 10, 15, false, 3)),
                2, List.of(
                        new Seed("Shoulder Press", MuscleGroup.SHOULDERS, 6, 10, true, 3),
                        new Seed("Lateral Raise", MuscleGroup.SHOULDERS, 10, 20, false, 4),
                        new Seed("Rear Delt Fly", MuscleGroup.SHOULDERS, 12, 20, false, 3),
                        new Seed("Curl", MuscleGroup.BICEPS, 6, 10, false, 3),
                        new Seed("Hammer Curl", MuscleGroup.BICEPS, 8, 12, false, 3),
                        new Seed("Pushdown", MuscleGroup.TRICEPS, 8, 12, false, 3),
                        new Seed("Overhead Dumbbell Extension", MuscleGroup.TRICEPS, 10, 15, false, 3),
                        new Seed("Reverse Curl", MuscleGroup.FOREARMS, 10, 15, false, 2),
                        new Seed("Behind-the-Back Wrist Curl", MuscleGroup.FOREARMS, 12, 20, false, 2)),
                3, List.of(
                        new Seed("Barbell Squat", MuscleGroup.LEGS, 6, 10, true, 3),
                        new Seed("Romanian Deadlift", MuscleGroup.LEGS, 6, 10, true, 3),
                        new Seed("Leg Extension", MuscleGroup.LEGS, 10, 15, false, 3),
                        new Seed("Hamstring Curl", MuscleGroup.LEGS, 10, 15, false, 3),
                        new Seed("Calf Raise", MuscleGroup.LEGS, 10, 20, false, 4),
                        new Seed("Leg Raise Machine", MuscleGroup.ABS, 10, 20, false, 3)),
                4, List.of(
                        new Seed("Lat Pulldown", MuscleGroup.BACK, 8, 12, true, 3),
                        new Seed("One-Arm Dumbbell Row", MuscleGroup.BACK, 8, 12, true, 3),
                        new Seed("Upper-Back Row", MuscleGroup.BACK, 10, 12, true, 2),
                        new Seed("Dumbbell Pullover", MuscleGroup.BACK, 10, 15, true, 2),
                        new Seed("Shrugs", MuscleGroup.BACK, 10, 15, false, 3),
                        new Seed("Rear Delt Fly", MuscleGroup.SHOULDERS, 12, 20, false, 2),
                        new Seed("Incline Bench", MuscleGroup.CHEST, 8, 12, true, 3),
                        new Seed("Flat Bench", MuscleGroup.CHEST, 8, 12, true, 3),
                        new Seed("Fly", MuscleGroup.CHEST, 12, 15, false, 2)),
                5, List.of(
                        new Seed("Dumbbell Shoulder Press", MuscleGroup.SHOULDERS, 8, 12, true, 3),
                        new Seed("Lateral Raise", MuscleGroup.SHOULDERS, 12, 20, false, 4),
                        new Seed("Rear Delt Fly", MuscleGroup.SHOULDERS, 12, 20, false, 3),
                        new Seed("Incline Dumbbell Curl", MuscleGroup.BICEPS, 8, 12, false, 3),
                        new Seed("Hammer Curl", MuscleGroup.BICEPS, 8, 12, false, 3),
                        new Seed("Pushdown", MuscleGroup.TRICEPS, 10, 15, false, 3),
                        new Seed("Overhead Dumbbell Extension", MuscleGroup.TRICEPS, 8, 12, false, 3),
                        new Seed("Reverse Curl", MuscleGroup.FOREARMS, 10, 15, false, 2)));

        Map<String, Exercise> byName = new HashMap<>();
        // Iterate days 1..5 in order: shared exercises (e.g. Lat Pulldown,
        // Incline Bench) keep the Day A rep range, deterministically.
        for (int dayNumber = 1; dayNumber <= 5; dayNumber++) {
            List<Seed> seeds = days.get(dayNumber);
            WorkoutDay day = new WorkoutDay();
            day.setDayNumber(dayNumber);
            day.setName(dayNumber <= 2
                    ? (dayNumber == 1 ? "Back + Chest A" : "Shoulders + Arms A")
                    : (dayNumber == 3 ? "Legs + Abs"
                    : (dayNumber == 4 ? "Back + Chest B" : "Shoulders + Arms B")));
            workoutDayRepository.save(day);
            for (int i = 0; i < seeds.size(); i++) {
                Seed s = seeds.get(i);
                Exercise ex = byName.computeIfAbsent(s.name(), k -> {
                    Exercise e = new Exercise();
                    e.setName(s.name());
                    e.setMuscleGroup(s.group());
                    e.setRepMin(s.repMin());
                    e.setRepMax(s.repMax());
                    e.setCompound(s.compound());
                    return exerciseRepository.save(e);
                });
                WorkoutExercise we = new WorkoutExercise();
                we.setWorkoutDay(day);
                we.setExercise(ex);
                we.setSetOrder(i);
                we.setSets(s.sets());
                workoutExerciseRepository.save(we);
            }
        }
    }

    // ---------------------------------------------------- legacy demo world

    /**
     * Recreates the pre-upgrade single-user state (legacy user with no
     * credentials + sample history), used only to test the migration path.
     */
    private void seedLegacyDemoWorld() {
        AppUser user = new AppUser();
        user.setName("Athlete");
        user.setUnit(Unit.KG);
        user.setStartDay(1);
        userRepository.save(user);

        List<WorkoutDay> templateDays = workoutDayRepository.findByUserIdIsNullOrderByDayNumberAsc();
        Map<Long, Integer> sessionCountByExercise = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (int back = 27; back >= 1; back--) {
            LocalDate date = today.minusDays(back);
            int idx = (date.getDayOfWeek().getValue() - user.getStartDay() + 7) % 7;
            if (idx >= 5) {
                continue; // rest day
            }
            WorkoutDay day = templateDays.get(idx);

            WorkoutSession session = new WorkoutSession();
            session.setDate(date);
            session.setWorkoutDay(day);
            session.setStartedAt(date.atTime(17, 0));
            session.setCompleted(true);
            session.setDemo(true);

            List<WorkoutExercise> wes = workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(day.getId());
            int totalSets = wes.stream().mapToInt(WorkoutExercise::getSets).sum();
            session.setDurationMinutes(38 + totalSets);
            session.setFinishedAt(session.getStartedAt().plusMinutes(session.getDurationMinutes()));
            sessionRepository.save(session);

            int exIdx = 0;
            for (WorkoutExercise we : wes) {
                Exercise ex = we.getExercise();
                int count = sessionCountByExercise.merge(ex.getId(), 1, Integer::sum);
                double weight = baseWeight(ex.getName()) + 0.5 * (count - 1);
                int range = ex.getRepMax() - ex.getRepMin() + 1;
                for (int n = 1; n <= we.getSets(); n++) {
                    int reps = ex.getRepMin()
                            + ((date.getDayOfYear() * 3 + n * 2 + exIdx * 5) % range);
                    SetLog sl = new SetLog();
                    sl.setSession(session);
                    sl.setExercise(ex);
                    sl.setSetNumber(n);
                    sl.setWeight(weight);
                    sl.setReps(reps);
                    sl.setRir(n % 3 == 0 ? 2 : null);
                    sl.setCompleted(true);
                    setLogRepository.save(sl);
                }
                exIdx++;
            }
        }

        for (int i = 0; i < 10; i++) {
            LocalDate date = today.minusDays(1).minusDays((long) (9 - i) * 7);
            double wobble = Math.sin(i * 1.7) * 0.2;
            BodyWeightLog log = new BodyWeightLog();
            log.setDate(date);
            log.setWeightKg(Math.round((73.4 + i * 0.16 + wobble) * 10) / 10.0);
            log.setDemo(true);
            bodyWeightRepository.save(log);
        }
    }

    private double baseWeight(String name) {
        Map<String, Double> base = Map.ofEntries(
                Map.entry("Lat Pulldown", 55.0), Map.entry("Mid-Back Row", 35.0),
                Map.entry("Upper-Back Row", 30.0), Map.entry("Dumbbell Pullover", 20.0),
                Map.entry("Shrugs", 35.0), Map.entry("Rear Delt Fly", 10.0),
                Map.entry("Incline Bench", 45.0), Map.entry("Flat Bench", 55.0),
                Map.entry("Fly", 15.0), Map.entry("Shoulder Press", 27.5),
                Map.entry("Lateral Raise", 7.5), Map.entry("Curl", 15.0),
                Map.entry("Hammer Curl", 12.5), Map.entry("Pushdown", 30.0),
                Map.entry("Overhead Dumbbell Extension", 12.5), Map.entry("Reverse Curl", 10.0),
                Map.entry("Behind-the-Back Wrist Curl", 10.0), Map.entry("Barbell Squat", 70.0),
                Map.entry("Romanian Deadlift", 60.0), Map.entry("Leg Extension", 40.0),
                Map.entry("Hamstring Curl", 35.0), Map.entry("Calf Raise", 55.0),
                Map.entry("Leg Raise Machine", 15.0), Map.entry("One-Arm Dumbbell Row", 22.5),
                Map.entry("Incline Dumbbell Curl", 10.0), Map.entry("Dumbbell Shoulder Press", 17.5));
        return base.getOrDefault(name, 20.0);
    }
}
