package com.erumpay.card_simulator_service.controller;

import com.erumpay.card_simulator_service.dto.TokenInquireRequest;
import com.erumpay.card_simulator_service.dto.TokenIssueRequest;
import com.erumpay.card_simulator_service.dto.TokenResponse;
import com.erumpay.card_simulator_service.service.CardTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/card-simulator/token")
@RequiredArgsConstructor
public class CardTokenController {

    private final CardTokenService cardTokenService;

    @PostMapping("/issue")
    public TokenResponse issue(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid TokenIssueRequest request) {
        return cardTokenService.issue(idempotencyKey, request);
    }

    @PostMapping("/inquire")
    public TokenResponse inquire(@RequestBody @Valid TokenInquireRequest request) {
        return cardTokenService.inquire(request);
    }
}
