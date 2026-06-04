package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.dto.BetTemplateForm;
import com.fantacalcio.fantaschedina.dto.LeagueRequest;
import com.fantacalcio.fantaschedina.service.BetTemplateService;
import com.fantacalcio.fantaschedina.service.LeagueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/leagues")
@RequiredArgsConstructor
public class AdminLeagueController {

    private final LeagueService leagueService;
    private final BetTemplateService betTemplateService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("leagues", leagueService.findAll());
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
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/leagues/form";
        }
        var league = leagueService.create(leagueRequest);
        redirectAttributes.addFlashAttribute("success", "Lega creata con successo");
        return "redirect:/admin/leagues/" + league.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("league", leagueService.findById(id));
        model.addAttribute("betTemplateForm", betTemplateService.buildForm(id));
        model.addAttribute("jackpot", leagueService.getJackpot(id));
        return "admin/leagues/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var league = leagueService.findById(id);
        var request = new LeagueRequest();
        request.setName(league.getName());
        request.setSeason(league.getSeason());
        request.setMatchdayCost(league.getMatchdayCost());
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
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("leagueId", id);
            return "admin/leagues/form";
        }
        leagueService.update(id, leagueRequest);
        redirectAttributes.addFlashAttribute("success", "Lega aggiornata");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        leagueService.activate(id);
        redirectAttributes.addFlashAttribute("success", "Lega attivata");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        leagueService.close(id);
        redirectAttributes.addFlashAttribute("success", "Lega chiusa");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/jackpot/adjust")
    public String adjustJackpot(@PathVariable Long id,
                                @RequestParam int newAmount,
                                RedirectAttributes redirectAttributes) {
        leagueService.adjustJackpot(id, newAmount);
        redirectAttributes.addFlashAttribute("success", "Jackpot aggiornato");
        return "redirect:/admin/leagues/" + id;
    }

    @PostMapping("/{id}/bet-template")
    public String saveBetTemplate(@PathVariable Long id,
                                  @ModelAttribute BetTemplateForm betTemplateForm,
                                  RedirectAttributes redirectAttributes) {
        betTemplateService.save(id, betTemplateForm);
        redirectAttributes.addFlashAttribute("success", "Template schedina salvato");
        return "redirect:/admin/leagues/" + id;
    }
}
