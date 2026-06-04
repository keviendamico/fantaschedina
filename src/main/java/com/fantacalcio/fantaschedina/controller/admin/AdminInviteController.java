package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.dto.InviteRequest;
import com.fantacalcio.fantaschedina.service.InviteService;
import com.fantacalcio.fantaschedina.service.LeagueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteService inviteService;
    private final LeagueService leagueService;

    @GetMapping
    public String listInvites(Model model) {
        populateLeagueModel(model);
        model.addAttribute("invites", inviteService.findAll());
        model.addAttribute("inviteRequest", new InviteRequest());
        return "admin/invites";
    }

    @PostMapping("/send")
    public String sendInvite(@Valid @ModelAttribute InviteRequest inviteRequest,
                             BindingResult result,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateLeagueModel(model);
            model.addAttribute("invites", inviteService.findAll());
            return "admin/invites";
        }
        inviteService.createInvite(inviteRequest.getLeagueId(), inviteRequest.getEmail());
        redirectAttributes.addFlashAttribute("success", "Invito inviato a " + inviteRequest.getEmail());
        return "redirect:/admin/invites";
    }

    @PostMapping("/{id}/revoke")
    public String revokeInvite(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        inviteService.revokeInvite(id);
        redirectAttributes.addFlashAttribute("success", "Invito revocato");
        return "redirect:/admin/invites";
    }

    private void populateLeagueModel(Model model) {
        List<League> leagues = leagueService.findAll();
        Map<Long, String> leagueNames = leagues.stream()
            .collect(Collectors.toMap(League::getId, l -> l.getName() + " (" + l.getSeason() + ")"));
        model.addAttribute("leagues", leagues);
        model.addAttribute("leagueNames", leagueNames);
    }
}
