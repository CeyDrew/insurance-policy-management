package com.portfolio.insurance.batch.claimimport;

import com.portfolio.insurance.batch.BatchJobs;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Inserts imported claims with status REPORTED; the AUDIT trigger records each insert. */
@RequiredArgsConstructor
class ClaimImportWriter implements ItemWriter<ClaimImportItem> {

    static final String IMPORT_USER = BatchJobs.BATCH_USER + "-import";

    private final JdbcTemplate jdbc;

    @Override
    public void write(Chunk<? extends ClaimImportItem> chunk) {
        int year = LocalDate.now().getYear();
        List<Object[]> rows = new ArrayList<>();
        for (ClaimImportItem item : chunk) {
            long id = jdbc.queryForObject("SELECT claim_seq.NEXTVAL FROM dual", Long.class);
            rows.add(new Object[]{id, "HSR-%d-%06d".formatted(year, id), item.policyId(),
                    Date.valueOf(item.incidentDate()), item.incidentCityId(), item.description(),
                    item.estimatedAmount(), IMPORT_USER});
        }
        jdbc.batchUpdate("""
                INSERT INTO claim (id, claim_number, policy_id, incident_date, incident_city_id, description,
                                   status, estimated_amount, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'REPORTED', ?, ?)
                """, rows);
    }
}
