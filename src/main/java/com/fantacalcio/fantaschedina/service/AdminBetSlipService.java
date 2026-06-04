package com.fantacalcio.fantaschedina.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.BetPickRequest;
import com.fantacalcio.fantaschedina.dto.BetSlipRequest;
import com.fantacalcio.fantaschedina.dto.PickSlot;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminBetSlipService {

    private final BetSlipRepository betSlipRepository;
    private final BetPickRepository betPickRepository;
    private final BetSlipSnapshotRepository betSlipSnapshotRepository;
    private final MatchdayRepository matchdayRepository;
    private final MatchdayFixtureRepository matchdayFixtureRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final LeagueRepository leagueRepository;
    private final BetTemplateService betTemplateService;
    private final MatchdayService matchdayService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<BetSlip> getSlipsForMatchday(Long matchdayId) {
        return betSlipRepository.findByMatchdayId(matchdayId);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getTeamNames(Long leagueId) {
        return fantaTeamRepository.findByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(FantaTeam::getId, FantaTeam::getName));
    }

    /** Map fantaTeamId → teamName for a list of slips */
    @Transactional(readOnly = true)
    public Map<Long, String> getTeamNamesForSlips(List<BetSlip> slips) {
        return slips.stream()
                .map(s -> fantaTeamRepository.findById(s.getFantaTeamId()).orElseThrow())
                .collect(Collectors.toMap(FantaTeam::getId, FantaTeam::getName));
    }

    @Transactional(readOnly = true)
    public BetSlip getSlip(Long slipId) {
        return betSlipRepository.findById(slipId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<PickSlot> getPickSlots(Long leagueId) {
        return betTemplateService.buildPickSlots(leagueId);
    }

    @Transactional(readOnly = true)
    public List<BetPick> getPicksOrdered(Long betSlipId) {
        return betPickRepository.findByBetSlipId(betSlipId).stream()
                .sorted(Comparator.comparingLong(BetPick::getId))
                .toList();
    }

    @Transactional
    public void modifySlip(Long slipId, Long adminUserId, BetSlipRequest request, String note) {
        BetSlip slip = betSlipRepository.findById(slipId).orElseThrow();
        Matchday matchday = matchdayRepository.findById(slip.getMatchdayId()).orElseThrow();
        League league = leagueRepository.findById(matchday.getLeagueId()).orElseThrow();

        if (matchday.getStatus() != MatchdayStatus.OPEN) {
            throw new IllegalStateException("La giornata non è più aperta: impossibile modificare la schedina.");
        }
        LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new IllegalStateException("La deadline è già passata: impossibile modificare la schedina.");
        }

        // Save snapshot of current picks
        List<BetPick> currentPicks = betPickRepository.findByBetSlipId(slipId);
        try {
            String picksJson = objectMapper.writeValueAsString(currentPicks);
            betSlipSnapshotRepository.save(BetSlipSnapshot.builder()
                    .betSlipId(slipId)
                    .snapshotAt(LocalDateTime.now())
                    .adminUserId(adminUserId)
                    .picksJson(picksJson)
                    .note(note)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Errore nella serializzazione dello snapshot", e);
        }

        // Replace picks
        betPickRepository.deleteAll(currentPicks);
        for (BetPickRequest pick : request.getPicks()) {
            betPickRepository.save(BetPick.builder()
                    .betSlipId(slipId)
                    .matchdayFixtureId(pick.getFixtureId())
                    .outcomeType(pick.getOutcomeType())
                    .pickedOutcome(pick.getPickedOutcome())
                    .build());
        }

        slip.setIsAdminModified(true);
        slip.setAdminModifiedAt(LocalDateTime.now());
        betSlipRepository.save(slip);
    }
}