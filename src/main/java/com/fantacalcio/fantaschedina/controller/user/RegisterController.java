package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.dto.RegisterRequest;
import com.fantacalcio.fantaschedina.exception.InvalidInviteException;
import com.fantacalcio.fantaschedina.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final InviteService inviteService;

    @GetMapping("/register")
    public String showRegisterForm(@RequestParam String token, Model model) {
        inviteService.findValidInvite(token);
        model.addAttribute("token", token);
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String token,
                           @Valid @ModelAttribute RegisterRequest registerRequest,
                           BindingResult result,
                           Model model) {
        if (!result.hasFieldErrors("confirmPassword")
                && !registerRequest.getConfirmPassword().equals(registerRequest.getPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Le password non coincidono");
        }

        if (result.hasErrors()) {
            log.warn("register: rejected — validation errors for username \"{}\"", registerRequest.getUsername());
            model.addAttribute("token", token);
            return "register";
        }

        inviteService.acceptForNewUser(
            token,
            registerRequest.getUsername(),
            registerRequest.getPassword(),
            registerRequest.getFantaTeamName()
        );
        log.info("register: new user \"{}\" registered via invite", registerRequest.getUsername());
        return "redirect:/login?registered";
    }
}
