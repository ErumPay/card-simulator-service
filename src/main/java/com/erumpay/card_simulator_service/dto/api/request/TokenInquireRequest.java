package com.erumpay.card_simulator_service.dto.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

// [be] 하지혁 260603 카드사 토큰 조회 Request DTO
public record TokenInquireRequest(
        @JsonProperty("target_idempotency_key") @NotBlank String targetIdempotencyKey
) {
}
