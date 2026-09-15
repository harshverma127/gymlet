package com.gymlet.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gymlet.service.PlanService;
import com.gymlet.web.dto.PlanDtos;
import com.gymlet.web.dto.Requests;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public PlanDtos.PlanListDto list() {
        List<PlanDtos.PlanSummaryDto> plans = planService.listPlans();
        PlanDtos.PlanSummaryDto active = planService.activePlan();
        return new PlanDtos.PlanListDto(plans, active);
    }

    @PostMapping("/{id}/copy")
    public PlanDtos.PlanSummaryDto copy(@PathVariable Long id) {
        return planService.copyPlan(id);
    }

    @GetMapping("/active")
    public PlanDtos.PlanSummaryDto active() {
        return planService.activePlan();
    }

    @PostMapping
    public PlanDtos.PlanSummaryDto create(@Valid @RequestBody Requests.CreatePlanRequest req) {
        return planService.createPlan(req);
    }

    @PutMapping("/{id}")
    public PlanDtos.PlanSummaryDto update(@PathVariable Long id,
                                          @Valid @RequestBody Requests.UpdatePlanRequest req) {
        return planService.updatePlan(id, req);
    }

    @PostMapping("/{id}/activate")
    public PlanDtos.PlanSummaryDto activate(@PathVariable Long id) {
        return planService.activatePlan(id);
    }

    @PostMapping("/{id}/archive")
    public PlanDtos.PlanSummaryDto archive(@PathVariable Long id) {
        return planService.archivePlan(id);
    }

    @PutMapping("/{id}/schedule/{weekday}")
    public PlanDtos.ScheduleDayDto assignWorkoutDay(@PathVariable Long id,
                                                    @PathVariable int weekday,
                                                    @Valid @RequestBody Requests.ScheduleWorkoutRequest req) {
        return planService.assignWorkoutDay(id, weekday, req.workoutDayId());
    }

    @PutMapping("/{id}/schedule/{weekday}/rest")
    public PlanDtos.ScheduleDayDto setRestDay(@PathVariable Long id,
                                              @PathVariable int weekday) {
        return planService.setRestDay(id, weekday);
    }
}
