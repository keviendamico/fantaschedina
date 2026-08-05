package com.fantacalcio.fantaschedina.domain.enums;

public enum AdminLogType {
    JACKPOT_ADJUST("Modifica jackpot"),
    CREDIT_ADJUST("Rettifica crediti");

    private final String label;

    AdminLogType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}