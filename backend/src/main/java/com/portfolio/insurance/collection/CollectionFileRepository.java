package com.portfolio.insurance.collection;

import com.portfolio.insurance.collection.CollectionDtos.CollectionFileSummary;
import com.portfolio.insurance.collection.CollectionDtos.CollectionResultRow;
import com.portfolio.insurance.collection.CollectionDtos.Direction;
import com.portfolio.insurance.collection.CollectionDtos.FileStatus;
import com.portfolio.insurance.collection.CollectionDtos.Outcome;
import com.portfolio.insurance.collection.CollectionDtos.RejectReason;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Reads COLLECTION_FILE / COLLECTION_RESULT; both are written by the batch jobs and PKG_COLLECTION. */
@Repository
class CollectionFileRepository {

    private static final String SUMMARY_SELECT = """
            SELECT f.id, f.direction, f.request_file_id, f.file_name, f.file_date, f.status, f.record_count,
                   f.total_amount, f.collected_count, f.collected_amount, f.rejected_count, f.error_count,
                   f.error_message, f.job_execution_id, f.created_at, f.created_by, f.completed_at,
                   CASE WHEN EXISTS (SELECT 1 FROM collection_file r
                                      WHERE r.request_file_id = f.id AND r.status <> 'FAILED') THEN 1 ELSE 0 END
                       AS answered
              FROM collection_file f
            """;

    private static final RowMapper<CollectionFileSummary> SUMMARY = (rs, rowNum) -> {
        FileStatus status = FileStatus.valueOf(rs.getString("status"));
        String error = rs.getString("error_message");
        return new CollectionFileSummary(rs.getLong("id"), Direction.valueOf(rs.getString("direction")),
                rs.getObject("request_file_id", Long.class), rs.getString("file_name"),
                rs.getObject("file_date", LocalDate.class), status, rs.getObject("record_count", Long.class),
                rs.getBigDecimal("total_amount"), rs.getLong("collected_count"), rs.getBigDecimal("collected_amount"),
                rs.getLong("rejected_count"), rs.getLong("error_count"), error,
                rs.getObject("job_execution_id", Long.class), rs.getObject("created_at", LocalDateTime.class),
                rs.getString("created_by"), rs.getObject("completed_at", LocalDateTime.class),
                rs.getInt("answered") == 1, status == FileStatus.PROCESSING && error != null);
    };

    private final NamedParameterJdbcTemplate jdbc;

    CollectionFileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<CollectionFileSummary> page(Direction direction, int offset, int size) {
        return jdbc.query(SUMMARY_SELECT + """
                 WHERE (:direction IS NULL OR f.direction = :direction)
                 ORDER BY f.id DESC
                 OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """, new MapSqlParameterSource().addValue("direction", direction == null ? null : direction.name(), Types.VARCHAR)
                .addValue("offset", offset).addValue("size", size), SUMMARY);
    }

    long count(Direction direction) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM collection_file WHERE (:direction IS NULL OR direction = :direction)",
                new MapSqlParameterSource().addValue("direction", direction == null ? null : direction.name(), Types.VARCHAR), Long.class);
        return count == null ? 0 : count;
    }

    Optional<CollectionFileSummary> find(long id) {
        return jdbc.query(SUMMARY_SELECT + " WHERE f.id = :id", new MapSqlParameterSource("id", id), SUMMARY)
                .stream().findFirst();
    }

    List<CollectionFileSummary> resultFilesOf(long requestFileId) {
        return jdbc.query(SUMMARY_SELECT + " WHERE f.request_file_id = :id ORDER BY f.id DESC",
                new MapSqlParameterSource("id", requestFileId), SUMMARY);
    }

    Optional<String> storagePath(long id) {
        return jdbc.queryForList("SELECT storage_path FROM collection_file WHERE id = :id",
                new MapSqlParameterSource("id", id), String.class).stream().findFirst();
    }

    /** An applied or still running copy of the same content. */
    boolean existsActiveInbound(String fileHash) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM collection_file
                 WHERE direction = 'INBOUND' AND status <> 'FAILED' AND file_hash = :hash
                """, new MapSqlParameterSource("hash", fileHash), Long.class);
        return count != null && count > 0;
    }

    List<RejectReason> rejectReasons(long fileId) {
        return jdbc.query("""
                SELECT result_code, pkg_collection.result_description(result_code) AS description,
                       COUNT(*) AS cnt, NVL(SUM(amount), 0) AS amount
                  FROM collection_result
                 WHERE collection_file_id = :id AND outcome = 'REJECTED'
                 GROUP BY result_code
                 ORDER BY cnt DESC, result_code
                """, new MapSqlParameterSource("id", fileId), (rs, rowNum) -> new RejectReason(
                rs.getString("result_code"), rs.getString("description"), rs.getLong("cnt"),
                rs.getBigDecimal("amount")));
    }

    List<CollectionResultRow> results(long fileId, Outcome outcome, int offset, int size) {
        return jdbc.query("""
                SELECT r.line_no, r.installment_id, i.policy_id, r.policy_number, r.installment_no, r.amount,
                       r.result_code, r.outcome, r.payment_id, r.message
                  FROM collection_result r
                  LEFT JOIN installment i ON i.id = r.installment_id
                 WHERE r.collection_file_id = :id AND (:outcome IS NULL OR r.outcome = :outcome)
                 ORDER BY r.line_no
                 OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """, new MapSqlParameterSource("id", fileId)
                .addValue("outcome", outcome == null ? null : outcome.name(), Types.VARCHAR)
                .addValue("offset", offset).addValue("size", size), (rs, rowNum) -> new CollectionResultRow(
                rs.getLong("line_no"), rs.getObject("installment_id", Long.class),
                rs.getObject("policy_id", Long.class), rs.getString("policy_number"),
                rs.getObject("installment_no", Integer.class), rs.getBigDecimal("amount"),
                rs.getString("result_code"), Outcome.valueOf(rs.getString("outcome")),
                rs.getObject("payment_id", Long.class), rs.getString("message")));
    }

    long countResults(long fileId, Outcome outcome) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM collection_result
                 WHERE collection_file_id = :id AND (:outcome IS NULL OR outcome = :outcome)
                """, new MapSqlParameterSource("id", fileId)
                .addValue("outcome", outcome == null ? null : outcome.name(), Types.VARCHAR), Long.class);
        return count == null ? 0 : count;
    }
}
