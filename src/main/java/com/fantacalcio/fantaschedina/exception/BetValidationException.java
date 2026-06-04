package com.fantacalcio.fantaschedina.exception;

import lombok.Getter;

@Getter
public class BetValidationException extends RuntimeException {

    private final Long leagueId;
    private final Long matchdayId;

    public BetValidationException(String message, Long leagueId, Long matchdayId) {
        super(message);
        this.leagueId = leagueId;
        this.matchdayId = matchdayId;
    }
}
