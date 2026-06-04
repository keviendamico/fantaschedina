package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.domain.entity.Invite;
import com.fantacalcio.fantaschedina.service.InviteService;
import com.fantacalcio.fantaschedina.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/invite")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final UserService userService;

    @GetMapping("/accept")
    public String acceptInvite(@RequestParam String token,
                               Authentication authentication,
                               Model model) {
        Invite invite = inviteService.findValidInvite(token);

        // New user flow
        if (invite.getUserId() == null) {
            return "redirect:/register?token=" + token;
        }

        // Existing user flow — must be authenticated
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }

        Long currentUserId = userService.getUserId(authentication.getName());
        if (!currentUserId.equals(invite.getUserId())) {
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
        redirectAttributes.addFlashAttribute("success", "Sei entrato nella lega!");
        return "redirect:/dashboard";
    }

}
