package com.portfolio.insurance.batch;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every job end to end against Oracle with Spring Batch Test's {@link JobOperatorTestUtils}.
 * Jobs commit their own transactions, so tests are not transactional; each test creates its own policies and
 * only asserts on them (jobs also process the seed data, which is fine).
 */
@IntegrationTest
class BatchJobsIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BatchProperties batchProperties;

    @Autowired
    @Qualifier(BatchJobs.POLICY_RENEWAL)
    private Job policyRenewalJob;

    @Autowired
    @Qualifier(BatchJobs.OVERDUE_INSTALLMENT)
    private Job overdueInstallmentJob;

    @Autowired
    @Qualifier(BatchJobs.OVERDUE_INSTALLMENT_PLSQL)
    private Job overdueInstallmentPlsqlJob;

    @Autowired
    @Qualifier(BatchJobs.POLICY_EXPIRATION)
    private Job policyExpirationJob;

    @Autowired
    @Qualifier(BatchJobs.CLAIM_IMPORT)
    private Job claimImportJob;

    @Autowired
    @Qualifier(BatchJobs.MONTHLY_PRODUCTION_REPORT)
    private Job monthlyProductionReportJob;

    private PlsqlFixtures fixtures;
    private long customer;
    private long vehicle;

    @BeforeEach
    void setUp() {
        fixtures = new PlsqlFixtures(jdbc);
        customer = fixtures.individual(LocalDate.of(1980, 2, 2), 6);
        fixtures.contact(customer, "batch.test@example.com", null);
        vehicle = fixtures.vehicle(customer, 2022, new BigDecimal("800000"), "PRIVATE");
    }

    @Test
    void renewalJobCreatesOneQuotePerExpiringPolicy() throws Exception {
        LocalDate end = TODAY.plusDays(10);
        long policy = active(end.minusYears(1), end, "5000.00");
        jdbc.update("UPDATE policy SET ncd_step = 5 WHERE id = ?", policy);

        JobExecution execution = run(policyRenewalJob, runDate(TODAY));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        Map<String, Object> renewal = jdbc.queryForMap("""
                SELECT status, start_date, end_date, ncd_step, net_premium, quote_valid_until, created_by
                  FROM policy WHERE renewed_from_policy_id = ?
                """, policy);
        assertThat(renewal.get("STATUS")).isEqualTo("QUOTE");
        assertThat(date(renewal.get("START_DATE"))).isEqualTo(end);
        assertThat(date(renewal.get("END_DATE"))).isEqualTo(end.plusYears(1));
        assertThat(date(renewal.get("QUOTE_VALID_UNTIL"))).isEqualTo(end);
        assertThat(((Number) renewal.get("NCD_STEP")).intValue()).isEqualTo(6); // claim-free term: one step up
        assertThat(renewal.get("CREATED_BY")).isEqualTo("batch");

        BigDecimal expectedNet = jdbc.queryForObject(
                "SELECT pkg_premium.calculate_premium('TRAFFIC', ?, ?, NULL, 6, ?) FROM dual",
                BigDecimal.class, customer, vehicle, end);
        assertThat((BigDecimal) renewal.get("NET_PREMIUM")).isEqualByComparingTo(expectedNet);
        assertThat(notifications(policy, "RENEWAL_OFFER")).isZero(); // notification belongs to the new quote
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification n JOIN policy p ON p.id = n.policy_id
                 WHERE p.renewed_from_policy_id = ? AND n.notification_type = 'RENEWAL_OFFER'
                   AND n.recipient = 'batch.test@example.com'
                """, Integer.class, policy)).isEqualTo(1);

        // Re-running does not create a second renewal.
        run(policyRenewalJob, runDate(TODAY));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy WHERE renewed_from_policy_id = ?",
                Integer.class, policy)).isEqualTo(1);
    }

    @Test
    void overdueJobMarksInstallmentsAndSuspendsPolicy() throws Exception {
        long policy = policyWithPastInstallments();

        JobExecution execution = run(overdueInstallmentJob, runDate(TODAY));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).extracting(s -> s.getStepName())
                .containsExactly("markOverdueStep", "suspendPoliciesStep");
        assertOverdueOutcome(policy);
    }

    @Test
    void plsqlBulkVariantProducesTheSameResult() throws Exception {
        long policy = policyWithPastInstallments();

        JobExecution execution = run(overdueInstallmentPlsqlJob, runDate(TODAY));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions().iterator().next().getExecutionContext().getLong("markedInstallments"))
                .isGreaterThanOrEqualTo(3);
        assertOverdueOutcome(policy);
    }

    @Test
    void expirationJobExpiresOrRenews() throws Exception {
        LocalDate ended = TODAY.minusDays(1);
        long expiring = active(ended.minusYears(1), ended, "1000");
        long renewed = active(ended.minusYears(1), ended, "1000");
        long successor = active(ended, ended.plusYears(1), "1000");
        jdbc.update("UPDATE policy SET renewed_from_policy_id = ? WHERE id = ?", renewed, successor);
        long suspended = active(ended.minusYears(1), ended, "1000");
        jdbc.update("UPDATE policy SET status = 'SUSPENDED' WHERE id = ?", suspended);
        long stillRunning = active(TODAY.minusMonths(1), TODAY.plusMonths(11), "1000");

        JobExecution execution = run(policyExpirationJob, runDate(TODAY));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(status(expiring)).isEqualTo("EXPIRED");
        assertThat(status(renewed)).isEqualTo("RENEWED");
        assertThat(status(suspended)).isEqualTo("EXPIRED");
        assertThat(status(successor)).isEqualTo("ACTIVE");
        assertThat(status(stillRunning)).isEqualTo("ACTIVE");
        assertThat(notifications(expiring, "POLICY_EXPIRED")).isEqualTo(1);
        assertThat(notifications(renewed, "POLICY_EXPIRED")).isZero();
    }

    @Test
    void claimImportSkipsAndReportsInvalidLines(@TempDir Path dir) throws Exception {
        long policy = active(TODAY.minusMonths(3), TODAY.plusMonths(9), "1000");
        String number = jdbc.queryForObject("SELECT policy_number FROM policy WHERE id = ?", String.class, policy);
        String incident = TODAY.minusDays(20).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Path csv = dir.resolve("hasar.csv");
        Files.writeString(csv, String.join("\n",
                "policy_number;incident_date;description;estimated_amount;incident_city_plate",
                number + ";" + incident + ";Arka tampon hasarı;12.500,50;34",                     // 2 ok
                "TRF-1999-999999;" + incident + ";Bilinmeyen poliçe;1000",                         // 3 unknown policy
                number + ";31.02.2026;Geçersiz tarih;1000",                                         // 4 impossible date
                number + ";" + TODAY.plusDays(3).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        + ";Gelecek tarih;1000",                                                    // 5 future date
                number + ";" + incident + ";Negatif tutar;-5",                                      // 6 negative amount
                number + ";" + incident + ";Arka tampon hasarı;12.500,50;34",                     // 7 duplicate of 2
                "\"" + number + "\";" + TODAY.minusDays(5) + ";\"Cam kırılması\";3000.75",        // 8 ok (quoted, ISO date)
                "sadece;üç;alan"), StandardCharsets.UTF_8);                                         // 9 wrong field count

        JobExecution execution = run(claimImportJob, new JobOperatorTestUtils(jobOperator, jobRepository)
                .getUniqueJobParametersBuilder()
                .addString(BatchJobs.PARAM_INPUT_FILE, csv.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(BatchJobs.COMPLETED_WITH_SKIPS);
        var step = execution.getStepExecutions().iterator().next();
        assertThat(step.getReadCount()).isEqualTo(8);
        assertThat(step.getWriteCount()).isEqualTo(2);
        assertThat(step.getProcessSkipCount()).isEqualTo(6);

        List<Map<String, Object>> claims = jdbc.queryForList("""
                SELECT status, estimated_amount, description, created_by FROM claim WHERE policy_id = ? ORDER BY id
                """, policy);
        assertThat(claims).extracting(c -> c.get("DESCRIPTION")).containsExactly("Arka tampon hasarı", "Cam kırılması");
        assertThat((BigDecimal) claims.get(0).get("ESTIMATED_AMOUNT")).isEqualByComparingTo("12500.50");
        assertThat(claims).allSatisfy(c -> assertThat(c.get("STATUS")).isEqualTo("REPORTED"));

        List<Map<String, Object>> errors = jdbc.queryForList(
                "SELECT line_number, error_message FROM claim_import_error WHERE job_execution_id = ? ORDER BY line_number",
                execution.getId());
        assertThat(errors).extracting(e -> ((Number) e.get("LINE_NUMBER")).intValue()).containsExactly(3, 4, 5, 6, 7, 9);
        assertThat(errors.get(0).get("ERROR_MESSAGE")).isEqualTo("Poliçe bulunamadı: TRF-1999-999999");
        assertThat((String) errors.get(1).get("ERROR_MESSAGE")).startsWith("Geçersiz hasar tarihi");
        assertThat((String) errors.get(2).get("ERROR_MESSAGE")).startsWith("Hasar tarihi ileri bir tarih olamaz");
        assertThat((String) errors.get(4).get("ERROR_MESSAGE")).isEqualTo("Bu hasar ihbarı daha önce kaydedilmiş.");
    }

    @Test
    void claimImportFailsWhenFileIsMissing() throws Exception {
        JobExecution execution = run(claimImportJob, new JobOperatorTestUtils(jobOperator, jobRepository)
                .getUniqueJobParametersBuilder()
                .addString(BatchJobs.PARAM_INPUT_FILE, "does-not-exist.csv")
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    void productionReportIsWrittenToExcel() throws Exception {
        long policy = active(TODAY.minusDays(1), TODAY.plusYears(1), "1234.56"); // issued now -> this month
        YearMonth month = YearMonth.from(TODAY);

        JobExecution execution = run(monthlyProductionReportJob, new JobOperatorTestUtils(jobOperator, jobRepository)
                .getUniqueJobParametersBuilder()
                .addString(BatchJobs.PARAM_MONTH, month.toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        Path report = batchProperties.reportDir().resolve("uretim-raporu-" + month + ".xlsx");
        assertThat(report).exists();
        try (InputStream in = Files.newInputStream(report); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).startsWith("Aylık Prim Üretim Raporu");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Ürün");
            Row totals = sheet.getRow(sheet.getLastRowNum());
            assertThat(totals.getCell(0).getStringCellValue()).isEqualTo("TOPLAM");

            BigDecimal expectedGross = jdbc.queryForObject("""
                    SELECT SUM(gross_premium) FROM policy
                     WHERE issued_at >= TRUNC(SYSDATE, 'MM') AND issued_at < ADD_MONTHS(TRUNC(SYSDATE, 'MM'), 1)
                    """, BigDecimal.class);
            assertThat(BigDecimal.valueOf(totals.getCell(6).getNumericCellValue()))
                    .isEqualByComparingTo(expectedGross.setScale(2));
        }
        assertThat(policy).isPositive();
    }

    /** ACTIVE TRAFFIC policy with 4 installments; the first 3 are already due and unpaid. */
    private long policyWithPastInstallments() {
        LocalDate start = TODAY.minusDays(75);
        long policy = active(start, start.plusYears(1), "4000.00");
        fixtures.installments(policy, 4); // due: -75, -45, -15 days and +15 days
        return policy;
    }

    private void assertOverdueOutcome(long policy) {
        assertThat(jdbc.queryForList("SELECT status FROM installment WHERE policy_id = ? ORDER BY installment_no",
                String.class, policy)).containsExactly("OVERDUE", "OVERDUE", "OVERDUE", "PENDING");
        assertThat(status(policy)).isEqualTo("SUSPENDED");
        assertThat(notifications(policy, "INSTALLMENT_OVERDUE")).isEqualTo(3);
        assertThat(notifications(policy, "POLICY_SUSPENDED")).isEqualTo(1);
    }

    private long active(LocalDate start, LocalDate end, String gross) {
        return fixtures.trafficPolicy(customer, vehicle, "ACTIVE", start, end, new BigDecimal(gross), 4, null);
    }

    private JobExecution run(Job job, JobParameters parameters) throws Exception {
        JobOperatorTestUtils utils = new JobOperatorTestUtils(jobOperator, jobRepository);
        utils.setJob(job);
        return utils.startJob(parameters);
    }

    private JobParameters runDate(LocalDate date) {
        return new JobOperatorTestUtils(jobOperator, jobRepository).getUniqueJobParametersBuilder()
                .addLocalDate(BatchJobs.PARAM_RUN_DATE, date)
                .toJobParameters();
    }

    private String status(long policyId) {
        return jdbc.queryForObject("SELECT status FROM policy WHERE id = ?", String.class, policyId);
    }

    private int notifications(long policyId, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE policy_id = ? AND notification_type = ?",
                Integer.class, policyId, type);
    }

    private static LocalDate date(Object value) {
        return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate();
    }
}
