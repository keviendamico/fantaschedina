package com.fantacalcio.fantaschedina.scheduler;

import com.fantacalcio.fantaschedina.domain.enums.InviteStatus;
import com.fantacalcio.fantaschedina.repository.InviteRepository;
import com.fantacalcio.fantaschedina.util.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class InviteScheduler {

    private final InviteRepository inviteRepository;

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void expireInvites() {
        CorrelationId.runAsJob(this::doExpireInvites);
    }

    private void doExpireInvites() {
        var expired = inviteRepository.findByStatusAndExpiresAtBefore(InviteStatus.PENDING, LocalDateTime.now());
        expired.forEach(invite -> invite.setStatus(InviteStatus.EXPIRED));
        inviteRepository.saveAll(expired);
        log.info("Expired {} pending invites", expired.size());
    }
}