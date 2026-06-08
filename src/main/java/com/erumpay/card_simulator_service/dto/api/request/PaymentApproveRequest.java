package com.erumpay.card_simulator_service.dto.api.request;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// [be] 하지혁 260603 결제 Request DTO
public record PaymentApproveRequest(
        @JsonProperty("pg_id") @NotBlank @Size(max = 20) String pgId,
        @JsonProperty("pg_txn_id") @NotNull @Positive Long pgTxnId,
        @JsonProperty("card_company") @NotNull CardCompany cardCompany,
        @JsonProperty("card_token") @NotBlank String cardToken,
        @JsonProperty("original_amount") @NotNull @Positive Long originalAmount,
        @JsonProperty("approved_amount") @NotNull @Positive Long approvedAmount
) {
}
