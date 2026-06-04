package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Jackpot;
import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.enums.LeagueStatus;
import com.fantacalcio.fantaschedina.dto.LeagueRequest;
import com.fantacalcio.fantaschedina.repository.JackpotRepository;
import com.fantacalcio.fantaschedina.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final JackpotRepository jackpotRepository;

    @Transactional(readOnly = true)
    public List<League> findAll() {
        return leagueRepository.findAll();
    }

    @Transactional(readOnly = true)
    public League findById(Long id) {
        return leagueRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Lega non trovata: " + id));
    }

    public League create(LeagueRequest request) {
        League league = League.builder()
            .name(request.getName())
            .season(request.getSeason())
            .matchdayCost(request.getMatchdayCost())
            .jackpotStart(request.getJackpotStart())
            .betDeadlineMinutes(request.getBetDeadlineMinutes())
            .maxTeams(request.getMaxTeams())
            .status(LeagueStatus.SETUP)
            .build();
        league = leagueRepository.save(league);

        Jackpot jackpot = Jackpot.builder()
            .leagueId(league.getId())
            .currentAmount(league.getJackpotStart())
            .build();
        jackpotRepository.save(jackpot);

        return league;
    }

    public League update(Long id, LeagueRequest request) {
        League league = findById(id);
        league.setName(request.getName());
        league.setSeason(request.getSeason());
        league.setMatchdayCost(request.getMatchdayCost());
        league.setJackpotStart(request.getJackpotStart());
        league.setBetDeadlineMinutes(request.getBetDeadlineMinutes());
        league.setMaxTeams(request.getMaxTeams());
        return leagueRepository.save(league);
    }

    public void activate(Long id) {
        League league = findById(id);
        if (league.getStatus() != LeagueStatus.SETUP) {
            throw new IllegalStateException("Solo una lega in stato SETUP può essere attivata");
        }
        league.setStatus(LeagueStatus.ACTIVE);
        leagueRepository.save(league);
    }

    public void close(Long id) {
        League league = findById(id);
        if (league.getStatus() == LeagueStatus.CLOSED) {
            throw new IllegalStateException("La lega è già chiusa");
        }
        league.setStatus(LeagueStatus.CLOSED);
        leagueRepository.save(league);
    }

    @Transactional(readOnly = true)
    public Jackpot getJackpot(Long leagueId) {
        return jackpotRepository.findByLeagueId(leagueId)
            .orElseThrow(() -> new IllegalArgumentException("Jackpot non trovato per la lega: " + leagueId));
    }

    public void adjustJackpot(Long leagueId, int newAmount) {
        if (newAmount < 0) throw new IllegalArgumentException("Il jackpot non può essere negativo.");
        Jackpot jackpot = jackpotRepository.findByLeagueId(leagueId)
            .orElseThrow(() -> new IllegalArgumentException("Jackpot non trovato."));
        jackpot.setCurrentAmount(newAmount);
        jackpotRepository.save(jackpot);
    }
}
