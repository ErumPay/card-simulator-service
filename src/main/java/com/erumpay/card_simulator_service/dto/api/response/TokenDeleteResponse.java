package com.erumpay.card_simulator_service.dto.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record TokenDeleteResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
}
