package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.collection.CollectionFileService;
import com.portfolio.insurance.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Request export and result import against Oracle. Jobs commit, so the tests are not transactional; each test
 * creates its own policies (one installment due today each) and asserts only on those.
 */
@IntegrationTest
class CollectionJobsIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CollectionFaultInjector faultInjector;

    @Autowired
    private CollectionFileService collectionFileService;

    @Autowired
    @Qualifier(BatchJobs.COLLECTION_REQUEST_EXPORT)
    private Job exportJob;

    @Autowired
    @Qualifier(BatchJobs.COLLECTION_RESULT_IMPORT)
    private Job importJob;

    @TempDir
    private Path dir;

    private PlsqlFixtures fixtures;
    private long customer;
    private long vehicle;

    @BeforeEach
    void setUp() {
        fixtures = new PlsqlFixtures(jdbc);
        customer = fixtures.individual(LocalDate.of(1979, 9, 9), 34);
        fixtures.contact(customer, "otomatik.odeme@example.com", null);
        vehicle = fixtures.vehicle(customer, 2021, new BigDecimal("900000"), "PRIVATE");
    }

    @AfterEach
    void tearDown() {
        faultInjector.off();
        // keep the seed data's installments requestable for other tests
        jdbc.update("UPDATE installment SET collection_request_file_id = NULL WHERE status <> 'PAID'");
    }

    @Test
    void requestFileListsDueInstallmentsWithControlTotals() throws Exception {
        List<Line> ours = installments(3, "1200.00");

        JobExecution execution = run(exportJob, unique().addLocalDate(BatchJobs.PARAM_RUN_DATE, TODAY)
                .addString(BatchJobs.PARAM_LAUNCHED_BY, "test").toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        long fileId = execution.getExecutionContext().getLong(CollectionRequestExportJobConfig.CONTEXT_FILE_ID);
        Path file = Path.of(execution.getExecutionContext().getString(CollectionRequestExportJobConfig.CONTEXT_FILE_PATH));
        List<String> lines = Files.readAllLines(file, StandardCharsets.US_ASCII);
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(BankFile.LINE_LENGTH));

        var mapper = BankFile.lineMapper();
        var header = (BankRecord.Header) mapper.mapLine(lines.getFirst(), 1);
        assertThat(header.fileType()).isEqualTo(BankFile.TYPE_REQUEST);
        assertThat(header.fileNumber()).isEqualTo(fileId);
        List<BankRecord.Detail> details = new ArrayList<>();
        for (int i = 1; i < lines.size() - 1; i++) {
            details.add((BankRecord.Detail) mapper.mapLine(lines.get(i), i + 1));
        }
        var trailer = (BankRecord.Trailer) mapper.mapLine(lines.getLast(), lines.size());
        assertThat(trailer.recordCount()).isEqualTo(details.size());
        assertThat(trailer.totalAmount()).isEqualByComparingTo(
                details.stream().map(BankRecord.Detail::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(details).extracting(BankRecord.Detail::lineNo)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, details.size()).mapToObj(Long::valueOf).toList());
        assertThat(details).extracting(BankRecord.Detail::installmentId)
                .containsAll(ours.stream().map(Line::installmentId).toList());
        assertThat(details).filteredOn(d -> d.installmentId() == ours.getFirst().installmentId()).singleElement()
                .satisfies(d -> {
                    assertThat(d.amount()).isEqualByComparingTo("300.00");
                    assertThat(d.resultCode()).isEmpty();
                });

        Map<String, Object> registered = jdbc.queryForMap(
                "SELECT status, record_count, total_amount, file_hash FROM collection_file WHERE id = ?", fileId);
        assertThat(registered.get("STATUS")).isEqualTo("GENERATED");
        assertThat(((Number) registered.get("RECORD_COUNT")).longValue()).isEqualTo(details.size());
        assertThat(registered.get("FILE_HASH")).isEqualTo(FileHash.sha256(file));
        assertThat(requestFileOf(ours.getFirst().installmentId())).isEqualTo(fileId);

        // Already requested installments wait for the answer instead of being requested again.
        JobExecution second = run(exportJob, unique().addLocalDate(BatchJobs.PARAM_RUN_DATE, TODAY).toJobParameters());
        Path secondFile = Path.of(second.getExecutionContext().getString(CollectionRequestExportJobConfig.CONTEXT_FILE_PATH));
        assertThat(Files.readString(secondFile)).doesNotContain(ours.getFirst().policyNumber());
    }

    @Test
    void resultFileCollectsRejectsAndReportsMismatches() throws Exception {
        List<Line> ours = installments(3, "1200.00");
        long request = requestFile();
        Line paid = ours.get(0);
        Line rejected = ours.get(1);
        Line wrongAmount = ours.get(2);
        Path result = resultFile(request, List.of(
                paid.answer("00"),
                rejected.answer("51"),
                new Line(wrongAmount.installmentId(), wrongAmount.policyNumber(), 1, new BigDecimal("1.00"), "00"),
                new Line(999_999_999L, "TST-0", 1, new BigDecimal("5.00"), "00")));

        JobExecution execution = run(importJob, importParameters(result, 2));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        long fileId = inboundFileOf(execution);

        assertThat(installmentStatus(paid.installmentId())).isEqualTo("PAID");
        assertThat(jdbc.queryForMap("SELECT payment_method, reference_no FROM payment WHERE installment_id = ?",
                paid.installmentId()))
                .containsEntry("PAYMENT_METHOD", "DIRECT_DEBIT")
                .containsEntry("REFERENCE_NO", "OTO-%06d-%08d".formatted(fileId, 1));

        assertThat(installmentStatus(rejected.installmentId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT collection_reject_code FROM installment WHERE id = ?", String.class,
                rejected.installmentId())).isEqualTo("51");
        assertThat(notifications("COLLECTION_REJECTED")).isEqualTo(1);
        assertThat(notifications("PAYMENT_RECEIVED")).isEqualTo(1);

        assertThat(installmentStatus(wrongAmount.installmentId())).isEqualTo("PENDING");
        Map<String, Object> file = jdbc.queryForMap("""
                SELECT status, record_count, collected_count, collected_amount, rejected_count, error_count
                  FROM collection_file WHERE id = ?
                """, fileId);
        assertThat(file).containsEntry("STATUS", "COMPLETED");
        assertThat(numbers(file)).containsEntry("RECORD_COUNT", 4L).containsEntry("COLLECTED_COUNT", 1L)
                .containsEntry("REJECTED_COUNT", 1L).containsEntry("ERROR_COUNT", 2L);
        assertThat((BigDecimal) file.get("COLLECTED_AMOUNT")).isEqualByComparingTo("300.00");
        assertThat(jdbc.queryForList(
                "SELECT message FROM collection_result WHERE collection_file_id = ? AND outcome = 'ERROR' ORDER BY line_no",
                String.class, fileId))
                .containsExactly("Tutar uyuşmuyor: beklenen 300,00 TL.", "Taksit bulunamadı.");
    }

    @Test
    void trailerMismatchRejectsTheWholeFile() throws Exception {
        List<Line> ours = installments(2, "1200.00");
        long request = requestFile();
        List<String> lines = new ArrayList<>(Files.readAllLines(
                resultFile(request, ours.stream().map(line -> line.answer("00")).toList())));
        lines.set(lines.size() - 1, BankFile.trailer(3, new BigDecimal("600.00"))); // one line "lost"
        Path broken = dir.resolve("broken.txt");
        Files.write(broken, lines, StandardCharsets.US_ASCII);

        JobExecution execution = run(importJob, importParameters(broken, 1));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        Map<String, Object> file = jdbc.queryForMap(
                "SELECT id, status, error_message FROM collection_file WHERE file_hash = ?", FileHash.sha256(broken));
        assertThat(file.get("STATUS")).isEqualTo("FAILED");
        assertThat((String) file.get("ERROR_MESSAGE")).isEqualTo("Trailer kayıt sayısı 3, dosyadaki detay sayısı 2.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM collection_result WHERE collection_file_id = ?",
                Long.class, file.get("ID"))).isZero();
        assertThat(ours).allSatisfy(line -> assertThat(installmentStatus(line.installmentId())).isEqualTo("PENDING"));

        // The corrected file is a different file (different hash) and goes through.
        Path fixed = resultFile(request, ours.stream().map(line -> line.answer("00")).toList());
        assertThat(run(importJob, importParameters(fixed, 1)).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(ours).allSatisfy(line -> assertThat(installmentStatus(line.installmentId())).isEqualTo("PAID"));
    }

    @Test
    void theSameResultFileIsAppliedOnlyOnce() throws Exception {
        List<Line> ours = installments(1, "1200.00");
        Path result = resultFile(requestFile(), List.of(ours.getFirst().answer("00")));
        JobParameters parameters = importParameters(result, 1);
        assertThat(run(importJob, parameters).getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Same content = same hash = same job instance, which is already complete ...
        assertThatThrownBy(() -> jobOperator.start(importJob, importParameters(copyOf(result), 1)))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        // ... and the service refuses it before launching anything.
        assertThatThrownBy(() -> collectionFileService.importResult(copyOf(result), "again.txt", 1, "test", true))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_FILE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE installment_id = ?", Long.class,
                ours.getFirst().installmentId())).isEqualTo(1);
    }

    @Test
    void anInterruptedImportResumesWhereItStoppedWithoutCollectingTwice() throws Exception {
        // 150 lines, chunks of 100, two partitions of 75 lines: the crash at line 130 rolls back the second
        // partition's only chunk, the first partition completes.
        List<Line> ours = installments(150, "400.00");
        Path result = resultFile(requestFile(), ours.stream().map(line -> line.answer("00")).toList());
        faultInjector.failAtLine(130);

        JobExecution failed = run(importJob, importParameters(result, 2));

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        long fileId = inboundFileOf(failed);
        assertThat(appliedLines(fileId)).isEqualTo(75);
        assertThat(jdbc.queryForMap("SELECT status, error_message FROM collection_file WHERE id = ?", fileId))
                .containsEntry("STATUS", "PROCESSING")
                .hasEntrySatisfying("ERROR_MESSAGE", message -> assertThat((String) message)
                        .startsWith("Yarıda kesildi").contains("line 130"));
        assertThatThrownBy(() -> collectionFileService.importResult(copyOf(result), "copy.txt", 1, "test", true))
                .isInstanceOf(BusinessException.class);

        faultInjector.off();
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getStepExecutions()).extracting(StepExecution::getStepName)
                .doesNotContain("validateResultFileStep", "applyResultsWorkerStep:partition0");
        assertThat(appliedLines(fileId)).isEqualTo(150);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment p
                 WHERE p.installment_id IN (SELECT installment_id FROM collection_result WHERE collection_file_id = ?)
                """, Long.class, fileId)).isEqualTo(150);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM collection_result WHERE collection_file_id = ? AND outcome <> 'COLLECTED'
                """, Long.class, fileId)).isZero();
        assertThat(jdbc.queryForMap(
                "SELECT status, collected_count, error_message FROM collection_file WHERE id = ?", fileId))
                .containsEntry("STATUS", "COMPLETED")
                .containsEntry("ERROR_MESSAGE", null)
                .hasEntrySatisfying("COLLECTED_COUNT", count -> assertThat(((Number) count).intValue()).isEqualTo(150));
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    /** A line of a result file; installment no 1 is always the one due today. */
    private record Line(long installmentId, String policyNumber, int installmentNo, BigDecimal amount,
                        String resultCode) {

        Line answer(String code) {
            return new Line(installmentId, policyNumber, installmentNo, amount, code);
        }
    }

    /** {@code count} active policies starting today, 4 installments each; returns their first installments. */
    private List<Line> installments(int count, String grossPremium) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long policy = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", TODAY, TODAY.plusYears(1),
                    new BigDecimal(grossPremium), 4, null);
            fixtures.installments(policy, 4);
            lines.add(jdbc.queryForObject("""
                    SELECT i.id, p.policy_number, i.amount FROM installment i JOIN policy p ON p.id = i.policy_id
                     WHERE p.id = ? AND i.installment_no = 1
                    """, (rs, rowNum) -> new Line(rs.getLong("id"), rs.getString("policy_number"), 1,
                    rs.getBigDecimal("amount"), "  "), policy));
        }
        return lines;
    }

    private long requestFile() {
        long id = jdbc.queryForObject("SELECT collection_file_seq.NEXTVAL FROM dual", Long.class);
        jdbc.update("""
                INSERT INTO collection_file (id, direction, file_name, file_date, status, record_count, total_amount)
                VALUES (?, 'OUTBOUND', 'TL-test.txt', ?, 'GENERATED', 0, 0)
                """, id, TODAY);
        return id;
    }

    private Path resultFile(long requestFileId, List<Line> answers) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(BankFile.header("0099", TODAY, requestFileId, BankFile.TYPE_RESULT));
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < answers.size(); i++) {
            Line line = answers.get(i);
            lines.add(BankFile.detail(i + 1, line.installmentId(), line.policyNumber(), line.installmentNo(), TODAY,
                    line.amount(), line.resultCode()));
            total = total.add(line.amount());
        }
        lines.add(BankFile.trailer(answers.size(), total));
        Path file = Files.createTempFile(dir, "SN-", ".txt");
        Files.write(file, lines, StandardCharsets.US_ASCII);
        return file;
    }

    private Path copyOf(Path file) throws Exception {
        return Files.copy(file, Files.createTempFile(dir, "copy-", ".txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private JobParameters importParameters(Path file, int partitions) {
        return new JobParametersBuilder()
                .addString(BatchJobs.PARAM_FILE_HASH, FileHash.sha256(file))
                .addString(BatchJobs.PARAM_INPUT_FILE, file.toString(), false)
                .addString(BatchJobs.PARAM_ORIGINAL_FILE_NAME, file.getFileName().toString(), false)
                .addString(BatchJobs.PARAM_LAUNCHED_BY, "test", false)
                .addLong(BatchJobs.PARAM_PARTITIONS, (long) partitions, false)
                .toJobParameters();
    }

    private JobParametersBuilder unique() {
        return new JobOperatorTestUtils(jobOperator, jobRepository).getUniqueJobParametersBuilder();
    }

    private JobExecution run(Job job, JobParameters parameters) throws Exception {
        return jobOperator.start(job, parameters);
    }

    private long inboundFileOf(JobExecution execution) {
        return execution.getExecutionContext().getLong(ResultFileValidationTasklet.CONTEXT_FILE_ID);
    }

    private long appliedLines(long fileId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM collection_result WHERE collection_file_id = ?",
                Long.class, fileId);
    }

    private String installmentStatus(long installmentId) {
        return jdbc.queryForObject("SELECT status FROM installment WHERE id = ?", String.class, installmentId);
    }

    private Long requestFileOf(long installmentId) {
        return jdbc.queryForObject("SELECT collection_request_file_id FROM installment WHERE id = ?", Long.class,
                installmentId);
    }

    private long notifications(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE customer_id = ? AND notification_type = ?",
                Long.class, customer, type);
    }

    private static Map<String, Long> numbers(Map<String, Object> row) {
        Map<String, Long> result = new java.util.HashMap<>();
        row.forEach((key, value) -> {
            if (value instanceof Number number && !(value instanceof BigDecimal decimal && decimal.scale() > 0)) {
                result.put(key, number.longValue());
            }
        });
        return result;
    }
}
