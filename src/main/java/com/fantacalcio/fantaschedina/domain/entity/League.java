package com.fantacalcio.fantaschedina.domain.entity;

import com.fantacalcio.fantaschedina.domain.enums.LeagueStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leagues")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String season;

    @Column(nullable = false)
    private Integer matchdayCost;

    @Column(nullable = false)
    @Builder.Default
    private Integer jackpotStart = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer betDeadlineMinutes = 5;

    private Integer maxTeams;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeagueStatus status;
}
