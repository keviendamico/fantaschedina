package com.fantacalcio.fantaschedina.controller.user;

import com.fantacalcio.fantaschedina.dto.UserHistoryView;
import com.fantacalcio.fantaschedina.service.MatchdayService;
import com.fantacalcio.fantaschedina.service.UserHistoryService;
import com.fantacalcio.fantaschedina.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/leagues")
@RequiredArgsConstructor
public class UserHistoryController {

    private final MatchdayService matchdayService;
    private final UserHistoryService userHistoryService;
    private final UserService userService;

    @GetMapping("/{leagueId}/history")
    public String history(@PathVariable Long leagueId, Authentication authentication, Model model) {
        Long userId = userService.getUserId(authentication.getName());

        model.addAttribute("league", matchdayService.getLeagueForMember(leagueId, userId));

        UserHistoryView view = userHistoryService.getHistory(leagueId, userId);
        model.addAttribute("membership", view.membership());
        model.addAttribute("myTeam", view.myTeam());
        model.addAttribute("slips", view.slips());
        model.addAttribute("matchdayMap", view.matchdayMap());
        model.addAttribute("transactions", view.transactions());

        return "user/history";
    }
}