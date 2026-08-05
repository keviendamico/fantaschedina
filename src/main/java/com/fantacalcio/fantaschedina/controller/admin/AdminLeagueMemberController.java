package com.fantacalcio.fantaschedina.controller.admin;

import com.fantacalcio.fantaschedina.domain.entity.LeagueMembership;
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

    @GetMapping("/{membershipId}/edit-user")
    public String editUserForm(@PathVariable Long leagueId,
                               @PathVariable Long membershipId,
                               @AuthenticationPrincipal UserDetails user,
                               Model model) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        LeagueMembership membership = memberService.getMembershipForLeague(leagueId, membershipId);

        model.addAttribute("league", leagueService.findById(leagueId));
        model.addAttribute("membershipId", membershipId);
        model.addAttribute("targetUser", userService.getById(membership.getUserId()));
        return "admin/leagues/edit-user";
    }

    @PostMapping("/{membershipId}/edit-user")
    public String editUser(@PathVariable Long leagueId,
                           @PathVariable Long membershipId,
                           @RequestParam String email,
                           @RequestParam(defaultValue = "false") boolean notificationsEnabled,
                           @AuthenticationPrincipal UserDetails user,
                           RedirectAttributes redirectAttributes) {
        leagueService.findByIdForAdmin(leagueId, userService.getUserId(user.getUsername()));
        LeagueMembership membership = memberService.getMembershipForLeague(leagueId, membershipId);

        userService.adminUpdateUser(membership.getUserId(), email, notificationsEnabled);
        redirectAttributes.addFlashAttribute("success", "Utente aggiornato.");
        return "redirect:/admin/leagues/" + leagueId + "/members";
    }

}