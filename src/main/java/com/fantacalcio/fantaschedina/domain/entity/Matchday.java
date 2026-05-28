package com.fantacalcio.fantaschedina.domain.entity;

import com.fantacalcio.fantaschedina.domain.enums.MatchdayStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "matchdays")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Matchday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long leagueId;

    @Column(nullable = false)
    private Integer number;

    private LocalDateTime startAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchdayStatus status;
}
