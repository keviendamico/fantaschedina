package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.dto.BetTemplateForm;
import com.fantacalcio.fantaschedina.dto.LeagueRequest;
import com.fantacalcio.fantaschedina.service.BetTemplateService;
import com.fantacalcio.fantaschedina.service.LeagueService;
import com.fantacalcio.fantaschedina.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/leagues")
@RequiredArgsConstructor
public class AdminLeagueController {

    private final LeagueService leagueService;
    private final BetTemplateService betTemplateService;
    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails user, Model model) {
        log.debug("list: admin {}", user.getUsername());
        model.addAttribute("leagues", leagueService.findAllForAdmin(userService.getUserId(user.getUsername())));
        return "admin/leagues/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("leagueRequest", new LeagueRequest());
        return "admin/leagues/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute LeagueRequest leagueRequest,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetails user,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            log.warn("create: rejected - validation errors for league \"{}\"", leagueRequest.getName());
            return "admin/leagues/form";
        }
        var league = leagueService.create(leagueRequest, userService.getUserId(user.getUsername()));
        log.info("create: league {} \"{}\" created by admin {}", league.getId(), league.getName(), user.getUsername());
        redirectAttributes.addFlashAttribute("success", "Lega creata con successo");
        return "redirect:/admin/leagues/" + league.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails user,
                         Model model) {
        log.debug("detail: league {}", id);
        model.addAttribute("league", leagueService.findByIdForAdmin(id, userService.getUserId(user.getUsername())));
        model.addAttribute("betTemplateForm", betTemplateService.buildForm(id));
        model.addAttribute("jackpot", leagueService.getJackpot(id));
        return "admin/leagues/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails user,
                           Model model) {
        var league = leagueService.findByIdForAdmin(id, userService.getUserId(user.getUsername()));
        var request = new LeagueRequest();
        request.setName(league.getName());
        request.setSeason(league.getSeason());
        request.setMatchdayCost(league.getMatchdayCost());
        request.setTotalMatchdays(league.getTotalMatchdays());
        request.setJackpotStart(league.getJackpotStart());
        request.setBetDeadlineMinutes(league.getBetDeadlineMinutes());
        request.setMaxTeams(league.getMaxTeams());
        model.addAttribute("leagueRequest", request);
        model.addAttribute("leagueId", id);
        return "admin/leagues/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute LeagueRequest leagueRequest,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetails user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            log.warn("update: rejected - validation errors for league {}", id);
            model.addAttribute("leagueId", id);
            return "admin/leagues/form";
        }
        leagueService.findByIdForAdmin(id, userService.getUserId(user.getUsername()));
        leagueService.update(id, leagueRequest);
        log.info("update: league {} updated", id);
        redirectAttributes.addFlashAttribute("success", "Lega aggiornata");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails user,
                           RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(id, userService.getUserId(user.getUsername()));
        leagueService.activate(id);
        log.info("activate: league {} activated", id);
        redirectAttributes.addFlashAttribute("success", "Lega attivata");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id,
                        @AuthenticationPrincipal UserDetails user,
                        RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(id, userService.getUserId(user.getUsername()));
        leagueService.close(id);
        log.info("close: league {} closed", id);
        redirectAttributes.addFlashAttribute("success", "Lega chiusa");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/jackpot/adjust")
    public String adjustJackpot(@PathVariable Long id,
                                @RequestParam int newAmount,
                                @AuthenticationPrincipal UserDetails user,
                                RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(id, userService.getUserId(user.getUsername()));
        leagueService.adjustJackpot(id, newAmount);
        log.info("adjustJackpot: league {} jackpot adjusted to {}", id, newAmount);
        redirectAttributes.addFlashAttribute("success", "Jackpot aggiornato");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/bet-template")
    public String saveBetTemplate(@PathVariable Long id,
                                  @ModelAttribute BetTemplateForm betTemplateForm,
                                  @AuthenticationPrincipal UserDetails user,
                                  RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(id, userService.getUserId(user.getUsername()));
        betTemplateService.save(id, betTemplateForm);
        log.info("saveBetTemplate: league {} bet template saved", id);
        redirectAttributes.addFlashAttribute("success", "Template schedina salvato");
        return "redirect:/admin/leagues/" + id;
    }
}
