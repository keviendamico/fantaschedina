package com.fantacalcio.fantaschedina.service;

import com.fantacalcio.fantaschedina.domain.entity.Invite;
import com.fantacalcio.fantaschedina.domain.entity.League;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@fantatotocalcio.it}")
    private String from;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendInviteEmail(Invite invite, League league) {
        String inviteLink = baseUrl + "/invite/accept?token=" + invite.getToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(invite.getEmail());
        message.setSubject("Sei stato invitato a partecipare a " + league.getName());
        message.setText("""
                Ciao!

                Sei stato invitato a unirti alla lega "%s" su FantaTotocalcio.

                Clicca il link per accettare l'invito:
                %s

                Il link scade il %s.

                FantaTotocalcio
                """.formatted(league.getName(), inviteLink, invite.getExpiresAt().toLocalDate()));

        try {
            mailSender.send(message);
            log.info("Invite email sent to {}", invite.getEmail());
        } catch (Exception e) {
            log.error("Failed to send invite email to {}: {}", invite.getEmail(), e.getMessage());
        }
    }
}
