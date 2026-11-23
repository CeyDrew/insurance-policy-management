package com.portfolio.insurance.payment;

public enum PaymentMethod {
    CREDIT_CARD,
    BANK_TRANSFER,
    CASH,
    /** Bank auto-debit (otomatik ödeme talimatı); only recorded from bank result files. */
    DIRECT_DEBIT
}
