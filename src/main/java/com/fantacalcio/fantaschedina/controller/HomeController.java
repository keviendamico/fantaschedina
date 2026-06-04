package com.fantacalcio.fantaschedina.controller;

import com.fantacalcio.fantaschedina.service.UserDashboardService;
import com.fantacalcio.fantaschedina.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserDashboardService userDashboardService;
    private final UserService userService;

    @GetMapping("/")
    public String root(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return "redirect:/admin/dashboard";
        }
        Long userId = userService.getUserId(authentication.getName());
        model.addAttribute("leagueCards", userDashboardService.buildCards(userId));
        return "dashboard";
    }
}