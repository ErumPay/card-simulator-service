package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.Category;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
import com.erumpay.card_simulator_service.exception.CustomException;
import com.erumpay.card_simulator_service.exception.ErrorCode;
import com.erumpay.card_simulator_service.repository.SimulatorResponseCodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class ResponseCodeResolver {

    // 부팅 시 존재해야 하는 (Category, ResponseType) 매핑. 호출 흐름에서 실제로 resolve(...) 되는 조합만 포함.
    private static final Set<CategoryTypeKey> REQUIRED_MAPPINGS = Set.of(
            new CategoryTypeKey(Category.TOKEN, ResponseType.SUCCESS),
            new CategoryTypeKey(Category.TOKEN, ResponseType.TOKEN_NOT_FOUND),
            new CategoryTypeKey(Category.TOKEN, ResponseType.TOKEN_DUPLICATE),
            new CategoryTypeKey(Category.TOKEN, ResponseType.TOKEN_ALREADY_DELETED),
            new CategoryTypeKey(Category.TOKEN, ResponseType.TOKEN_ISSUE_NOT_FOUND),
            new CategoryTypeKey(Category.CARD, ResponseType.SUCCESS),
            new CategoryTypeKey(Category.CARD, ResponseType.CARD_NOT_FOUND),
            new CategoryTypeKey(Category.CARD, ResponseType.CARD_LOST),
            new CategoryTypeKey(Category.CARD, ResponseType.CARD_EXPIRED),
            new CategoryTypeKey(Category.CARD, ResponseType.CARD_DELETED),
            new CategoryTypeKey(Category.CARD, ResponseType.CARD_INVALID_EXPIRY),
            new CategoryTypeKey(Category.CARD, ResponseType.CARD_INVALID_CVC),
            new CategoryTypeKey(Category.CARD, ResponseType.CARD_INVALID_PASSWORD),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.SUCCESS),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.PAYMENT_LIMIT_EXCEEDED),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.PAYMENT_INSUFFICIENT_BALANCE),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.PAYMENT_CARD_EXPIRED),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.PAYMENT_CARD_LOST),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.PAYMENT_CARD_DELETED),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.PAYMENT_TOKEN_INVALID),
            new CategoryTypeKey(Category.PAYMENT, ResponseType.PAYMENT_CARD_NOT_FOUND),
            new CategoryTypeKey(Category.TRANSACTION, ResponseType.TRANSACTION_NOT_FOUND),
            new CategoryTypeKey(Category.TRANSACTION, ResponseType.TRANSACTION_ALREADY_PROCESSED),
            new CategoryTypeKey(Category.USER, ResponseType.USER_BIRTH_INVALID),
            new CategoryTypeKey(Category.USER, ResponseType.USER_PHONE_INVALID)
    );

    private final SimulatorResponseCodeRepository responseCodeRepository;

    @PostConstruct
    void verifyResponseCodeMappingsExist() {
        List<CategoryTypeKey> missing = new ArrayList<>();
        for (CategoryTypeKey key : REQUIRED_MAPPINGS) {
            if (responseCodeRepository.findFirstByCategoryAndResponseType(key.category(), key.responseType()).isEmpty()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            log.error("Required response_code mappings missing: {}", missing);
            throw new IllegalStateException(
                    "Required simulator_response_code rows missing: " + missing
                            + ". Seed the DB before booting (check simulator.seed.enabled or run init SQL)."
            );
        }
        log.info("Response code mapping verification passed: {} mappings present.", REQUIRED_MAPPINGS.size());
    }

    public SimulatorResponseCode resolve(Category category, ResponseType responseType) {
        return responseCodeRepository.findFirstByCategoryAndResponseType(category, responseType)
                .orElseThrow(() -> new CustomException(ErrorCode.RESPONSE_CODE_MAPPING_MISSING));
    }

    public SimulatorResponseCode resolveByCode(String responseCode) {
        return responseCodeRepository.findByResponseCode(responseCode)
                .orElseThrow(() -> new CustomException(ErrorCode.RESPONSE_CODE_MAPPING_MISSING));
    }

    private record CategoryTypeKey(Category category, ResponseType responseType) {}
}
