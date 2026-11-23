package com.portfolio.insurance.batch.claimimport;

import com.portfolio.insurance.claim.ClaimCoverage;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses and validates one CSV line:
 * <pre>policy_number;incident_date;description;estimated_amount[;incident_city_plate]</pre>
 * Dates are dd.MM.yyyy or yyyy-MM-dd, amounts accept a decimal comma ("12500,50") or point.
 * Any problem raises {@link ClaimImportException}, which the step skips and records.
 * <p>
 * Step scoped: remembers the claims seen in this file to reject duplicates within the same file.
 */
class ClaimCsvProcessor implements ItemProcessor<ClaimCsvLine, ClaimImportItem> {

    static final String DELIMITER = ";";
    /** STRICT: 31.11.2026 is rejected instead of being silently turned into 30.11.2026. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    private static final int MAX_DESCRIPTION = 2000;

    private final JdbcTemplate jdbc;
    private final LocalDate today;
    /** claim key -> first line it appeared on; keyed by line so re-processing the same line stays idempotent. */
    private final Map<String, Integer> seenInFile = new HashMap<>();

    ClaimCsvProcessor(JdbcTemplate jdbc, LocalDate today) {
        this.jdbc = jdbc;
        this.today = today;
    }

    private record PolicyTerm(long id, String status, LocalDate startDate, LocalDate endDate, LocalDateTime cancelledAt) {
    }

    @Override
    public ClaimImportItem process(ClaimCsvLine line) {
        String[] fields = line.content().split(DELIMITER, -1);
        if (fields.length < 4 || fields.length > 5) {
            throw new ClaimImportException("Beklenen 4 veya 5 alan, bulunan " + fields.length
                    + ". Biçim: poliçe_no;hasar_tarihi;açıklama;tahmini_tutar[;il_plaka_kodu]");
        }
        String policyNumber = clean(fields[0]).toUpperCase(Locale.ROOT);
        LocalDate incidentDate = parseDate(clean(fields[1]));
        String description = clean(fields[2]);
        BigDecimal amount = parseAmount(clean(fields[3]));
        Long cityId = fields.length == 5 && !clean(fields[4]).isEmpty() ? cityId(clean(fields[4])) : null;

        if (policyNumber.isEmpty()) {
            throw new ClaimImportException("Poliçe numarası boş.");
        }
        if (description.isEmpty()) {
            throw new ClaimImportException("Hasar açıklaması boş.");
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw new ClaimImportException("Hasar açıklaması en fazla " + MAX_DESCRIPTION + " karakter olabilir.");
        }
        if (incidentDate.isAfter(today)) {
            throw new ClaimImportException("Hasar tarihi ileri bir tarih olamaz: " + fields[1].trim());
        }

        PolicyTerm policy = findPolicy(policyNumber);
        ClaimCoverage.check(policy.status(), policy.startDate(), policy.endDate(), policy.cancelledAt(), incidentDate,
                today).ifPresent(reason -> {
            throw new ClaimImportException(reason);
        });

        String key = policy.id() + "|" + incidentDate + "|" + description.toLowerCase(Locale.ROOT);
        int firstLine = seenInFile.computeIfAbsent(key, k -> line.lineNumber());
        if (firstLine != line.lineNumber() || alreadyReported(policy.id(), incidentDate, description)) {
            throw new ClaimImportException("Bu hasar ihbarı daha önce kaydedilmiş.");
        }
        return new ClaimImportItem(line, policy.id(), incidentDate, description, amount, cityId);
    }

    private PolicyTerm findPolicy(String policyNumber) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, status, start_date, end_date, cancelled_at FROM policy WHERE policy_number = ?",
                    (rs, rowNum) -> new PolicyTerm(rs.getLong("id"), rs.getString("status"),
                            rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                            rs.getObject("cancelled_at", LocalDateTime.class)),
                    policyNumber);
        } catch (EmptyResultDataAccessException e) {
            throw new ClaimImportException("Poliçe bulunamadı: " + policyNumber);
        }
    }

    private boolean alreadyReported(long policyId, LocalDate incidentDate, String description) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM claim
                 WHERE policy_id = ? AND incident_date = ? AND LOWER(description) = LOWER(?)
                """, Integer.class, policyId, incidentDate, description);
        return count != null && count > 0;
    }

    private Long cityId(String plate) {
        try {
            return jdbc.queryForObject("SELECT id FROM city WHERE plate_code = ?", Long.class, Integer.parseInt(plate));
        } catch (NumberFormatException | EmptyResultDataAccessException e) {
            throw new ClaimImportException("Geçersiz il plaka kodu: " + plate);
        }
    }

    private static LocalDate parseDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new ClaimImportException("Geçersiz hasar tarihi: '" + value + "' (gg.aa.yyyy bekleniyor).");
    }

    private static BigDecimal parseAmount(String value) {
        String normalized = value.replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", ""); // 12.500,50 -> 12500,50
        }
        normalized = normalized.replace(',', '.');
        BigDecimal amount;
        try {
            amount = new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new ClaimImportException("Geçersiz tutar: '" + value + "'.");
        }
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ClaimImportException("Tahmini tutar 0'dan büyük ve en fazla 100.000.000 olmalıdır.");
        }
        return amount;
    }

    /** Trims and removes surrounding double quotes added by spreadsheet exports. */
    private static String clean(String field) {
        String value = field.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"").trim();
        }
        return value;
    }
}
