package com.fantacalcio.fantaschedina.domain.entity;

import com.fantacalcio.fantaschedina.domain.enums.AdminLogType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "league_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeagueAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long leagueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminLogType type;

    private Long targetMembershipId;

    private Integer amount;

    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}