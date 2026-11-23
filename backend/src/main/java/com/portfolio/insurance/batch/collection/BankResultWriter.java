package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.collection.BankRecord.Detail;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;

/**
 * Hands a whole chunk of result lines to PKG_COLLECTION.APPLY_RESULTS in one call, as a JSON array in a CLOB
 * that PL/SQL opens with JSON_TABLE: one round trip per chunk instead of one per line, with plain JDBC types.
 * The call runs on the step's transactional connection, so the payments of a chunk commit together with the
 * reader's position.
 */
class BankResultWriter implements ItemWriter<Detail> {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final long fileId;

    BankResultWriter(JdbcTemplate jdbc, JsonMapper json, long fileId) {
        this.jdbc = jdbc;
        this.json = json;
        this.fileId = fileId;
    }

    @Override
    public void write(Chunk<? extends Detail> chunk) {
        List<ResultLine> lines = chunk.getItems().stream()
                .map(d -> new ResultLine(d.lineNo(), d.installmentId(), d.policyNumber(), d.installmentNo(),
                        d.amount(), d.resultCode()))
                .toList();
        jdbc.update("BEGIN pkg_collection.apply_results(?, ?, ?); END;", fileId,
                new SqlParameterValue(Types.CLOB, json.writeValueAsString(lines)), BatchJobs.BATCH_USER);
    }

    /** JSON shape read by PKG_COLLECTION (a JSON number is parsed independently of the session's NLS settings). */
    record ResultLine(long lineNo, long installmentId, String policyNumber, int installmentNo, BigDecimal amount,
                      String resultCode) {
    }
}
