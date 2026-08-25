package com.fantacalcio.fantaschedina.domain.enums;

public enum MatchdayStatus {
    SCHEDULED("In programma"),
    OPEN("Aperta"),
    CLOSED("Chiusa"),
    AWAITING_RECOVERY("In attesa del recupero"),
    RESULTS_LOADED("Risultati caricati"),
    PROCESSED("Elaborata");

    private final String label;

    MatchdayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
