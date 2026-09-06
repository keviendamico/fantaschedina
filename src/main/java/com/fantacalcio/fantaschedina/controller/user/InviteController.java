package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.domain.entity.Invite;
import com.fantacalcio.fantaschedina.service.InviteService;
import com.fantacalcio.fantaschedina.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/invite")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final UserService userService;
    private final RequestCache requestCache;

    @GetMapping("/accept")
    public String acceptInvite(@RequestParam String token,
                               Authentication authentication,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               Model model) {
        Invite invite = inviteService.findValidInvite(token);
        log.debug("acceptInvite: invite {} for league {}", invite.getId(), invite.getLeagueId());

        // New user flow
        if (invite.getUserId() == null) {
            log.debug("acceptInvite: invite {} has no user - redirecting to register", invite.getId());
            return "redirect:/register?token=" + token;
        }

        // Existing user flow - must be authenticated
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            log.debug("acceptInvite: invite {} requires authentication - redirecting to login", invite.getId());
            requestCache.saveRequest(request, response);
            return "redirect:/login";
        }

        Long currentUserId = userService.getUserId(authentication.getName());
        if (!currentUserId.equals(invite.getUserId())) {
            log.warn("acceptInvite: rejected - invite {} targets user {} but current user is {}", invite.getId(), invite.getUserId(), currentUserId);
            model.addAttribute("error", "Questo invito non è destinato a te.");
            return "invite-error";
        }

        model.addAttribute("token", token);
        return "invite-accept";
    }

    @PostMapping("/accept")
    public String confirmAccept(@RequestParam String token,
                                @RequestParam String fantaTeamName,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        Long currentUserId = userService.getUserId(authentication.getName());
        inviteService.acceptForExistingUser(token, currentUserId, fantaTeamName);
        log.info("confirmAccept: user {} joined league via invite, team=\"{}\"", currentUserId, fantaTeamName);
        redirectAttributes.addFlashAttribute("success", "Sei entrato nella lega!");
        return "redirect:/dashboard";
    }

}
