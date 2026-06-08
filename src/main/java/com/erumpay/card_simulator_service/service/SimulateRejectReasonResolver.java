package com.erumpay.card_simulator_service.service;

import com.erumpay.card_simulator_service.common.AesCryptoUtil;
import com.erumpay.card_simulator_service.common.IinMapping;
import com.erumpay.card_simulator_service.common.SimResponseCode;
import com.erumpay.card_simulator_service.entity.SimulatorCard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// [be] 하지혁 260603 시뮬레이션 거절 사유 매핑 (신용=한도초과, 체크=잔액부족)
@Component
@RequiredArgsConstructor
public class SimulateRejectReasonResolver {

    private final AesCryptoUtil aesCryptoUtil;

    public SimResponseCode resolve(SimulatorCard card) {
        IinMapping.CardForm form = IinMapping.resolveCardForm(aesCryptoUtil.decrypt(card.getCardNumber()));
        return form == IinMapping.CardForm.CHECK
                ? SimResponseCode.PAYMENT_INSUFFICIENT_BALANCE
                : SimResponseCode.PAYMENT_LIMIT_EXCEEDED;
    }
}
