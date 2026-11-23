package com.portfolio.insurance.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPolicyRequest(
        @NotBlank(message = "İptal nedeni zorunludur.")
        @Size(max = 300, message = "İptal nedeni en fazla 300 karakter olabilir.")
        String reason) {
}
