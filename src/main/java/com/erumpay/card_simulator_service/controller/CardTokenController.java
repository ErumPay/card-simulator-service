package com.erumpay.card_simulator_service.controller;

import com.erumpay.card_simulator_service.dto.api.request.TokenDeleteRequest;
import com.erumpay.card_simulator_service.dto.api.response.TokenDeleteResponse;
import com.erumpay.card_simulator_service.dto.api.request.TokenInquireRequest;
import com.erumpay.card_simulator_service.dto.api.request.TokenIssueRequest;
import com.erumpay.card_simulator_service.dto.api.response.TokenResponse;
import com.erumpay.card_simulator_service.service.CardTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/card-simulator/token")
@RequiredArgsConstructor
@Validated
public class CardTokenController {

    private final CardTokenService cardTokenService;

    @PostMapping("/issue")
    public TokenResponse issue(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid TokenIssueRequest request) {
        return cardTokenService.issue(idempotencyKey, request);
    }

    @PostMapping("/delete")
    public TokenDeleteResponse delete(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid TokenDeleteRequest request) {
        return cardTokenService.delete(idempotencyKey, request);
    }

    @PostMapping("/inquire")
    public TokenResponse inquire(@RequestBody @Valid TokenInquireRequest request) {
        return cardTokenService.inquire(request);
    }
}
