package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import com.fantacalcio.fantaschedina.dto.MatchdayResultRequest;
import com.fantacalcio.fantaschedina.service.LeagueService;
import com.fantacalcio.fantaschedina.service.MatchdayProcessingService;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/leagues/{leagueId}/matchdays/{matchdayId}/results")
@RequiredArgsConstructor
public class AdminResultController {

    private final LeagueService leagueService;
    private final MatchdayService matchdayService;
    private final MatchdayProcessingService processingService;

    @GetMapping
    public String form(@PathVariable Long leagueId, @PathVariable Long matchdayId, Model model) {
        Matchday matchday = matchdayService.getMatchday(matchdayId, leagueId);

        if (matchday.getStatus() != MatchdayStatus.CLOSED) {
            return "redirect:/admin/leagues/" + leagueId + "/calendar";
        }

        model.addAttribute("league", leagueService.findById(leagueId));
        model.addAttribute("matchday", matchday);
        model.addAttribute("fixtures", matchdayService.getFixtures(matchdayId));
        model.addAttribute("teamNames", matchdayService.getTeamNames(leagueId));
        return "admin/leagues/results";
    }

    @PostMapping
    public String submit(@PathVariable Long leagueId, @PathVariable Long matchdayId,
                         @ModelAttribute MatchdayResultRequest request,
                         RedirectAttributes redirectAttributes) {
        Matchday matchday = processingService.loadResults(matchdayId, request);
        redirectAttributes.addFlashAttribute("success",
                "Risultati giornata " + matchday.getNumber() + " caricati e schedine elaborate.");
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }
}
