package com.gymlet.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutExercise;
import com.gymlet.domain.WorkoutPlan;
import com.gymlet.repository.AppUserRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.repository.WorkoutPlanRepository;
import com.gymlet.repository.WorkoutSessionRepository;
import com.gymlet.web.dto.PlanDtos;
import com.gymlet.web.dto.Requests;

@Service
public class PlanService {
    private final WorkoutExerciseRepository workoutExerciseRepository;

    private final UserContext userContext;
    private final AppUserRepository userRepository;
    private final WorkoutPlanRepository planRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final PlanScheduleSupport scheduleSupport;

    public PlanService(UserContext userContext,
                       AppUserRepository userRepository,
                       WorkoutPlanRepository planRepository,
                       WorkoutDayRepository workoutDayRepository,
                       WorkoutExerciseRepository workoutExerciseRepository,
                       WorkoutSessionRepository sessionRepository,
                       PlanScheduleSupport scheduleSupport) {
        this.userContext = userContext;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.workoutDayRepository = workoutDayRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.sessionRepository = sessionRepository;
        this.scheduleSupport = scheduleSupport;
    }

    @Transactional(readOnly = true)
    public List<PlanDtos.PlanSummaryDto> listPlans() {
        Long userId = userContext.getUserId();
        AppUser user = userContext.getUser();
        return planRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(p -> toSummary(p, user.getActivePlanId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanDtos.PlanSummaryDto activePlan() {
        AppUser user = userContext.getUser();
        Long planId = requireActivePlanId(user);
        WorkoutPlan plan = planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Active plan not found"));
        return toSummary(plan, planId);
    }

    @Transactional
    public PlanDtos.PlanSummaryDto createPlan(Requests.CreatePlanRequest req) {
        AppUser user = userContext.getUser();
        String name = req.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Plan name is required");
        }
        if (planRepository.findByUserIdAndNameIgnoreCase(user.getId(), name).isPresent()) {
            throw new IllegalArgumentException("You already have a plan with that name");
        }
        WorkoutPlan plan = new WorkoutPlan();
        plan.setUserId(user.getId());
        plan.setName(name);
        if (req.description() != null && !req.description().isBlank()) {
            plan.setDescription(req.description().trim());
        }
        if (req.goal() != null && !req.goal().isBlank()) {
            plan.setGoal(req.goal().trim());
        }
        plan = planRepository.save(plan);
        scheduleSupport.ensureSevenDaySchedule(user, plan.getId());
        return toSummary(plan, user.getActivePlanId());
    }

    @Transactional
    public PlanDtos.PlanSummaryDto updatePlan(Long planId, Requests.UpdatePlanRequest req) {
        AppUser user = userContext.getUser();
        WorkoutPlan plan = planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Plan not found"));
        if (req.name() != null) {
            String name = req.name().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Plan name cannot be empty");
            }
            planRepository.findByUserIdAndNameIgnoreCase(user.getId(), name)
                    .filter(other -> !other.getId().equals(planId))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("You already have a plan with that name");
                    });
            plan.setName(name);
        }
        if (req.description() != null) {
            plan.setDescription(req.description().isBlank() ? null : req.description().trim());
        }
        if (req.goal() != null) {
            plan.setGoal(req.goal().isBlank() ? null : req.goal().trim());
        }
        planRepository.save(plan);
        return toSummary(plan, user.getActivePlanId());
    }

    @Transactional
    public PlanDtos.PlanSummaryDto copyPlan(Long sourcePlanId) {
        AppUser user = userContext.getUser();
        WorkoutPlan source = planRepository.findByIdAndUserId(sourcePlanId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Plan not found"));
        if (source.isArchived()) {
            throw new IllegalArgumentException("Archived plans cannot be copied");
        }

        WorkoutPlan copy = new WorkoutPlan();
        copy.setUserId(user.getId());
        copy.setName(uniqueCopyName(user.getId(), source.getName()));
        copy.setDescription(source.getDescription());
        copy.setGoal(source.getGoal());
        copy = planRepository.save(copy);

        List<WorkoutDay> sourceDays = workoutDayRepository
                .findAllByUserIdAndPlanIdOrderByDayNumberAsc(user.getId(), sourcePlanId);
        for (WorkoutDay sourceDay : sourceDays) {
            WorkoutDay copiedDay = new WorkoutDay();
            copiedDay.setUserId(user.getId());
            copiedDay.setPlanId(copy.getId());
            copiedDay.setName(sourceDay.getName());
            copiedDay.setDayNumber(sourceDay.getDayNumber());
            copiedDay.setWeekday(sourceDay.getWeekday());
            copiedDay.setRestDay(sourceDay.isRestDay());
            copiedDay = workoutDayRepository.save(copiedDay);

            for (WorkoutExercise sourceExercise : workoutExerciseRepository
                    .findByWorkoutDayIdOrderBySetOrderAsc(sourceDay.getId())) {
                WorkoutExercise copiedExercise = new WorkoutExercise();
                copiedExercise.setWorkoutDay(copiedDay);
                copiedExercise.setExercise(sourceExercise.getExercise());
                copiedExercise.setSetOrder(sourceExercise.getSetOrder());
                copiedExercise.setSets(sourceExercise.getSets());
                copiedExercise.setTargetRir(sourceExercise.getTargetRir());
                copiedExercise.setRestSeconds(sourceExercise.getRestSeconds());
                workoutExerciseRepository.save(copiedExercise);
            }
        }
        return toSummary(copy, user.getActivePlanId());
    }

    private String uniqueCopyName(Long userId, String sourceName) {
        String base = sourceName + " copy";
        String candidate = base;
        int suffix = 2;
        while (planRepository.findByUserIdAndNameIgnoreCase(userId, candidate).isPresent()) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    @Transactional
    public PlanDtos.ScheduleDayDto assignWorkoutDay(Long planId, int weekday, Long workoutDayId) {
        WorkoutPlan plan = requireEditablePlan(planId);
        WorkoutDay day = scheduleSupport.assignWorkoutDay(userContext.getUserId(), plan.getId(), weekday, workoutDayId);
        return toScheduleDayDto(day);
    }

    @Transactional
    public PlanDtos.ScheduleDayDto setRestDay(Long planId, int weekday) {
        WorkoutPlan plan = requireEditablePlan(planId);
        WorkoutDay day = scheduleSupport.setRestDay(userContext.getUserId(), plan.getId(), weekday);
        return toScheduleDayDto(day);
    }

    @Transactional
    public PlanDtos.PlanSummaryDto activatePlan(Long planId) {
        AppUser user = userContext.getUser();
        WorkoutPlan plan = planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Plan not found"));
        if (plan.isArchived()) {
            throw new IllegalArgumentException("Archived plans cannot be activated");
        }
        user.setActivePlanId(plan.getId());
        userRepository.save(user);
        scheduleSupport.ensureSevenDaySchedule(user, plan.getId());
        return toSummary(plan, plan.getId());
    }

    /**
     * Archives a plan. Historical sessions are untouched (they reference workout_day rows directly).
     * Never deletes workout days that belong to the plan.
     */
    @Transactional
    public PlanDtos.PlanSummaryDto archivePlan(Long planId) {
        AppUser user = userContext.getUser();
        WorkoutPlan plan = planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Plan not found"));
        if (plan.isArchived()) {
            return toSummary(plan, user.getActivePlanId());
        }
        plan.setArchived(true);
        planRepository.save(plan);

        if (planId.equals(user.getActivePlanId())) {
            WorkoutPlan replacement = planRepository.findAllByUserIdAndArchivedFalseOrderByCreatedAtDesc(user.getId())
                    .stream()
                    .filter(p -> !p.getId().equals(planId))
                    .findFirst()
                    .orElse(null);
            if (replacement == null) {
                plan.setArchived(false);
                planRepository.save(plan);
                throw new IllegalArgumentException("Cannot archive your only active plan");
            }
            user.setActivePlanId(replacement.getId());
            userRepository.save(user);
        }
        return toSummary(plan, user.getActivePlanId());
    }

    public Long requireActivePlanId(AppUser user) {
        if (user.getActivePlanId() == null) {
            throw new IllegalStateException("No active workout plan — try logging out and back in");
        }
        return user.getActivePlanId();
    }

    private WorkoutPlan requireEditablePlan(Long planId) {
        WorkoutPlan plan = planRepository.findByIdAndUserId(planId, userContext.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Plan not found"));
        if (plan.isArchived()) {
            throw new IllegalArgumentException("Archived plans cannot be modified");
        }
        return plan;
    }

    private PlanDtos.ScheduleDayDto toScheduleDayDto(WorkoutDay day) {
        return new PlanDtos.ScheduleDayDto(day.getId(), day.getWeekday(), day.getDayNumber(), day.getName(), day.isRestDay());
    }

    /** Ensures every user with workout days has an active plan (called from migration). */
    @Transactional
    public WorkoutPlan ensureDefaultPlan(AppUser user) {
        if (user.getActivePlanId() != null) {
            return planRepository.findByIdAndUserId(user.getActivePlanId(), user.getId()).orElseGet(() -> {
                user.setActivePlanId(null);
                userRepository.save(user);
                return createMySplitPlan(user);
            });
        }
        return createMySplitPlan(user);
    }

    private WorkoutPlan createMySplitPlan(AppUser user) {
        WorkoutPlan existing = planRepository.findByUserIdAndNameIgnoreCase(user.getId(), "My Split").orElse(null);
        if (existing != null) {
            user.setActivePlanId(existing.getId());
            userRepository.save(user);
            return existing;
        }
        WorkoutPlan plan = new WorkoutPlan();
        plan.setUserId(user.getId());
        plan.setName("My Split");
        plan = planRepository.save(plan);
        user.setActivePlanId(plan.getId());
        userRepository.save(user);
        return plan;
    }

    private PlanDtos.PlanSummaryDto toSummary(WorkoutPlan plan, Long activePlanId) {
        boolean active = plan.getId().equals(activePlanId);
        long sessionCount = sessionRepository.findAllByUserId(userContext.getUserId()).stream()
                .filter(s -> s.getWorkoutDay().getPlanId() != null
                        && s.getWorkoutDay().getPlanId().equals(plan.getId()))
                .count();
        return new PlanDtos.PlanSummaryDto(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getGoal(),
                plan.isArchived(),
                active,
                sessionCount);
    }
}
