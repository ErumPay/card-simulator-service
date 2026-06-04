package com.erumpay.card_simulator_service.dto.api.response;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.erumpay.card_simulator_service.entity.SimulatorCardToken.TokenStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

// [be] 하지혁 260603 카드사 토큰 발급/조회 Response DTO
@Builder
public record TokenResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("token_status") TokenStatus tokenStatus,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("card_company") CardCompany cardCompany,
        @JsonProperty("masked_number") String maskedNumber,
        @JsonProperty("response_http") Integer responseHttp,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_reason") String responseReason,
        @JsonProperty("response_message") String responseMessage
) {
}
