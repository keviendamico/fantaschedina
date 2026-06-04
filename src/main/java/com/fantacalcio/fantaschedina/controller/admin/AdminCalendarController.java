package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.dto.MatchdayScheduleRequest;
import com.fantacalcio.fantaschedina.service.CalendarService;
import com.fantacalcio.fantaschedina.service.LeagueService;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/leagues/{leagueId}/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final CalendarService calendarService;
    private final LeagueService leagueService;
    private final MatchdayService matchdayService;

    private static final String SESSION_KEY = "pendingCsvImport";

    @GetMapping
    public String calendar(@PathVariable Long leagueId, Model model) {
        var matchdays = calendarService.findMatchdaysByLeague(leagueId);
        int nextNumber = matchdays.isEmpty() ? 1 : matchdays.getLast().getNumber() + 1;

        model.addAttribute("league", leagueService.findById(leagueId));
        model.addAttribute("matchdays", matchdays);
        model.addAttribute("fixturesByMatchday", calendarService.findFixturesGroupedByMatchday(matchdays));
        model.addAttribute("teamNames", matchdayService.getTeamNames(leagueId));
        model.addAttribute("nextMatchdayNumber", nextNumber);
        return "admin/leagues/calendar";
    }

    @PostMapping("/import")
    public String importCsv(@PathVariable Long leagueId,
                            @RequestParam("file") MultipartFile file,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Seleziona un file CSV");
            return "redirect:/admin/leagues/" + leagueId + "/calendar";
        }
        try {
            byte[] csvBytes = file.getBytes();
            List<Integer> conflicts = calendarService.importCsv(leagueId, csvBytes, false);
            if (!conflicts.isEmpty()) {
                session.setAttribute(SESSION_KEY, csvBytes);
                redirectAttributes.addFlashAttribute("conflictMatchdays", conflicts);
            } else {
                redirectAttributes.addFlashAttribute("success", "Calendario importato con successo");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/import/confirm")
    public String confirmImport(@PathVariable Long leagueId,
                                @RequestParam(defaultValue = "false") boolean overwrite,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        byte[] csvBytes = (byte[]) session.getAttribute(SESSION_KEY);
        if (csvBytes == null) {
            redirectAttributes.addFlashAttribute("error", "Sessione scaduta, ricarica il CSV");
            return "redirect:/admin/leagues/" + leagueId + "/calendar";
        }
        try {
            calendarService.importCsv(leagueId, csvBytes, overwrite);
            String msg = overwrite ? "Calendario importato con sovrascrittura" : "Calendario importato (giornate esistenti saltate)";
            redirectAttributes.addFlashAttribute("success", msg);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } finally {
            session.removeAttribute(SESSION_KEY);
        }
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/matchday")
    public String addMatchday(@PathVariable Long leagueId,
                              @RequestParam Integer number,
                              RedirectAttributes redirectAttributes) {
        try {
            calendarService.addMatchday(leagueId, number);
            redirectAttributes.addFlashAttribute("success", "Giornata " + number + " creata");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/{matchdayId}/fixture")
    public String addFixture(@PathVariable Long leagueId,
                             @PathVariable Long matchdayId,
                             @RequestParam Long homeTeamId,
                             @RequestParam Long awayTeamId,
                             RedirectAttributes redirectAttributes) {
        try {
            calendarService.addFixture(leagueId, matchdayId, homeTeamId, awayTeamId);
            redirectAttributes.addFlashAttribute("success", "Partita aggiunta");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/matchday/delete-last")
    public String deleteLastMatchday(@PathVariable Long leagueId,
                                     RedirectAttributes redirectAttributes) {
        try {
            calendarService.deleteLastMatchday(leagueId);
            redirectAttributes.addFlashAttribute("success", "Giornata eliminata");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/{matchdayId}/schedule")
    public String schedule(@PathVariable Long leagueId,
                           @PathVariable Long matchdayId,
                           @ModelAttribute MatchdayScheduleRequest request,
                           RedirectAttributes redirectAttributes) {
        try {
            calendarService.scheduleMatchday(matchdayId, request);
            redirectAttributes.addFlashAttribute("success", "Date aggiornate");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }
}
