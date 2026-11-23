package com.portfolio.insurance.report;

import com.portfolio.insurance.reference.ProductCode;
import com.portfolio.insurance.report.DashboardDto.MonthlyProduction;
import com.portfolio.insurance.report.DashboardDto.ProductMix;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {

    @Test
    void fillsMissingMonthsAndProductsWithZeros() {
        YearMonth first = YearMonth.of(2026, 1);
        List<ProductMix> products = List.of(
                new ProductMix(ProductCode.TRAFFIC, "Trafik", 1, BigDecimal.ONE),
                new ProductMix(ProductCode.HOME, "Konut", 1, BigDecimal.ONE));
        List<MonthlyProduction> rows = List.of(
                new MonthlyProduction("2026-03", ProductCode.HOME, "Konut", 2, new BigDecimal("500")));

        List<MonthlyProduction> filled = DashboardService.fillMonths(rows, first, products);

        assertThat(filled).hasSize(DashboardService.MONTHS * 2);
        assertThat(filled.getFirst()).isEqualTo(
                new MonthlyProduction("2026-01", ProductCode.TRAFFIC, "Trafik", 0, BigDecimal.ZERO));
        assertThat(filled.get(5)).isSameAs(rows.getFirst()); // March, HOME
        assertThat(filled.getLast().month()).isEqualTo("2026-12");
    }
}
