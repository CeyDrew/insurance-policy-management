package com.portfolio.insurance.batch.renewal;

import com.portfolio.insurance.premium.PremiumEngine;
import com.portfolio.insurance.premium.PremiumRequest;
import com.portfolio.insurance.premium.PremiumResult;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.time.LocalDate;

/**
 * Prices the next term with the PL/SQL premium engine. The new term starts the day the current one ends and
 * carries the no-claims step earned in the current term. Tariff errors (e.g. driver now outside the age table)
 * surface as BusinessException and are skipped by the step.
 */
@RequiredArgsConstructor
class RenewalQuoteProcessor implements ItemProcessor<RenewalCandidate, RenewalQuote> {

    private final PremiumEngine premiumEngine;

    @Override
    public RenewalQuote process(RenewalCandidate candidate) {
        LocalDate start = candidate.endDate();
        PremiumResult premium = premiumEngine.calculatePremium(new PremiumRequest(candidate.productCode(),
                candidate.customerId(), candidate.vehicleId(), candidate.propertyId(), candidate.nextNcdStep(), start));
        return new RenewalQuote(candidate, start, start.plusMonths(candidate.termMonths()), premium);
    }
}
