package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.collection.BankRecord.Detail;
import com.portfolio.insurance.batch.collection.BankRecord.Header;
import org.springframework.batch.infrastructure.item.file.LineMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Plays the bank for the demo and the volume test: answers a request file with a result file. The answer of each
 * line is derived from the installment reference, so the same request always gets the same result: about 88%
 * collected, the rest spread over the reject codes.
 */
public final class BankSimulator {

    private BankSimulator() {
    }

    public static void answer(Path requestFile, Path resultFile, LocalDate answerDate) {
        LineMapper<BankRecord> mapper = BankFile.lineMapper();
        try (BufferedReader in = Files.newBufferedReader(requestFile, StandardCharsets.US_ASCII);
             BufferedWriter out = Files.newBufferedWriter(resultFile, StandardCharsets.US_ASCII)) {
            String line;
            int lineNumber = 0;
            while ((line = in.readLine()) != null) {
                BankRecord record = mapper.mapLine(line, ++lineNumber);
                String answer = switch (record) {
                    case Header h -> BankFile.header(h.bankCode(), answerDate, h.fileNumber(), BankFile.TYPE_RESULT);
                    case Detail d -> BankFile.detail(d.lineNo(), d.installmentId(), d.policyNumber(),
                            d.installmentNo(), d.dueDate(), d.amount(), resultCode(d.installmentId()));
                    // the bank echoes the control totals; the amounts are the requested ones
                    case BankRecord.Trailer t -> line;
                };
                out.write(answer);
                out.write('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not answer " + requestFile, e);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid request file " + requestFile + ": " + e.getMessage(), e);
        }
    }

    static String resultCode(long installmentId) {
        long bucket = Math.floorMod(installmentId * 7919, 100);
        if (bucket < 88) {
            return "00";
        } else if (bucket < 94) {
            return "51";
        } else if (bucket < 97) {
            return "54";
        } else if (bucket < 99) {
            return "57";
        }
        return "91";
    }
}
