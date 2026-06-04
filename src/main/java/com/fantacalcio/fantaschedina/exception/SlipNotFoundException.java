package com.fantacalcio.fantaschedina.exception;

import lombok.Getter;

@Getter
public class SlipNotFoundException extends RuntimeException {

    private final Long leagueId;

    public SlipNotFoundException(Long leagueId) {
        super("Schedina non trovata");
        this.leagueId = leagueId;
    }
}
