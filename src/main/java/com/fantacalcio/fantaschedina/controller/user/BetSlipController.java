package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.domain.entity.*;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.BetSlipRequest;
import com.fantacalcio.fantaschedina.repository.UserRepository;
import com.fantacalcio.fantaschedina.service.BetService;
import com.fantacalcio.fantaschedina.service.BetTemplateService;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/leagues")
@RequiredArgsConstructor
public class BetSlipController {

    private final BetService betService;
    private final BetTemplateService betTemplateService;
    private final MatchdayService matchdayService;
    private final UserRepository userRepository;

    private Long userId(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow().getId();
    }

    @GetMapping("/{leagueId}/matchdays/{matchdayId}/bet")
    public String form(@PathVariable Long leagueId, @PathVariable Long matchdayId,
                       Authentication authentication, Model model) {
        Long userId = userId(authentication);
        League league;
        try {
            league = matchdayService.getLeagueForMember(leagueId, userId);
        } catch (IllegalArgumentException e) {
            return "redirect:/dashboard";
        }

        Matchday matchday;
        try {
            matchday = matchdayService.getMatchday(matchdayId, leagueId);
        } catch (IllegalArgumentException e) {
            return "redirect:/leagues/" + leagueId + "/matchdays";
        }

        // Only accessible when OPEN and deadline not passed
        LocalDateTime deadline = matchdayService.effectiveDeadline(matchday, league.getBetDeadlineMinutes());
        if (matchday.getStatus() != MatchdayStatus.OPEN ||
                (deadline != null && LocalDateTime.now().isAfter(deadline))) {
            return "redirect:/leagues/" + leagueId + "/matchdays/" + matchdayId;
        }

        FantaTeam myTeam = matchdayService.getFantaTeam(leagueId, userId).orElse(null);

        // If slip already submitted, redirect to detail
        if (myTeam != null) {
            BetSlip existing = betService.findSlip(myTeam.getId(), matchdayId);
            if (existing != null) {
                return "redirect:/leagues/" + leagueId + "/matchdays/" + matchdayId;
            }
        }

        List<MatchdayFixture> fixtures = matchdayService.getFixtures(matchdayId);
        Map<Long, String> teamNames = matchdayService.getTeamNames(leagueId);

        model.addAttribute("league", league);
        model.addAttribute("matchday", matchday);
        model.addAttribute("fixtures", fixtures);
        model.addAttribute("teamNames", teamNames);
        model.addAttribute("templates", betTemplateService.findByLeague(leagueId));
        model.addAttribute("myTeam", myTeam);
        model.addAttribute("deadline", deadline);

        return "user/bet-slip-form";
    }

    @PostMapping("/{leagueId}/matchdays/{matchdayId}/bet")
    public String submit(@PathVariable Long leagueId, @PathVariable Long matchdayId,
                         @ModelAttribute BetSlipRequest request,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        Long userId = userId(authentication);
        try {
            betService.submit(leagueId, matchdayId, userId, request);
            redirectAttributes.addFlashAttribute("success", "Schedina inviata con successo!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/leagues/" + leagueId + "/matchdays/" + matchdayId + "/bet";
        }
        return "redirect:/leagues/" + leagueId + "/matchdays/" + matchdayId;
    }
}