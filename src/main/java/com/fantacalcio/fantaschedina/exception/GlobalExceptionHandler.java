package com.fantacalcio.fantaschedina.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLeagueMemberException.class)
    public String notLeagueMember() {
        return "redirect:/dashboard";
    }

    @ExceptionHandler(MatchdayNotFoundException.class)
    public String matchdayNotFound(MatchdayNotFoundException e) {
        return "redirect:/leagues/" + e.getLeagueId() + "/matchdays";
    }

    @ExceptionHandler(BetValidationException.class)
    public String betValidation(BetValidationException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/leagues/" + e.getLeagueId() + "/matchdays/" + e.getMatchdayId() + "/bet";
    }

    @ExceptionHandler(RegistrationException.class)
    public String registration(RegistrationException e, Model model) {
        model.addAttribute("token", e.getToken());
        model.addAttribute("error", e.getMessage());
        return "register";
    }

    @ExceptionHandler(SlipNotFoundException.class)
    public String slipNotFound(SlipNotFoundException e) {
        return "redirect:/leagues/" + e.getLeagueId() + "/history";
    }

    @ExceptionHandler(InvalidInviteException.class)
    public String invalidInvite(InvalidInviteException e, Model model) {
        model.addAttribute("error", e.getMessage());
        return "invite-error";
    }

    @ExceptionHandler(InviteActionException.class)
    public String inviteAction(InviteActionException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/dashboard";
    }
}
