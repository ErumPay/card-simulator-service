package com.erumpay.card_simulator_service.dto;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCancelRequest(
        @JsonProperty("pg_id") @NotBlank String pgId,
        @JsonProperty("origin_idempotency_key") @NotBlank String originIdempotencyKey,
        @JsonProperty("pg_txn_id") @NotNull Long pgTxnId,
        @JsonProperty("origin_pg_txn_id") @NotNull Long originPgTxnId,
        @JsonProperty("card_company") @NotNull CardCompany cardCompany,
        @JsonProperty("card_token") @NotBlank String cardToken,
        @JsonProperty("approval_number") @NotBlank String approvalNumber
) {
}
