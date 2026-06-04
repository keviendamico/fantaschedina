package com.fantacalcio.fantaschedina.exception;

import lombok.Getter;

@Getter
public class MatchdayNotFoundException extends RuntimeException {

    private final Long leagueId;

    public MatchdayNotFoundException(Long matchdayId, Long leagueId) {
        super("Giornata non trovata: " + matchdayId);
        this.leagueId = leagueId;
    }
}
