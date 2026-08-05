package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.dto.MatchdayScheduleRequest;
import com.fantacalcio.fantaschedina.service.CalendarService;
import com.fantacalcio.fantaschedina.service.LeagueService;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import com.fantacalcio.fantaschedina.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin/leagues/{leagueId}/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final CalendarService calendarService;
    private final LeagueService leagueService;
    private final MatchdayService matchdayService;
    private final UserService userService;

    private static final String SESSION_KEY = "pendingCsvImport";

    @GetMapping
    public String calendar(@PathVariable Long leagueId,
                           @AuthenticationPrincipal UserDetails user,
                           Model model) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
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
                            @AuthenticationPrincipal UserDetails user,
                            @RequestParam("file") MultipartFile file,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) throws IOException {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Seleziona un file CSV");
            return "redirect:/admin/leagues/" + leagueId + "/calendar";
        }
        byte[] csvBytes = file.getBytes();
        List<Integer> conflicts = calendarService.importCsv(leagueId, csvBytes, false);
        if (!conflicts.isEmpty()) {
            session.setAttribute(SESSION_KEY, csvBytes);
            redirectAttributes.addFlashAttribute("conflictMatchdays", conflicts);
        } else {
            redirectAttributes.addFlashAttribute("success", "Calendario importato con successo");
        }
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/import/confirm")
    public String confirmImport(@PathVariable Long leagueId,
                                @AuthenticationPrincipal UserDetails user,
                                @RequestParam(defaultValue = "false") boolean overwrite,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        byte[] csvBytes = (byte[]) session.getAttribute(SESSION_KEY);
        if (csvBytes == null) {
            redirectAttributes.addFlashAttribute("error", "Sessione scaduta, ricarica il CSV");
            return "redirect:/admin/leagues/" + leagueId + "/calendar";
        }
        session.removeAttribute(SESSION_KEY);
        calendarService.importCsv(leagueId, csvBytes, overwrite);
        String msg = overwrite ? "Calendario importato con sovrascrittura" : "Calendario importato (giornate esistenti saltate)";
        redirectAttributes.addFlashAttribute("success", msg);
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/matchday")
    public String addMatchday(@PathVariable Long leagueId,
                              @AuthenticationPrincipal UserDetails user,
                              @RequestParam Integer number,
                              RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        calendarService.addMatchday(leagueId, number);
        redirectAttributes.addFlashAttribute("success", "Giornata " + number + " creata");
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/{matchdayId}/fixture")
    public String addFixture(@PathVariable Long leagueId,
                             @PathVariable Long matchdayId,
                             @AuthenticationPrincipal UserDetails user,
                             @RequestParam Long homeTeamId,
                             @RequestParam Long awayTeamId,
                             RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        calendarService.addFixture(leagueId, matchdayId, homeTeamId, awayTeamId);
        redirectAttributes.addFlashAttribute("success", "Partita aggiunta");
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/matchday/delete-last")
    public String deleteLastMatchday(@PathVariable Long leagueId,
                                     @AuthenticationPrincipal UserDetails user,
                                     RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        calendarService.deleteLastMatchday(leagueId);
        redirectAttributes.addFlashAttribute("success", "Giornata eliminata");
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

    @PostMapping("/{matchdayId}/schedule")
    public String schedule(@PathVariable Long leagueId,
                           @PathVariable Long matchdayId,
                           @AuthenticationPrincipal UserDetails user,
                           @ModelAttribute MatchdayScheduleRequest request,
                           RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        calendarService.scheduleMatchday(matchdayId, request);
        redirectAttributes.addFlashAttribute("success", "Date aggiornate");
        return "redirect:/admin/leagues/" + leagueId + "/calendar";
    }

}