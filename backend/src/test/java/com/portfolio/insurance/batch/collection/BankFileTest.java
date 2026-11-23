package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.collection.BankRecord.Detail;
import com.portfolio.insurance.batch.collection.BankRecord.Header;
import com.portfolio.insurance.batch.collection.BankRecord.Trailer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.LineMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankFileTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 23);

    private final LineMapper<BankRecord> mapper = BankFile.lineMapper();

    @Test
    void writesFixedWidthLinesAtTheDocumentedPositions() {
        String header = BankFile.header("0099", DATE, 42, BankFile.TYPE_REQUEST);
        String detail = BankFile.detail(1, 12345, "KSK-2026-000196", 3, LocalDate.of(2026, 12, 13),
                new BigDecimal("1275.12"));
        String trailer = BankFile.trailer(1, new BigDecimal("1275.12"));

        assertThat(List.of(header, detail, trailer)).allSatisfy(line -> assertThat(line).hasSize(120));
        assertThat(header).startsWith("H009920261123" + "0000000042" + "TL" + "SIGORTA-DEMO        ");
        // D | line no | installment ref | policy no (20) | inst. no | due date | amount in kuruş | result
        assertThat(detail).startsWith("D" + "0000000001" + "0000012345" + "KSK-2026-000196     " + "03"
                + "20261213" + "000000000127512" + "  ");
        assertThat(trailer).startsWith("T" + "0000000001" + "000000000000127512");
    }

    @Test
    void readsBackWhatItWrites() throws Exception {
        Detail detail = (Detail) mapper.mapLine(BankFile.detail(7, 99, "TRF-2026-000200", 12, DATE,
                new BigDecimal("1547.91"), "51"), 2);

        assertThat(detail).isEqualTo(new Detail(7, 99, "TRF-2026-000200", 12, DATE, new BigDecimal("1547.91"), "51"));
        assertThat(mapper.mapLine(BankFile.header("0099", DATE, 5, BankFile.TYPE_RESULT), 1))
                .isEqualTo(new Header("0099", DATE, 5, "SN", "SIGORTA-DEMO"));
        assertThat(mapper.mapLine(BankFile.trailer(3, new BigDecimal("10.50")), 3))
                .isEqualTo(new Trailer(3, new BigDecimal("10.50")));
        // a request line has no result code yet
        assertThat(((Detail) mapper.mapLine(BankFile.detail(1, 1, "X", 1, DATE, BigDecimal.ONE), 2)).resultCode())
                .isEmpty();
    }

    @Test
    void rejectsLinesOfTheWrongLengthOrType() {
        String detail = BankFile.detail(1, 1, "X", 1, DATE, BigDecimal.ONE);

        assertThatThrownBy(() -> mapper.mapLine(detail.substring(0, 119), 2)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.mapLine(detail + " ", 2)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.mapLine("X" + detail.substring(1), 2)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> BankFile.detail(1, 1, "A-POLICY-NUMBER-TOO-LONG", 1, DATE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulatorAnswersEveryLineAndKeepsTheControlTotals(@TempDir Path dir) throws Exception {
        Path request = dir.resolve("request.txt");
        Files.write(request, List.of(
                BankFile.header("0099", DATE, 9, BankFile.TYPE_REQUEST),
                BankFile.detail(1, 100, "TRF-1", 1, DATE, new BigDecimal("10.00")),
                BankFile.detail(2, 101, "TRF-2", 1, DATE, new BigDecimal("20.00")),
                BankFile.trailer(2, new BigDecimal("30.00"))), StandardCharsets.US_ASCII);
        Path result = dir.resolve("result.txt");

        BankSimulator.answer(request, result, DATE.plusDays(1));

        List<String> lines = Files.readAllLines(result, StandardCharsets.US_ASCII);
        assertThat(lines).hasSize(4).allSatisfy(line -> assertThat(line).hasSize(120));
        Header header = (Header) mapper.mapLine(lines.getFirst(), 1);
        assertThat(header.fileType()).isEqualTo(BankFile.TYPE_RESULT);
        assertThat(header.fileNumber()).isEqualTo(9);
        assertThat(((Detail) mapper.mapLine(lines.get(1), 2)).resultCode())
                .isEqualTo(BankSimulator.resultCode(100)).isIn(BankFile.RESULT_CODES.keySet());
        assertThat(mapper.mapLine(lines.getLast(), 4)).isEqualTo(new Trailer(2, new BigDecimal("30.00")));
    }

    @Test
    void simulatorCollectsMostLines() {
        long collected = java.util.stream.LongStream.range(1, 10_001)
                .filter(id -> BankSimulator.resultCode(id).equals("00")).count();
        assertThat(collected).isBetween(8_500L, 9_100L);
    }

    @Test
    void partitionsCoverEveryDetailLineExactlyOnce() {
        var partitions = new DetailRangePartitioner(10, 3).partition(0);

        assertThat(partitions).hasSize(3);
        List<long[]> ranges = partitions.values().stream()
                .map(c -> new long[]{c.getLong(DetailRangePartitioner.FROM_ITEM), c.getLong(DetailRangePartitioner.TO_ITEM)})
                .toList();
        // header is item 1, details are items 2..11
        assertThat(ranges).containsExactly(new long[]{2, 5}, new long[]{6, 8}, new long[]{9, 11});
        assertThat(new DetailRangePartitioner(2, 8).partition(0)).hasSize(2);
        ExecutionContext empty = new DetailRangePartitioner(0, 4).partition(0).values().iterator().next();
        assertThat(empty.getLong(DetailRangePartitioner.TO_ITEM)).isLessThan(empty.getLong(DetailRangePartitioner.FROM_ITEM));
    }
}
