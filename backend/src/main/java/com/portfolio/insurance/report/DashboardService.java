package com.portfolio.insurance.report;

import com.portfolio.insurance.claim.ClaimStatus;
import com.portfolio.insurance.report.DashboardDto.ClaimStatusCount;
import com.portfolio.insurance.report.DashboardDto.MonthlyProduction;
import com.portfolio.insurance.report.DashboardDto.ProductMix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int MONTHS = 12;

    private final ReportRepository reports;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        LocalDate today = LocalDate.now(clock);
        YearMonth firstMonth = YearMonth.from(today).minusMonths(MONTHS - 1L);
        List<ProductMix> productMix = reports.productMix();
        return new DashboardDto(LocalDateTime.now(clock), reports.productionRefreshedAt(), reports.kpis(today),
                fillMonths(reports.monthlyProduction(firstMonth), firstMonth, productMix), productMix,
                reports.lossRatios(), reports.agents(), allStatuses(reports.claimStatuses()));
    }

    /** Recomputes the materialized views behind the dashboard. */
    @Transactional
    public LocalDateTime refresh() {
        reports.refresh();
        return reports.productionRefreshedAt();
    }

    /** Every month x product of the window, with zeros where nothing was issued, so charts get a regular grid. */
    static List<MonthlyProduction> fillMonths(List<MonthlyProduction> rows, YearMonth firstMonth,
                                              List<ProductMix> products) {
        Map<String, MonthlyProduction> byKey = rows.stream()
                .collect(Collectors.toMap(r -> r.month() + "|" + r.productCode(), Function.identity()));
        List<MonthlyProduction> result = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            String month = firstMonth.plusMonths(i).toString();
            for (ProductMix product : products) {
                MonthlyProduction row = byKey.get(month + "|" + product.productCode());
                result.add(row != null ? row
                        : new MonthlyProduction(month, product.productCode(), product.productName(), 0, BigDecimal.ZERO));
            }
        }
        return result;
    }

    private static List<ClaimStatusCount> allStatuses(List<ClaimStatusCount> counts) {
        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        counts.forEach(c -> byStatus.put(c.status(), c.count()));
        List<ClaimStatusCount> result = new ArrayList<>();
        for (ClaimStatus status : ClaimStatus.values()) {
            result.add(new ClaimStatusCount(status, byStatus.getOrDefault(status, 0L)));
        }
        return result;
    }
}
