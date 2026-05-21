package com.erumpay.card_simulator_service.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CardCompanyConverter implements AttributeConverter<CardCompany, String> {

    @Override
    public String convertToDatabaseColumn(CardCompany attribute) {
        return attribute == null ? null : attribute.getDisplayName();
    }

    @Override
    public CardCompany convertToEntityAttribute(String dbData) {
        return CardCompany.fromDisplayName(dbData);
    }
}
