package com.erumpay.card_simulator_service.dto.api.request;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// [be] 하지혁 260603 카드사 토큰 발급 Request DTO
public record TokenIssueRequest(
        @JsonProperty("pg_id") @NotBlank String pgId,
        @JsonProperty("card_company") @NotNull CardCompany cardCompany,
        @JsonProperty("card_number") @NotBlank String cardNumber,
        @JsonProperty("expiry_date") @NotBlank String expiryDate,
        @NotBlank String cvc,
        @JsonProperty("password_2digit") @NotBlank String password2digit,
        @JsonProperty("birth_date") @NotBlank String birthDate
) {
}
