package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Jackpot;
import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.LeagueAuditLog;
import com.fantacalcio.fantaschedina.domain.enums.AdminLogType;
import com.fantacalcio.fantaschedina.domain.enums.LeagueStatus;
import com.fantacalcio.fantaschedina.dto.LeagueRequest;
import com.fantacalcio.fantaschedina.repository.JackpotRepository;
import com.fantacalcio.fantaschedina.repository.LeagueAuditLogRepository;
import com.fantacalcio.fantaschedina.repository.LeagueRepository;
import com.fantacalcio.fantaschedina.repository.MatchdayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final JackpotRepository jackpotRepository;
    private final LeagueAuditLogRepository leagueAuditLogRepository;
    private final MatchdayRepository matchdayRepository;

    @Transactional(readOnly = true)
    public List<League> findAll() {
        return leagueRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<League> findAllForAdmin(Long adminUserId) {
        return leagueRepository.findByCreatedByUserId(adminUserId);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getLeagueNames() {
        return findAll().stream()
            .collect(Collectors.toMap(League::getId, l -> l.getName() + " (" + l.getSeason() + ")"));
    }

    @Transactional(readOnly = true)
    public League findById(Long id) {
        return leagueRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("findById: rejected - league {} not found", id);
                return new IllegalArgumentException("Lega non trovata: " + id);
            });
    }

    @Transactional(readOnly = true)
    public League findByIdForAdmin(Long id, Long adminUserId) {
        League league = findById(id);
        if (!league.getCreatedByUserId().equals(adminUserId)) {
            log.warn("findByIdForAdmin: rejected - league {} not owned by admin {}", id, adminUserId);
            throw new IllegalArgumentException("Non hai accesso a questa lega.");
        }
        return league;
    }

    public League create(LeagueRequest request, Long adminUserId) {
        log.debug("create: admin {} name \"{}\"", adminUserId, request.getName());
        League league = League.builder()
            .name(request.getName())
            .season(request.getSeason())
            .matchdayCost(request.getMatchdayCost())
            .totalMatchdays(request.getTotalMatchdays())
            .jackpotStart(request.getJackpotStart())
            .betDeadlineMinutes(request.getBetDeadlineMinutes())
            .maxTeams(request.getMaxTeams())
            .status(LeagueStatus.SETUP)
            .createdByUserId(adminUserId)
            .build();
        league = leagueRepository.save(league);

        Jackpot jackpot = Jackpot.builder()
            .leagueId(league.getId())
            .currentAmount(league.getJackpotStart())
            .build();
        jackpotRepository.save(jackpot);

        log.info("create: league {} \"{}\" created by admin {}", league.getId(), league.getName(), adminUserId);
        return league;
    }

    public League update(Long id, LeagueRequest request) {
        log.debug("update: league {}", id);
        League league = findById(id);
        league.setName(request.getName());
        league.setSeason(request.getSeason());
        league.setMatchdayCost(request.getMatchdayCost());
        league.setTotalMatchdays(request.getTotalMatchdays());
        league.setJackpotStart(request.getJackpotStart());
        league.setBetDeadlineMinutes(request.getBetDeadlineMinutes());
        league.setMaxTeams(request.getMaxTeams());
        League saved = leagueRepository.save(league);
        log.info("update: league {} updated", id);
        return saved;
    }

    public void activate(Long id) {
        log.debug("activate: league {}", id);
        League league = findById(id);
        if (league.getStatus() != LeagueStatus.SETUP) {
            log.warn("activate: rejected - league {} is not SETUP (status={})", id, league.getStatus());
            throw new IllegalStateException("Solo una lega in stato SETUP può essere attivata");
        }
        if (matchdayRepository.findByLeagueIdOrderByNumberAsc(id).isEmpty()) {
            log.warn("activate: rejected - league {} has no calendar loaded", id);
            throw new IllegalStateException("Non è possibile attivare la lega: carica prima il calendario.");
        }
        league.setStatus(LeagueStatus.ACTIVE);
        leagueRepository.save(league);
        log.info("activate: league {} SETUP -> ACTIVE", id);
    }

    public void close(Long id) {
        log.debug("close: league {}", id);
        League league = findById(id);
        if (league.getStatus() == LeagueStatus.CLOSED) {
            log.warn("close: rejected - league {} already CLOSED", id);
            throw new IllegalStateException("La lega è già chiusa");
        }
        LeagueStatus previousStatus = league.getStatus();
        league.setStatus(LeagueStatus.CLOSED);
        leagueRepository.save(league);
        log.info("close: league {} {} -> CLOSED", id, previousStatus);
    }

    @Transactional(readOnly = true)
    public Jackpot getJackpot(Long leagueId) {
        return jackpotRepository.findByLeagueId(leagueId)
            .orElseThrow(() -> {
                log.warn("getJackpot: rejected - no jackpot for league {}", leagueId);
                return new IllegalArgumentException("Jackpot non trovato per la lega: " + leagueId);
            });
    }

    public void adjustJackpot(Long leagueId, int newAmount) {
        log.debug("adjustJackpot: league {} newAmount {}", leagueId, newAmount);
        if (newAmount < 0) {
            log.warn("adjustJackpot: rejected - negative amount {} for league {}", newAmount, leagueId);
            throw new IllegalArgumentException("Il jackpot non può essere negativo.");
        }
        Jackpot jackpot = jackpotRepository.findByLeagueId(leagueId)
            .orElseThrow(() -> {
                log.warn("adjustJackpot: rejected - no jackpot for league {}", leagueId);
                return new IllegalArgumentException("Jackpot non trovato.");
            });
        int previousAmount = jackpot.getCurrentAmount();
        jackpot.setCurrentAmount(newAmount);
        jackpotRepository.save(jackpot);

        leagueAuditLogRepository.save(LeagueAuditLog.builder()
            .leagueId(leagueId)
            .type(AdminLogType.JACKPOT_ADJUST)
            .amount(newAmount)
            .createdAt(LocalDateTime.now())
            .build());

        log.info("adjustJackpot: league {} jackpot {} -> {}", leagueId, previousAmount, newAmount);
    }
}
