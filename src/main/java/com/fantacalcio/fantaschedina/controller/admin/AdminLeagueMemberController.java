package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.service.AdminLeagueMemberService;
import com.fantacalcio.fantaschedina.service.LeagueService;
import com.fantacalcio.fantaschedina.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/leagues/{leagueId}/members")
@RequiredArgsConstructor
public class AdminLeagueMemberController {

    private final LeagueService leagueService;
    private final AdminLeagueMemberService memberService;
    private final UserService userService;

    @GetMapping
    public String members(@PathVariable Long leagueId,
                          @AuthenticationPrincipal UserDetails user,
                          Model model) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        model.addAttribute("league", leagueService.findById(leagueId));
        model.addAttribute("members", memberService.getMembers(leagueId));
        model.addAttribute("auditLog", memberService.getAuditLog(leagueId));
        return "admin/leagues/members";
    }

    @PostMapping("/{membershipId}/adjust")
    public String adjustMember(@PathVariable Long leagueId,
                               @PathVariable Long membershipId,
                               @AuthenticationPrincipal UserDetails user,
                               @RequestParam int delta,
                               @RequestParam(required = false) String note,
                               RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        memberService.adjustMemberBalance(membershipId, delta, note);
        redirectAttributes.addFlashAttribute("success", "Crediti aggiornati.");
        return "redirect:/admin/leagues/" + leagueId + "/members";
    }

}