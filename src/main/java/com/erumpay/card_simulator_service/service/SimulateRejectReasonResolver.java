package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.IinMapping;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import com.erumpay.card_simulator_service.entity.SimulatorResponseCode.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// simulate 거절 시 카드형식별 사유 매핑: 신용 = 한도 초과, 체크 = 잔액 부족
@Component
@RequiredArgsConstructor
public class SimulateRejectReasonResolver {

    private final AesCryptoUtil aesCryptoUtil;

    public ResponseType resolve(SimulatorCard card) {
        IinMapping.CardForm form = IinMapping.resolveCardForm(aesCryptoUtil.decrypt(card.getCardNumber()));
        return form == IinMapping.CardForm.CHECK
                ? ResponseType.PAYMENT_INSUFFICIENT_BALANCE
                : ResponseType.PAYMENT_LIMIT_EXCEEDED;
    }
}
