package com.portfolio.insurance.batch.renewal;

import com.portfolio.insurance.premium.PremiumResult;

import java.time.LocalDate;

/** A priced renewal quote, ready to be inserted as a QUOTE policy linked to its predecessor. */
record RenewalQuote(RenewalCandidate previous, LocalDate startDate, LocalDate endDate, PremiumResult premium) {
}
