package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.entity.SimulatorResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.Category;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
import com.erumpay.card_simulator_service.repository.SimulatorResponseCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResponseCodeResolver {

    private final SimulatorResponseCodeRepository responseCodeRepository;

    public SimulatorResponseCode resolve(Category category, ResponseType responseType) {
        return responseCodeRepository.findFirstByCategoryAndResponseType(category, responseType)
                .orElseThrow(() -> new IllegalStateException(
                        "Response code mapping not found: " + category + " / " + responseType));
    }
}
