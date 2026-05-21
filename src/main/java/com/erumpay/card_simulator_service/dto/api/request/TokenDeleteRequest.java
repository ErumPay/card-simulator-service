package com.erumpay.card_simulator_service.dto.api.request;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TokenDeleteRequest(
        @JsonProperty("pg_id") @NotBlank @Size(max = 20) String pgId,
        @JsonProperty("card_company") @NotNull CardCompany cardCompany,
        @JsonProperty("card_token") @NotBlank String cardToken
) {
}
