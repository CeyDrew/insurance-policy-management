package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.collection.BankRecord.Detail;
import com.portfolio.insurance.batch.collection.BankRecord.Header;
import com.portfolio.insurance.batch.collection.BankRecord.Trailer;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.mapping.PatternMatchingCompositeLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.batch.infrastructure.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.LineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.Range;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * SPT-1, the fictional fixed-width bank collection file format: 120-character lines, one header ({@code H}),
 * detail lines ({@code D}) and a trailer ({@code T}) with control totals. Field positions are documented in
 * docs/bank-file-format.md and defined once here, for both writing and reading.
 */
public final class BankFile {

    public static final int LINE_LENGTH = 120;
    public static final String TYPE_REQUEST = "TL";
    public static final String TYPE_RESULT = "SN";
    public static final String SENDER = "SIGORTA-DEMO";
    public static final String RESULT_COLLECTED = "00";

    /** Bank result codes and their meaning. */
    public static final Map<String, String> RESULT_CODES = Map.of(
            "00", "Tahsil edildi",
            "51", "Yetersiz bakiye",
            "54", "Hesap kapalı",
            "57", "Talimat bulunamadı",
            "91", "Banka sistem hatası");

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Header: H | bank code | file date | file number | file type | sender | filler
    private static final Range[] HEADER = {new Range(1, 1), new Range(2, 5), new Range(6, 13), new Range(14, 23),
            new Range(24, 25), new Range(26, 45), new Range(46, LINE_LENGTH)};
    private static final String[] HEADER_NAMES = {"type", "bankCode", "fileDate", "fileNumber", "fileType", "sender",
            "filler"};

    // Detail: D | line no | installment ref | policy no | installment no | due date | amount (kuruş) | result | filler
    private static final Range[] DETAIL = {new Range(1, 1), new Range(2, 11), new Range(12, 21), new Range(22, 41),
            new Range(42, 43), new Range(44, 51), new Range(52, 66), new Range(67, 68), new Range(69, LINE_LENGTH)};
    private static final String[] DETAIL_NAMES = {"type", "lineNo", "installmentId", "policyNumber", "installmentNo",
            "dueDate", "amount", "resultCode", "filler"};

    // Trailer: T | record count | total amount (kuruş) | filler
    private static final Range[] TRAILER = {new Range(1, 1), new Range(2, 11), new Range(12, 29),
            new Range(30, LINE_LENGTH)};
    private static final String[] TRAILER_NAMES = {"type", "recordCount", "totalAmount", "filler"};

    private static final int POLICY_NUMBER_WIDTH = 20;

    /**
     * Detail line as a {@link java.util.Formatter} pattern; the last argument is the (empty) filler.
     * Used by the request writer's FormatterLineAggregator and by {@link #detail}.
     */
    public static final String DETAIL_FORMAT = "D%010d%010d%-20s%02d%s%015d%-2s%-52s";

    private BankFile() {
    }

    // --- writing -------------------------------------------------------------------------------------------

    public static String header(String bankCode, LocalDate fileDate, long fileNumber, String fileType) {
        return pad(String.format(Locale.ROOT, "H%-4s%s%010d%-2s%-20s", bankCode, DATE.format(fileDate), fileNumber,
                fileType, SENDER));
    }

    /** Request line: result code left blank. */
    public static String detail(long lineNo, long installmentId, String policyNumber, int installmentNo,
                                LocalDate dueDate, BigDecimal amount) {
        return detail(lineNo, installmentId, policyNumber, installmentNo, dueDate, amount, "  ");
    }

    public static String detail(long lineNo, long installmentId, String policyNumber, int installmentNo,
                                LocalDate dueDate, BigDecimal amount, String resultCode) {
        return String.format(Locale.ROOT, DETAIL_FORMAT,
                detailFields(lineNo, installmentId, policyNumber, installmentNo, dueDate, amount, resultCode));
    }

    /** Arguments of {@link #DETAIL_FORMAT}, in order. */
    public static Object[] detailFields(long lineNo, long installmentId, String policyNumber, int installmentNo,
                                        LocalDate dueDate, BigDecimal amount, String resultCode) {
        if (policyNumber.length() > POLICY_NUMBER_WIDTH) {
            throw new IllegalArgumentException("Policy number does not fit the format: " + policyNumber);
        }
        return new Object[]{lineNo, installmentId, policyNumber, installmentNo, DATE.format(dueDate),
                toKurus(amount), resultCode, ""};
    }

    public static String trailer(long recordCount, BigDecimal totalAmount) {
        return pad(String.format(Locale.ROOT, "T%010d%018d", recordCount, toKurus(totalAmount)));
    }

    /** 1275.12 TL -> 127512 kuruş */
    static long toKurus(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    static BigDecimal fromKurus(String digits) {
        return new BigDecimal(digits.trim()).movePointLeft(2);
    }

    private static String pad(String line) {
        if (line.length() > LINE_LENGTH) {
            throw new IllegalArgumentException("Line longer than " + LINE_LENGTH + " characters");
        }
        return line + " ".repeat(LINE_LENGTH - line.length());
    }

    // --- reading -------------------------------------------------------------------------------------------

    /**
     * Maps each line to a {@link BankRecord} by its first character. Tokenizers are strict: a line that is not
     * exactly {@value #LINE_LENGTH} characters long is rejected.
     */
    public static LineMapper<BankRecord> lineMapper() {
        return new PatternMatchingCompositeLineMapper<>(
                Map.<String, LineTokenizer>of(
                        "H*", tokenizer(HEADER, HEADER_NAMES),
                        "D*", tokenizer(DETAIL, DETAIL_NAMES),
                        "T*", tokenizer(TRAILER, TRAILER_NAMES)),
                Map.<String, FieldSetMapper<BankRecord>>of(
                        "H*", BankFile::toHeader,
                        "D*", BankFile::toDetail,
                        "T*", BankFile::toTrailer));
    }

    private static FixedLengthTokenizer tokenizer(Range[] ranges, String[] names) {
        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
        tokenizer.setColumns(ranges);
        tokenizer.setNames(names);
        tokenizer.setStrict(true);
        return tokenizer;
    }

    private static Header toHeader(FieldSet fields) {
        return new Header(fields.readString("bankCode"), date(fields, "fileDate"), fields.readLong("fileNumber"),
                fields.readString("fileType").toUpperCase(Locale.ROOT), fields.readString("sender"));
    }

    private static Detail toDetail(FieldSet fields) {
        return new Detail(fields.readLong("lineNo"), fields.readLong("installmentId"),
                fields.readString("policyNumber"), fields.readInt("installmentNo"), date(fields, "dueDate"),
                fromKurus(fields.readString("amount")), fields.readRawString("resultCode").trim());
    }

    private static Trailer toTrailer(FieldSet fields) {
        return new Trailer(fields.readLong("recordCount"), fromKurus(fields.readString("totalAmount")));
    }

    private static LocalDate date(FieldSet fields, String name) {
        return LocalDate.parse(fields.readString(name), DATE);
    }
}
