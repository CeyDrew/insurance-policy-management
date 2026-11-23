package com.portfolio.insurance.batch.support;

import com.portfolio.insurance.batch.BatchJobs;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Batch-inserts notifications; customers without e-mail or phone are silently skipped. */
@Component
@RequiredArgsConstructor
public class NotificationWriter {

    private final JdbcTemplate jdbc;

    public void write(List<Notification> notifications) {
        List<Object[]> rows = notifications.stream()
                .filter(Notification::deliverable)
                .map(n -> new Object[]{n.customerId(), n.policyId(), n.type(), n.channel(), n.recipient(),
                        n.subject(), n.body(), BatchJobs.BATCH_USER})
                .toList();
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO notification (customer_id, policy_id, notification_type, channel, recipient,
                                              subject, body, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, rows);
        }
    }
}
