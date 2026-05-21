package com.erumpay.card_simulator_service.dto.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PaymentInquireRequest(
        @JsonProperty("target_idempotency_key") @NotBlank String targetIdempotencyKey
) {
}
