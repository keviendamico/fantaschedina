package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.entity.MatchdayFixture;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.MatchdayScheduleRequest;
import com.fantacalcio.fantaschedina.repository.FantaTeamRepository;
import com.fantacalcio.fantaschedina.repository.LeagueRepository;
import com.fantacalcio.fantaschedina.repository.MatchdayFixtureRepository;
import com.fantacalcio.fantaschedina.repository.MatchdayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CalendarService {

    private final LeagueRepository leagueRepository;
    private final MatchdayRepository matchdayRepository;
    private final MatchdayFixtureRepository matchdayFixtureRepository;
    private final FantaTeamRepository fantaTeamRepository;
    private final MatchdayOpeningService matchdayOpeningService;

    /**
     * Parses and validates the CSV, then imports.
     * If overwrite=false and conflicting matchdays exist, returns their numbers without importing.
     * If overwrite=true, deletes existing fixtures for conflicting matchdays before importing.
     *
     * @return list of conflicting matchday numbers; empty = import completed successfully
     */
    public List<Integer> importCsv(Long leagueId, byte[] csvBytes, boolean overwrite) {
        log.debug("importCsv: league {} overwrite={}", leagueId, overwrite);
        leagueRepository.findById(leagueId)
            .orElseThrow(() -> {
                log.warn("importCsv: rejected — league {} not found", leagueId);
                return new IllegalArgumentException("Lega non trovata");
            });

        Map<String, Long> teamNameToId = fantaTeamRepository.findByLeagueId(leagueId).stream()
            .collect(Collectors.toMap(t -> t.getName(), t -> t.getId()));

        Map<Integer, List<String[]>> grouped = parseCsv(csvBytes, teamNameToId);

        List<Integer> conflicts = new ArrayList<>();
        for (Integer number : grouped.keySet()) {
            matchdayRepository.findByLeagueIdAndNumber(leagueId, number).ifPresent(md -> {
                if (matchdayFixtureRepository.countByMatchdayId(md.getId()) > 0) {
                    conflicts.add(number);
                }
            });
        }

        if (!conflicts.isEmpty() && !overwrite) {
            log.debug("importCsv: league {} has {} conflicting matchday(s)", leagueId, conflicts.size());
            return conflicts;
        }

        if (overwrite) {
            for (Integer number : conflicts) {
                matchdayRepository.findByLeagueIdAndNumber(leagueId, number).ifPresent(md ->
                    matchdayFixtureRepository.deleteByMatchdayId(md.getId()));
            }
        }

        persist(leagueId, grouped, teamNameToId);
        log.info("importCsv: league {} imported {} matchday(s)", leagueId, grouped.size());
        return Collections.emptyList();
    }

    private Map<Integer, List<String[]>> parseCsv(byte[] csvBytes, Map<String, Long> teamNameToId) {
        Map<Integer, List<String[]>> grouped = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null || !header.trim().equals("matchday_number,home_team,away_team")) {
                log.warn("parseCsv: rejected — invalid CSV header: \"{}\"", header);
                throw new IllegalArgumentException(
                    "Intestazione CSV non valida. Attesa: matchday_number,home_team,away_team");
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length != 3) {
                    errors.add("Riga " + lineNum + ": formato non valido (attese 3 colonne)");
                    continue;
                }
                try {
                    int number = Integer.parseInt(parts[0].trim());
                    grouped.computeIfAbsent(number, k -> new ArrayList<>()).add(parts);
                } catch (NumberFormatException e) {
                    errors.add("Riga " + lineNum + ": numero giornata non valido");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("parseCsv: rejected — error reading CSV file", e);
            throw new IllegalArgumentException("Errore nella lettura del file CSV");
        }

        if (!errors.isEmpty()) {
            log.warn("parseCsv: rejected — {} row error(s): {}", errors.size(), errors);
            throw new IllegalArgumentException("Errori nel CSV: " + String.join("; ", errors));
        }

        Set<String> unknown = new LinkedHashSet<>();
        for (List<String[]> rows : grouped.values()) {
            for (String[] row : rows) {
                if (!teamNameToId.containsKey(row[1].trim())) unknown.add(row[1].trim());
                if (!teamNameToId.containsKey(row[2].trim())) unknown.add(row[2].trim());
            }
        }
        if (!unknown.isEmpty()) {
            log.warn("parseCsv: rejected — unknown team(s): {}", unknown);
            throw new IllegalArgumentException(
                "Squadre non trovate nella lega: " + String.join(", ", unknown));
        }

        return grouped;
    }

    private void persist(Long leagueId, Map<Integer, List<String[]>> grouped, Map<String, Long> teamNameToId) {
        for (Map.Entry<Integer, List<String[]>> entry : grouped.entrySet()) {
            Integer number = entry.getKey();
            Matchday matchday = matchdayRepository.findByLeagueIdAndNumber(leagueId, number)
                .orElseGet(() -> matchdayRepository.save(Matchday.builder()
                    .leagueId(leagueId)
                    .number(number)
                    .status(MatchdayStatus.SCHEDULED)
                    .build()));

            for (String[] row : entry.getValue()) {
                matchdayFixtureRepository.save(MatchdayFixture.builder()
                    .matchdayId(matchday.getId())
                    .homeFantaTeamId(teamNameToId.get(row[1].trim()))
                    .awayFantaTeamId(teamNameToId.get(row[2].trim()))
                    .build());
            }
        }
    }

    public void scheduleMatchday(Long matchdayId, MatchdayScheduleRequest request) {
        log.debug("scheduleMatchday: matchday {} startAt={}", matchdayId, request.getStartAt());
        Matchday matchday = matchdayRepository.findById(matchdayId)
            .orElseThrow(() -> {
                log.warn("scheduleMatchday: rejected — matchday {} not found", matchdayId);
                return new IllegalArgumentException("Giornata non trovata");
            });

        if (matchday.getStatus() != MatchdayStatus.SCHEDULED) {
            log.warn("scheduleMatchday: rejected — matchday {} is not SCHEDULED (status={})", matchdayId, matchday.getStatus());
            throw new IllegalStateException(
                "Le date possono essere modificate solo su giornate in stato SCHEDULED");
        }

        matchday.setStartAt(request.getStartAt());
        matchdayRepository.save(matchday);
        log.info("scheduleMatchday: matchday {} startAt set to {}", matchdayId, request.getStartAt());

        // Trigger 1: open immediately if previous is PROCESSED (or absent)
        matchdayOpeningService.tryOpen(matchday);
    }

    public Matchday addMatchday(Long leagueId, Integer number) {
        log.debug("addMatchday: league {} number {}", leagueId, number);
        leagueRepository.findById(leagueId)
            .orElseThrow(() -> {
                log.warn("addMatchday: rejected — league {} not found", leagueId);
                return new IllegalArgumentException("Lega non trovata");
            });
        if (matchdayRepository.findByLeagueIdAndNumber(leagueId, number).isPresent()) {
            log.warn("addMatchday: rejected — matchday {} already exists for league {}", number, leagueId);
            throw new IllegalArgumentException("Giornata " + number + " già esistente");
        }
        Matchday matchday = matchdayRepository.save(Matchday.builder()
            .leagueId(leagueId)
            .number(number)
            .status(MatchdayStatus.SCHEDULED)
            .build());
        log.info("addMatchday: league {} matchday {} ({}) created", leagueId, matchday.getId(), number);
        return matchday;
    }

    public void addFixture(Long leagueId, Long matchdayId, Long homeTeamId, Long awayTeamId) {
        log.debug("addFixture: league {} matchday {} home {} away {}", leagueId, matchdayId, homeTeamId, awayTeamId);
        var league = leagueRepository.findById(leagueId)
            .orElseThrow(() -> {
                log.warn("addFixture: rejected — league {} not found", leagueId);
                return new IllegalArgumentException("Lega non trovata");
            });
        Matchday matchday = matchdayRepository.findById(matchdayId)
            .orElseThrow(() -> {
                log.warn("addFixture: rejected — matchday {} not found", matchdayId);
                return new IllegalArgumentException("Giornata non trovata");
            });
        if (!matchday.getLeagueId().equals(leagueId)) {
            log.warn("addFixture: rejected — matchday {} does not belong to league {}", matchdayId, leagueId);
            throw new IllegalArgumentException("La giornata non appartiene a questa lega");
        }
        if (matchday.getStatus() != MatchdayStatus.SCHEDULED) {
            log.warn("addFixture: rejected — matchday {} is not SCHEDULED (status={})", matchdayId, matchday.getStatus());
            throw new IllegalStateException("Impossibile aggiungere partite a una giornata non in stato SCHEDULED");
        }
        if (homeTeamId.equals(awayTeamId)) {
            log.warn("addFixture: rejected — home and away team are the same ({})", homeTeamId);
            throw new IllegalArgumentException("La squadra di casa e quella in trasferta devono essere diverse");
        }
        if (league.getMaxTeams() != null) {
            int maxFixtures = league.getMaxTeams() / 2;
            long fixtureCount = matchdayFixtureRepository.countByMatchdayId(matchdayId);
            if (fixtureCount >= maxFixtures) {
                log.warn("addFixture: rejected — matchday {} reached max fixtures ({}/{})", matchdayId, fixtureCount, maxFixtures);
                throw new IllegalStateException(
                    "Numero massimo di partite per giornata raggiunto (" + maxFixtures + "/" + maxFixtures + ")");
            }
        }
        fantaTeamRepository.findById(homeTeamId)
            .filter(t -> t.getLeagueId().equals(leagueId))
            .orElseThrow(() -> {
                log.warn("addFixture: rejected — home team {} not valid for league {}", homeTeamId, leagueId);
                return new IllegalArgumentException("Squadra di casa non valida");
            });
        fantaTeamRepository.findById(awayTeamId)
            .filter(t -> t.getLeagueId().equals(leagueId))
            .orElseThrow(() -> {
                log.warn("addFixture: rejected — away team {} not valid for league {}", awayTeamId, leagueId);
                return new IllegalArgumentException("Squadra in trasferta non valida");
            });

        matchdayFixtureRepository.save(MatchdayFixture.builder()
            .matchdayId(matchdayId)
            .homeFantaTeamId(homeTeamId)
            .awayFantaTeamId(awayTeamId)
            .build());
        log.info("addFixture: matchday {} fixture {} vs {} added", matchdayId, homeTeamId, awayTeamId);
    }

    public void deleteLastMatchday(Long leagueId) {
        log.debug("deleteLastMatchday: league {}", leagueId);
        List<Matchday> matchdays = matchdayRepository.findByLeagueIdOrderByNumberAsc(leagueId);
        if (matchdays.isEmpty()) {
            log.warn("deleteLastMatchday: rejected — league {} has no matchdays", leagueId);
            throw new IllegalArgumentException("Nessuna giornata da eliminare");
        }
        Matchday last = matchdays.getLast();
        if (last.getStatus() != MatchdayStatus.SCHEDULED) {
            log.warn("deleteLastMatchday: rejected — matchday {} is not SCHEDULED (status={})", last.getId(), last.getStatus());
            throw new IllegalStateException("Solo l'ultima giornata in stato SCHEDULED può essere eliminata");
        }
        matchdayFixtureRepository.deleteByMatchdayId(last.getId());
        matchdayRepository.delete(last);
        log.info("deleteLastMatchday: league {} matchday {} ({}) deleted", leagueId, last.getId(), last.getNumber());
    }

    @Transactional(readOnly = true)
    public List<Matchday> findMatchdaysByLeague(Long leagueId) {
        return matchdayRepository.findByLeagueIdOrderByNumberAsc(leagueId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MatchdayFixture>> findFixturesGroupedByMatchday(List<Matchday> matchdays) {
        Map<Long, List<MatchdayFixture>> result = new LinkedHashMap<>();
        for (Matchday md : matchdays) {
            result.put(md.getId(), matchdayFixtureRepository.findByMatchdayId(md.getId()));
        }
        return result;
    }
}
