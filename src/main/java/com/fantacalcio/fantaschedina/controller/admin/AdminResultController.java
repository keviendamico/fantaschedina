package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.MatchdayResultRequest;
import com.fantacalcio.fantaschedina.service.LeagueService;
import com.fantacalcio.fantaschedina.service.MatchdayProcessingService;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import com.fantacalcio.fantaschedina.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/leagues/{leagueId}/matchdays/{matchdayId}/results")
@RequiredArgsConstructor
public class AdminResultController {

    private final LeagueService leagueService;
    private final MatchdayService matchdayService;
    private final MatchdayProcessingService processingService;
    private final UserService userService;

    @GetMapping
    public String form(@PathVariable Long leagueId,
                       @PathVariable Long matchdayId,
                       @AuthenticationPrincipal UserDetails user,
                       Model model) {
        log.debug("form: league {} matchday {}", leagueId, matchdayId);
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        Matchday matchday = matchdayService.getMatchday(matchdayId, leagueId);

        if (matchday.getStatus() != MatchdayStatus.CLOSED && matchday.getStatus() != MatchdayStatus.AWAITING_RECOVERY) {
            log.warn("form: rejected — matchday {} does not accept results (status={})", matchdayId, matchday.getStatus());
            return "redirect:/admin/leagues/" + leagueId + "/calendar";
        }

        model.addAttribute("league", leagueService.findById(leagueId));
        model.addAttribute("matchday", matchday);
        model.addAttribute("fixtures", matchdayService.getFixtures(matchdayId));
        model.addAttribute("teamNames", matchdayService.getTeamNames(leagueId));
        return "admin/leagues/results";
    }

    @PostMapping
    public String submit(@PathVariable Long leagueId,
                         @PathVariable Long matchdayId,
                         @AuthenticationPrincipal UserDetails user,
                         @ModelAttribute MatchdayResultRequest request,
                         RedirectAttributes redirectAttributes) {
        log.info("submit: league {} matchday {} results submitted by admin {}", leagueId, matchdayId, user.getUsername());
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        matchdayService.getMatchday(matchdayId, leagueId); // ensure the matchday belongs to this league
        Matchday matchday = processingService.loadResults(matchdayId, request);
        log.info("submit: matchday {} results saved, status={}", matchdayId, matchday.getStatus());
        redirectAttributes.addFlashAttribute("success", resultMessage(matchday));
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/recovery")
    public String markRecovery(@PathVariable Long leagueId,
                               @PathVariable Long matchdayId,
                               @AuthenticationPrincipal UserDetails user,
                               RedirectAttributes redirectAttributes) {
        log.info("markRecovery: league {} matchday {} marked as awaiting recovery by admin {}", leagueId, matchdayId, user.getUsername());
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        matchdayService.getMatchday(matchdayId, leagueId); // ensure the matchday belongs to this league
        Matchday matchday = processingService.markAwaitingRecovery(matchdayId);
        redirectAttributes.addFlashAttribute("success",
                "Giornata " + matchday.getNumber() + " in attesa del recupero. Il jackpot è congelato e la "
                + "giornata successiva è ora giocabile. Carica i risultati quando i recuperi saranno disputati.");
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    private String resultMessage(Matchday matchday) {
        // loadResults leaves the matchday RESULTS_LOADED when it stays queued behind an earlier one
        // awaiting recovery, or PROCESSED when it is elaborated right away.
        if (matchday.getStatus() == MatchdayStatus.RESULTS_LOADED) {
            return "Risultati giornata " + matchday.getNumber()
                    + " caricati. Le schedine saranno elaborate al completamento della giornata precedente in attesa del recupero.";
        }
        return "Risultati giornata " + matchday.getNumber() + " caricati e schedine elaborate.";
    }
}