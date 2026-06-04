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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/leagues")
@RequiredArgsConstructor
public class BetSlipDetailController {

    private final BetService betService;
    private final MatchdayService matchdayService;
    private final UserService userService;

    @GetMapping("/{leagueId}/slips/{slipId}")
    public String detail(@PathVariable Long leagueId, @PathVariable Long slipId,
                         Authentication authentication, Model model) {
        Long userId = userService.getUserId(authentication.getName());

        League league = matchdayService.getLeagueForMember(leagueId, userId);

        BetSlip slip = betService.findSlipForUser(slipId, leagueId, userId);

        Matchday matchday = matchdayService.getMatchday(slip.getMatchdayId(), leagueId);
        List<BetPick> picks = betService.findPicks(slipId);
        List<MatchdayFixture> fixtures = matchdayService.getFixtures(slip.getMatchdayId());
        Map<Long, String> teamNames = matchdayService.getTeamNames(leagueId);
        Map<Long, BetPick> picksByFixture = picks.stream()
                .collect(Collectors.toMap(BetPick::getMatchdayFixtureId, p -> p));
        FantaTeam myTeam = matchdayService.getFantaTeam(leagueId, userId).orElse(null);

        model.addAttribute("league", league);
        model.addAttribute("matchday", matchday);
        model.addAttribute("slip", slip);
        model.addAttribute("picks", picks);
        model.addAttribute("fixtures", fixtures);
        model.addAttribute("teamNames", teamNames);
        model.addAttribute("picksByFixture", picksByFixture);
        model.addAttribute("myTeam", myTeam);

        return "user/slip-detail";
    }
}