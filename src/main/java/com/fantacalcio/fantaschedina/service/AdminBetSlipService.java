package com.fantacalcio.fantaschedina.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.BetPickRequest;
import com.fantacalcio.fantaschedina.dto.BetSlipRequest;
import com.fantacalcio.fantaschedina.dto.PickSlot;
import com.fantacalcio.fantaschedina.repository.*;
import com.fantacalcio.fantaschedina.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBetSlipService {

    private final BetSlipRepository betSlipRepository;
    private final BetPickRepository betPickRepository;
    private final BetSlipSnapshotRepository betSlipSnapshotRepository;
    private final MatchdayRepository matchdayRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final LeagueRepository leagueRepository;
    private final BetTemplateService betTemplateService;
    private final MatchdayService matchdayService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<BetSlip> getSlipsForMatchday(Long matchdayId) {
        log.debug("getSlipsForMatchday: matchday {}", matchdayId);
        return betSlipRepository.findByMatchdayId(matchdayId);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getTeamNames(Long leagueId) {
        log.debug("getTeamNames: league {}", leagueId);
        return fantaTeamRepository.findByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(FantaTeam::getId, FantaTeam::getName));
    }

    /** Map fantaTeamId → teamName for a list of slips */
    @Transactional(readOnly = true)
    public Map<Long, String> getTeamNamesForSlips(List<BetSlip> slips) {
        log.debug("getTeamNamesForSlips: {} slip(s)", slips.size());
        return slips.stream()
                .map(s -> fantaTeamRepository.findById(s.getFantaTeamId()).orElseThrow())
                .collect(Collectors.toMap(FantaTeam::getId, FantaTeam::getName));
    }

    @Transactional(readOnly = true)
    public BetSlip getSlip(Long slipId) {
        log.debug("getSlip: slip {}", slipId);
        return betSlipRepository.findById(slipId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<PickSlot> getPickSlots(Long leagueId) {
        log.debug("getPickSlots: league {}", leagueId);
        return betTemplateService.buildPickSlots(leagueId);
    }

    @Transactional(readOnly = true)
    public List<BetPick> getPicksOrdered(Long betSlipId) {
        log.debug("getPicksOrdered: slip {}", betSlipId);
        return betPickRepository.findByBetSlipId(betSlipId).stream()
                .sorted(Comparator.comparingLong(BetPick::getId))
                .toList();
    }

    /** Map matchdayFixtureId → pick for a slip, to render picks alongside their fixture. */
    @Transactional(readOnly = true)
    public Map<Long, BetPick> getPicksByFixture(Long betSlipId) {
        log.debug("getPicksByFixture: slip {}", betSlipId);
        return betPickRepository.findByBetSlipId(betSlipId).stream()
                .collect(Collectors.toMap(BetPick::getMatchdayFixtureId, p -> p, (a, b) -> a));
    }

    @Transactional
    public void modifySlip(Long slipId, Long adminUserId, BetSlipRequest request, String note) {
        log.debug("modifySlip: request received for slip {} by admin {}", slipId, adminUserId);

        BetSlip slip = betSlipRepository.findById(slipId).orElseThrow();
        Matchday matchday = matchdayRepository.findById(slip.getMatchdayId()).orElseThrow();
        League league = leagueRepository.findById(matchday.getLeagueId()).orElseThrow();

        if (matchday.getStatus() != MatchdayStatus.OPEN) {
            log.warn("modifySlip: rejected — matchday {} is not OPEN (status={})", matchday.getId(), matchday.getStatus());
            throw new IllegalStateException("La giornata non è più aperta: impossibile modificare la schedina.");
        }
        LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
        if (deadline != null && AppClock.now().isAfter(deadline)) {
            log.warn("modifySlip: rejected — deadline {} already passed for matchday {}", deadline, matchday.getId());
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
            log.debug("modifySlip: snapshot saved for slip {} ({} original picks)", slipId, currentPicks.size());
        } catch (Exception e) {
            log.error("modifySlip: failed to serialize snapshot for slip {}", slipId, e);
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
        log.info("modifySlip: slip {} modified by admin {}, note=\"{}\"", slipId, adminUserId, note);
    }
}
