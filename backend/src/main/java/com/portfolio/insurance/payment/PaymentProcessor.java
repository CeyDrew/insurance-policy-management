package com.portfolio.insurance.payment;

/**
 * Records a payment against an installment (PKG_PAYMENT). Collection is simulated: no payment provider is called.
 */
public interface PaymentProcessor {

    /**
     * @param reactivated true if the payment lifted the policy's suspension
     */
    record Outcome(long paymentId, boolean reactivated) {
    }

    Outcome payInstallment(Long installmentId, PaymentMethod method, String paidBy);
}
