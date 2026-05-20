package com.erumpay.card_simulator_service.controller;

import com.erumpay.card_simulator_service.dto.PaymentApproveRequest;
import com.erumpay.card_simulator_service.dto.PaymentApproveResponse;
import com.erumpay.card_simulator_service.dto.PaymentCancelRequest;
import com.erumpay.card_simulator_service.dto.PaymentCancelResponse;
import com.erumpay.card_simulator_service.dto.PaymentInquireRequest;
import com.erumpay.card_simulator_service.dto.PaymentInquireResponse;
import com.erumpay.card_simulator_service.service.PaymentService;
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
@RequestMapping("/api/v1/card-simulator/payment")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/approve")
    public PaymentApproveResponse approve(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid PaymentApproveRequest request) {
        return paymentService.approve(idempotencyKey, request);
    }

    @PostMapping("/cancel")
    public PaymentCancelResponse cancel(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid PaymentCancelRequest request) {
        return paymentService.cancel(idempotencyKey, request);
    }

    @PostMapping("/inquire")
    public PaymentInquireResponse inquire(@RequestBody @Valid PaymentInquireRequest request) {
        return paymentService.inquire(request);
    }
}
