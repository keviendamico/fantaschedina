package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public String profile(Authentication authentication, Model model) {
        model.addAttribute("profileUser", userService.getById(userService.getUserId(authentication.getName())));
        return "profile";
    }

    @PostMapping
    public String updateNotifications(@RequestParam(defaultValue = "false") boolean notificationsEnabled,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        Long userId = userService.getUserId(authentication.getName());
        userService.updateNotificationPreference(userId, notificationsEnabled);
        redirectAttributes.addFlashAttribute("success", "Preferenze aggiornate.");
        return "redirect:/profile";
    }
}