package com.portfolio.insurance.batch.collection;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of a bank collection file (docs/bank-file-format.md). */
public sealed interface BankRecord {

    /**
     * @param fileNumber number of the request file; a result file repeats the number of the request it answers
     * @param fileType   {@link BankFile#TYPE_REQUEST} or {@link BankFile#TYPE_RESULT}
     */
    record Header(String bankCode, LocalDate fileDate, long fileNumber, String fileType, String sender)
            implements BankRecord {
    }

    /**
     * @param lineNo        1-based position among the detail lines
     * @param installmentId the company's installment reference
     * @param resultCode    blank in a request, the bank's answer in a result file
     */
    record Detail(long lineNo, long installmentId, String policyNumber, int installmentNo, LocalDate dueDate,
                  BigDecimal amount, String resultCode) implements BankRecord {
    }

    /** Control totals over all detail lines. */
    record Trailer(long recordCount, BigDecimal totalAmount) implements BankRecord {
    }
}
