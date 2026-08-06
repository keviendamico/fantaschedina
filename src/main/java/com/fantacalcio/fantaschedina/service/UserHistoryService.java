package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.dto.UserHistoryView;
import com.fantacalcio.fantaschedina.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserHistoryService {

    private final LeagueMembershipRepository leagueMembershipRepository;
    private final MatchdayRepository matchdayRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final BetService betService;
    private final MatchdayService matchdayService;

    public UserHistoryView getHistory(Long leagueId, Long userId) {
        log.debug("getHistory: user {} league {}", userId, leagueId);
        LeagueMembership membership = leagueMembershipRepository
                .findByLeagueIdAndUserId(leagueId, userId).orElseThrow();

        FantaTeam myTeam = matchdayService.getFantaTeam(leagueId, userId).orElse(null);

        List<BetSlip> slips = myTeam != null
                ? betService.findSlipsForTeam(myTeam.getId())
                : List.of();

        Map<Long, Matchday> matchdayMap = slips.isEmpty() ? Map.of() :
                matchdayRepository.findAllById(
                        slips.stream().map(BetSlip::getMatchdayId).collect(Collectors.toSet())
                ).stream().collect(Collectors.toMap(Matchday::getId, m -> m));

        List<BetSlip> sortedSlips = slips.stream()
                .sorted(Comparator.comparing(
                        s -> {
                            Matchday m = matchdayMap.get(s.getMatchdayId());
                            return m != null ? m.getNumber() : 0;
                        },
                        Comparator.reverseOrder()
                ))
                .collect(Collectors.toList());

        List<CreditTransaction> transactions = creditTransactionRepository
                .findByLeagueMembershipIdOrderByCreatedAtDesc(membership.getId());

        return new UserHistoryView(membership, myTeam, sortedSlips, matchdayMap, transactions);
    }
}