package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.dto.InviteRequest;
import com.fantacalcio.fantaschedina.service.InviteService;
import com.fantacalcio.fantaschedina.service.LeagueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteService inviteService;
    private final LeagueService leagueService;

    @GetMapping
    public String listInvites(Model model) {
        log.debug("listInvites");
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
            log.warn("sendInvite: rejected — validation errors for league {} email {}", inviteRequest.getLeagueId(), inviteRequest.getEmail());
            populateLeagueModel(model);
            model.addAttribute("invites", inviteService.findAll());
            return "admin/invites";
        }
        inviteService.createInvite(inviteRequest.getLeagueId(), inviteRequest.getEmail());
        log.info("sendInvite: invite sent to {} for league {}", inviteRequest.getEmail(), inviteRequest.getLeagueId());
        redirectAttributes.addFlashAttribute("success", "Invito inviato a " + inviteRequest.getEmail());
        return "redirect:/admin/invites";
    }

    @PostMapping("/{id}/revoke")
    public String revokeInvite(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        inviteService.revokeInvite(id);
        log.info("revokeInvite: invite {} revoked", id);
        redirectAttributes.addFlashAttribute("success", "Invito revocato");
        return "redirect:/admin/invites";
    }

    private void populateLeagueModel(Model model) {
        model.addAttribute("leagues", leagueService.findAll());
        model.addAttribute("leagueNames", leagueService.getLeagueNames());
    }
}
