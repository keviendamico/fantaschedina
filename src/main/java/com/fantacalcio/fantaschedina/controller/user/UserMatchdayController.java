package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.service.BetService;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import com.fantacalcio.fantaschedina.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/leagues")
@RequiredArgsConstructor
public class UserMatchdayController {

    private final MatchdayService matchdayService;
    private final BetService betService;
    private final UserService userService;

    @GetMapping("/{leagueId}/matchdays")
    public String list(@PathVariable Long leagueId, Authentication authentication, Model model) {
        Long userId = userService.getUserId(authentication.getName());
        League league = matchdayService.getLeagueForMember(leagueId, userId);

        List<Matchday> matchdays = matchdayService.getMatchdays(leagueId);
        Map<Long, LocalDateTime> deadlines = matchdays.stream()
                .filter(m -> matchdayService.effectiveDeadline(m, league.getBetDeadlineMinutes()) != null)
                .collect(Collectors.toMap(
                        Matchday::getId,
                        m -> matchdayService.effectiveDeadline(m, league.getBetDeadlineMinutes())
                ));

        FantaTeam myTeam = matchdayService.getFantaTeam(leagueId, userId).orElse(null);
        Map<Long, BetSlip> slipsByMatchday = myTeam != null
                ? betService.findSlipsForTeam(myTeam.getId()).stream()
                        .collect(Collectors.toMap(BetSlip::getMatchdayId, s -> s))
                : Map.of();

        model.addAttribute("league", league);
        model.addAttribute("matchdays", matchdays);
        model.addAttribute("deadlines", deadlines);
        model.addAttribute("myTeam", myTeam);
        model.addAttribute("slipsByMatchday", slipsByMatchday);

        return "user/matchday-list";
    }

    @GetMapping("/{leagueId}/matchdays/{matchdayId}")
    public String detail(@PathVariable Long leagueId, @PathVariable Long matchdayId,
                         Authentication authentication, Model model) {
        Long userId = userService.getUserId(authentication.getName());
        League league = matchdayService.getLeagueForMember(leagueId, userId);

        Matchday matchday = matchdayService.getMatchday(matchdayId, leagueId);

        List<MatchdayFixture> fixtures = matchdayService.getFixtures(matchdayId);
        Map<Long, String> teamNames = matchdayService.getTeamNames(leagueId);
        LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());

        FantaTeam myTeam = matchdayService.getFantaTeam(leagueId, userId).orElse(null);
        BetSlip mySlip = myTeam != null ? betService.findSlip(myTeam.getId(), matchdayId) : null;
        List<BetPick> myPicks = mySlip != null ? betService.findPicks(mySlip.getId()) : List.of();
        Map<Long, BetPick> picksByFixture = myPicks.stream()
                .collect(Collectors.toMap(BetPick::getMatchdayFixtureId, p -> p));

        model.addAttribute("league", league);
        model.addAttribute("matchday", matchday);
        model.addAttribute("fixtures", fixtures);
        model.addAttribute("teamNames", teamNames);
        model.addAttribute("deadline", deadline);
        model.addAttribute("myTeam", myTeam);
        model.addAttribute("mySlip", mySlip);
        model.addAttribute("myPicks", myPicks);
        model.addAttribute("picksByFixture", picksByFixture);

        return "user/matchday-detail";
    }
}
