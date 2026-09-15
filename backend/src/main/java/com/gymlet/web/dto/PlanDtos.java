package com.gymlet.web.dto;

import java.util.List;

public final class PlanDtos {

    private PlanDtos() {
    }

    public record PlanSummaryDto(Long id, String name, String description, String goal,
                                 boolean archived, boolean active, long historicalSessionCount) {
    }

    public record PlanListDto(List<PlanSummaryDto> plans, PlanSummaryDto active) {
    }

    public record ScheduleDayDto(Long id, Integer weekday, Integer dayNumber, String name, boolean restDay) {
    }
}
