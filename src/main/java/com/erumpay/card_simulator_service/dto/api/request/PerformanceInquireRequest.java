package com.erumpay.card_simulator_service.dto.api.request;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PerformanceInquireRequest(
        @NotBlank String name,
        @JsonProperty("phone_number") @NotBlank String phoneNumber,
        @JsonProperty("card_company") @NotNull CardCompany cardCompany,
        @JsonProperty("product_name") @NotBlank String productName,
        @JsonProperty("inquiry_period") @NotBlank @Pattern(regexp = "\\d{6}") String inquiryPeriod
) {
}
