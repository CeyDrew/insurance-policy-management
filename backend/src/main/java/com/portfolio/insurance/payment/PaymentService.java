package com.portfolio.insurance.payment;

import com.portfolio.insurance.common.plsql.DatabaseSideChanges;
import com.portfolio.insurance.common.web.PageResponse;
import com.portfolio.insurance.payment.PaymentDtos.InstallmentRow;
import com.portfolio.insurance.payment.PaymentDtos.PaymentResult;
import com.portfolio.insurance.policy.InstallmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final InstallmentQueryRepository installmentQueries;
    private final PaymentProcessor paymentProcessor;
    private final DatabaseSideChanges databaseSideChanges;
    private final Clock clock;

    /**
     * @param unpaidOnly PENDING and OVERDUE installments (ignored when a status is given)
     */
    public PageResponse<InstallmentRow> installments(InstallmentStatus status, boolean unpaidOnly, String query,
                                                     Long policyId, LocalDate dueTo, int page, int size) {
        var filter = new InstallmentQueryRepository.Filter(status, unpaidOnly, query, policyId, dueTo);
        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);
        long total = installmentQueries.count(filter);
        return new PageResponse<>(installmentQueries.find(filter, LocalDate.now(clock), pageIndex, pageSize),
                pageIndex, pageSize, total, (int) Math.ceil((double) total / pageSize));
    }

    /** Collects an installment in full; a suspended policy becomes active once nothing is overdue. */
    @Transactional
    public PaymentResult pay(Long installmentId, PaymentMethod method, String username) {
        PaymentProcessor.Outcome outcome = databaseSideChanges.apply(
                () -> paymentProcessor.payInstallment(installmentId, method, username));
        return installmentQueries.paymentResult(outcome.paymentId(), outcome.reactivated());
    }
}
