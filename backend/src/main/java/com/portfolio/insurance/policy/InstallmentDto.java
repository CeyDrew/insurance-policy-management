package com.portfolio.insurance.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InstallmentDto(Long id, Integer installmentNo, LocalDate dueDate, BigDecimal amount,
                             InstallmentStatus status, LocalDateTime paidAt) {
}
