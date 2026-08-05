package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Invite;
import com.fantacalcio.fantaschedina.domain.entity.League;
import com.fantacalcio.fantaschedina.domain.entity.Matchday;
import com.fantacalcio.fantaschedina.domain.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:noreply@fantatotocalcio.it}")
    private String from;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendInviteEmail(Invite invite, League league) {
        String inviteLink = baseUrl + "/invite/accept?token=" + invite.getToken();

        Context context = new Context(Locale.ITALIAN);
        context.setVariable("leagueName", league.getName());
        context.setVariable("inviteLink", inviteLink);
        context.setVariable("expiresAt", invite.getExpiresAt().toLocalDate().format(DATE_ONLY));

        sendHtmlEmail(invite.getEmail(), "Sei stato invitato a partecipare a " + league.getName(),
                "invite", context);
    }

    public void sendPasswordResetEmail(User user, String token) {
        String resetLink = baseUrl + "/reset-password?token=" + token;

        Context context = new Context(Locale.ITALIAN);
        context.setVariable("username", user.getUsername());
        context.setVariable("resetLink", resetLink);

        sendHtmlEmail(user.getEmail(), "Reimposta la tua password FantaTotocalcio",
                "password-reset", context);
    }

    public void sendReminderEmail(User user, League league, Matchday matchday, LocalDateTime deadline) {
        if (!user.getNotificationsEnabled()) return;

        String betLink = baseUrl + "/leagues/" + league.getId() + "/matchdays/" + matchday.getId() + "/bet";

        Context context = new Context(Locale.ITALIAN);
        context.setVariable("username", user.getUsername());
        context.setVariable("leagueName", league.getName());
        context.setVariable("matchdayNumber", matchday.getNumber());
        context.setVariable("deadline", deadline.format(DATE_TIME));
        context.setVariable("betLink", betLink);

        sendHtmlEmail(user.getEmail(),
                "Non hai ancora giocato la giornata " + matchday.getNumber() + " – " + league.getName(),
                "reminder", context);
    }

    public void sendAutoSubmitEmail(User user, League league, Matchday matchday, int amountCharged) {
        if (!user.getNotificationsEnabled()) return;

        String matchdayLink = baseUrl + "/leagues/" + league.getId() + "/matchdays/" + matchday.getId();

        Context context = new Context(Locale.ITALIAN);
        context.setVariable("username", user.getUsername());
        context.setVariable("leagueName", league.getName());
        context.setVariable("matchdayNumber", matchday.getNumber());
        context.setVariable("amountCharged", amountCharged);
        context.setVariable("matchdayLink", matchdayLink);

        sendHtmlEmail(user.getEmail(), "Schedina auto-generata – giornata " + matchday.getNumber(),
                "auto-submit", context);
    }

    public void sendResultsAvailableEmail(User user, League league, Matchday matchday) {
        if (!user.getNotificationsEnabled()) return;

        String matchdayLink = baseUrl + "/leagues/" + league.getId() + "/matchdays/" + matchday.getId();

        Context context = new Context(Locale.ITALIAN);
        context.setVariable("username", user.getUsername());
        context.setVariable("leagueName", league.getName());
        context.setVariable("matchdayNumber", matchday.getNumber());
        context.setVariable("matchdayLink", matchdayLink);

        sendHtmlEmail(user.getEmail(), "Risultati disponibili – giornata " + matchday.getNumber(),
                "results-available", context);
    }

    public void sendJackpotWonEmail(User user, League league, Matchday matchday, List<String> winnerTeamNames, int amountPerWinner) {
        if (!user.getNotificationsEnabled()) return;

        String matchdayLink = baseUrl + "/leagues/" + league.getId() + "/matchdays/" + matchday.getId();

        Context context = new Context(Locale.ITALIAN);
        context.setVariable("username", user.getUsername());
        context.setVariable("leagueName", league.getName());
        context.setVariable("matchdayNumber", matchday.getNumber());
        context.setVariable("winnerTeamNames", winnerTeamNames);
        context.setVariable("amountPerWinner", amountPerWinner);
        context.setVariable("matchdayLink", matchdayLink);

        sendHtmlEmail(user.getEmail(),
                "Signori, abbiamo un vincitore! – giornata " + matchday.getNumber() + " – " + league.getName(),
                "jackpot-won", context);
    }

    private void sendHtmlEmail(String to, String subject, String templateName, Context context) {
        try {
            String html = templateEngine.process("email/" + templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline("logo", new ClassPathResource("static/images/logo.png"));

            mailSender.send(message);
            log.info("Email '{}' sent to {}", templateName, to);
        } catch (Exception e) {
            log.error("Failed to send '{}' email to {}: {}", templateName, to, e.getMessage());
        }
    }
}
