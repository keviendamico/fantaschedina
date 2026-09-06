package com.fantacalcio.fantaschedina.dto;

import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AdminLeagueCard {

    private final League league;
    private final Matchday currentMatchday;
    private final Integer jackpot;
    private final long slipsSubmitted;
    private final long totalTeams;
    private final LocalDateTime effectiveDeadline;

    public AdminLeagueCard(League league, Matchday currentMatchday, Integer jackpot,
                           long slipsSubmitted, long totalTeams, LocalDateTime effectiveDeadline) {
        this.league = league;
        this.currentMatchday = currentMatchday;
        this.jackpot = jackpot;
        this.slipsSubmitted = slipsSubmitted;
        this.totalTeams = totalTeams;
        this.effectiveDeadline = effectiveDeadline;
    }

    public boolean hasOpenMatchday() {
        return currentMatchday != null && currentMatchday.getStatus() == MatchdayStatus.OPEN;
    }

    /** True when the matchday is waiting for the admin to load results - fully or after a postponed match. */
    public boolean hasClosedMatchday() {
        return currentMatchday != null
                && (currentMatchday.getStatus() == MatchdayStatus.CLOSED
                    || currentMatchday.getStatus() == MatchdayStatus.AWAITING_RECOVERY);
    }
}
