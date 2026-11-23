package com.portfolio.insurance.payment;

import com.portfolio.insurance.payment.PaymentDtos.InstallmentRow;
import com.portfolio.insurance.payment.PaymentDtos.PaymentResult;
import com.portfolio.insurance.policy.InstallmentStatus;
import com.portfolio.insurance.policy.PolicyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read model of the collection screen: installments joined with policy and customer, filtered and paged in SQL.
 */
@Repository
@RequiredArgsConstructor
class InstallmentQueryRepository {

    private static final String FROM = """
             FROM installment i
             JOIN policy p ON p.id = i.policy_id
             JOIN customer c ON c.id = p.customer_id
            """;

    private final JdbcTemplate jdbc;

    record Filter(InstallmentStatus status, boolean unpaidOnly, String query, Long policyId, LocalDate dueTo) {
    }

    List<InstallmentRow> find(Filter filter, LocalDate today, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", page * size)
                .addValue("size", size);
        String where = where(filter, params);
        return named().query("""
                SELECT i.id, i.installment_no, i.due_date, i.amount, i.status, i.paid_at,
                       p.id AS policy_id, p.policy_number, p.status AS policy_status, c.id AS customer_id,
                       NVL(c.company_name, c.first_name || ' ' || c.last_name) AS customer_name
                """ + FROM + where + """
                 ORDER BY i.due_date, i.id
                 OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """, params, (rs, rowNum) -> {
            LocalDate dueDate = rs.getObject("due_date", LocalDate.class);
            InstallmentStatus status = InstallmentStatus.valueOf(rs.getString("status"));
            boolean unpaid = status == InstallmentStatus.PENDING || status == InstallmentStatus.OVERDUE;
            long daysOverdue = unpaid && dueDate.isBefore(today) ? ChronoUnit.DAYS.between(dueDate, today) : 0;
            return new InstallmentRow(rs.getLong("id"), rs.getLong("policy_id"), rs.getString("policy_number"),
                    PolicyStatus.valueOf(rs.getString("policy_status")), rs.getLong("customer_id"),
                    rs.getString("customer_name"), rs.getInt("installment_no"), dueDate, rs.getBigDecimal("amount"),
                    status, rs.getObject("paid_at", LocalDateTime.class), daysOverdue);
        });
    }

    long count(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(filter, params);
        Long count = named().queryForObject("SELECT COUNT(*) " + FROM + where, params, Long.class);
        return count == null ? 0 : count;
    }

    PaymentResult paymentResult(long paymentId, boolean reactivated) {
        return jdbc.queryForObject("""
                SELECT pay.id, pay.reference_no, pay.amount, pay.payment_method, i.id AS installment_id,
                       i.installment_no, p.id AS policy_id, p.policy_number
                  FROM payment pay
                  JOIN installment i ON i.id = pay.installment_id
                  JOIN policy p ON p.id = i.policy_id
                 WHERE pay.id = ?
                """, (rs, rowNum) -> new PaymentResult(rs.getLong("id"), rs.getString("reference_no"),
                rs.getBigDecimal("amount"), PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getLong("installment_id"), rs.getInt("installment_no"), rs.getLong("policy_id"),
                rs.getString("policy_number"), reactivated), paymentId);
    }

    private static String where(Filter filter, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (filter.status() != null) {
            conditions.add("i.status = :status");
            params.addValue("status", filter.status().name());
        } else if (filter.unpaidOnly()) {
            conditions.add("i.status IN ('PENDING', 'OVERDUE')");
        }
        if (StringUtils.hasText(filter.query())) {
            conditions.add("(LOWER(p.policy_number) LIKE :query"
                    + " OR LOWER(NVL(c.company_name, c.first_name || ' ' || c.last_name)) LIKE :query)");
            params.addValue("query", "%" + filter.query().trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (filter.policyId() != null) {
            conditions.add("p.id = :policyId");
            params.addValue("policyId", filter.policyId());
        }
        if (filter.dueTo() != null) {
            conditions.add("i.due_date <= :dueTo");
            params.addValue("dueTo", filter.dueTo());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions) + "\n";
    }

    private NamedParameterJdbcTemplate named() {
        return new NamedParameterJdbcTemplate(jdbc);
    }
}
