package com.erumpay.card_simulator_service.dto.api.response;

import com.erumpay.card_simulator_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record PerformanceInquireResponse(
        @JsonProperty("card_company") CardCompany cardCompany,
        @JsonProperty("product_name") String productName,
        @JsonProperty("inquiry_period") String inquiryPeriod,
        @JsonProperty("current_amount") Long currentAmount,
        @JsonProperty("response_http") Integer responseHttp,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_reason") String responseReason,
        @JsonProperty("response_message") String responseMessage
) {
}
