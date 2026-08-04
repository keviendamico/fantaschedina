package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.dto.ForgotPasswordRequest;
import com.fantacalcio.fantaschedina.dto.ResetPasswordRequest;
import com.fantacalcio.fantaschedina.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        if (!model.containsAttribute("sent")) {
            model.addAttribute("sent", false);
        }
        model.addAttribute("forgotPasswordRequest", new ForgotPasswordRequest());
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute ForgotPasswordRequest forgotPasswordRequest,
                                 BindingResult result,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "forgot-password";
        }

        passwordResetService.requestReset(forgotPasswordRequest.getEmail());
        redirectAttributes.addFlashAttribute("sent", true);
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        passwordResetService.findValidToken(token);
        model.addAttribute("token", token);
        model.addAttribute("resetPasswordRequest", new ResetPasswordRequest());
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @Valid @ModelAttribute ResetPasswordRequest resetPasswordRequest,
                                BindingResult result,
                                Model model) {
        if (!result.hasFieldErrors("confirmPassword")
                && !resetPasswordRequest.getConfirmPassword().equals(resetPasswordRequest.getPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Le password non coincidono");
        }

        if (result.hasErrors()) {
            model.addAttribute("token", token);
            return "reset-password";
        }

        passwordResetService.resetPassword(token, resetPasswordRequest.getPassword());
        return "redirect:/login?resetSuccess";
    }
}
