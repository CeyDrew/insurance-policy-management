package com.portfolio.insurance.batch.claimimport;

/** One raw data line of the CSV file; parsing happens in the processor so invalid lines can be skipped and reported. */
record ClaimCsvLine(int lineNumber, String content) {
}
