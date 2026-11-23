package com.portfolio.insurance.batch.renewal;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.support.Notification;
import com.portfolio.insurance.batch.support.NotificationWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inserts renewal quotes (status QUOTE, renewed_from_policy_id = previous term) and a RENEWAL_OFFER notification
 * for each. The quote stays valid until the current term ends.
 */
@RequiredArgsConstructor
class RenewalQuoteWriter implements ItemWriter<RenewalQuote> {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final JdbcTemplate jdbc;
    private final NotificationWriter notificationWriter;

    @Override
    public void write(Chunk<? extends RenewalQuote> chunk) {
        List<Object[]> policies = new ArrayList<>();
        List<Notification> notifications = new ArrayList<>();
        for (RenewalQuote quote : chunk) {
            RenewalCandidate previous = quote.previous();
            long id = jdbc.queryForObject("SELECT policy_seq.NEXTVAL FROM dual", Long.class);
            String number = "%s-%d-%06d".formatted(previous.productCode().policyPrefix(), quote.startDate().getYear(), id);
            policies.add(new Object[]{id, number, previous.productId(), previous.customerId(), previous.vehicleId(),
                    previous.propertyId(), previous.agentId(), Date.valueOf(quote.startDate()),
                    Date.valueOf(quote.endDate()), previous.nextNcdStep(), quote.premium().netPremium(),
                    quote.premium().taxAmount(), quote.premium().grossPremium(), previous.installmentCount(),
                    Date.valueOf(previous.endDate()), previous.policyId(), BatchJobs.BATCH_USER});
            notifications.add(new Notification(previous.customerId(), id, "RENEWAL_OFFER", previous.email(),
                    previous.phone(), "Poliçe yenileme teklifiniz hazır",
                    "Sayın %s, %s numaralı poliçeniz %s tarihinde sona eriyor. Yenileme teklifiniz: %s numaralı teklif, brüt prim %s TL."
                            .formatted(previous.customerName(), previous.policyNumber(),
                                    previous.endDate().format(DATE), number,
                                    String.format(Locale.forLanguageTag("tr-TR"), "%,.2f", quote.premium().grossPremium()))));
        }
        jdbc.batchUpdate("""
                INSERT INTO policy (id, policy_number, product_id, customer_id, vehicle_id, property_id, agent_id,
                                    status, start_date, end_date, ncd_step, net_premium, tax_amount, gross_premium,
                                    installment_count, quote_valid_until, renewed_from_policy_id, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'QUOTE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, policies);
        notificationWriter.write(notifications);
    }
}
