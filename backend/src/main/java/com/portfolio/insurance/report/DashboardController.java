package com.portfolio.insurance.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "Dashboard", description = "KPIs and charts fed by the reporting views")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "KPIs, monthly production, product mix, loss ratios, agent performance")
    @GetMapping
    public DashboardDto dashboard() {
        return dashboardService.dashboard();
    }

    @Operation(summary = "Recompute the materialized views behind the dashboard")
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, LocalDateTime> refresh() {
        return Map.of("refreshedAt", dashboardService.refresh());
    }
}
