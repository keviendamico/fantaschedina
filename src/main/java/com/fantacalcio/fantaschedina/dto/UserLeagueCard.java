package com.fantacalcio.fantaschedina.dto;

import com.fantacalcio.fantaschedina.domain.entity.BetSlip;
import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.LeagueMembership;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.util.AppClock;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserLeagueCard {

    private final League league;
    private final LeagueMembership membership;
    private final Matchday currentMatchday;
    private final BetSlip currentSlip;
    private final Integer jackpot;
    private final LocalDateTime effectiveDeadline;

    public UserLeagueCard(League league, LeagueMembership membership, Matchday currentMatchday,
                          BetSlip currentSlip, Integer jackpot, LocalDateTime effectiveDeadline) {
        this.league = league;
        this.membership = membership;
        this.currentMatchday = currentMatchday;
        this.currentSlip = currentSlip;
        this.jackpot = jackpot;
        this.effectiveDeadline = effectiveDeadline;
    }

    public boolean isMatchdayOpen() {
        return currentMatchday != null && currentMatchday.getStatus() == MatchdayStatus.OPEN;
    }

    public boolean canPlay() {
        if (!isMatchdayOpen()) return false;
        if (currentSlip != null) return false;
        if (effectiveDeadline == null) return true;
        return AppClock.now().isBefore(effectiveDeadline);
    }

    public boolean deadlinePassed() {
        return effectiveDeadline != null && AppClock.now().isAfter(effectiveDeadline);
    }
}