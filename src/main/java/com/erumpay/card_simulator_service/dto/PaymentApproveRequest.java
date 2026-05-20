package com.erumpay.card_simulator_service.dto;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentApproveRequest(
        @JsonProperty("pg_id") @NotBlank String pgId,
        @JsonProperty("pg_txn_id") @NotNull Long pgTxnId,
        @JsonProperty("card_company") @NotNull CardCompany cardCompany,
        @JsonProperty("card_token") @NotBlank String cardToken,
        @JsonProperty("original_amount") @NotNull @Positive Long originalAmount,
        @JsonProperty("approved_amount") @NotNull @Positive Long approvedAmount
) {
}
