package com.fantacalcio.fantaschedina.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeagueRequest {

    @NotBlank(message = "Il nome è obbligatorio")
    private String name;

    @NotBlank(message = "La stagione è obbligatoria")
    private String season;

    @NotNull(message = "Il costo schedina è obbligatorio")
    @Min(value = 1, message = "Il costo deve essere almeno 1")
    private Integer matchdayCost;

    @NotNull(message = "Il jackpot iniziale è obbligatorio")
    @Min(value = 0, message = "Il jackpot iniziale non può essere negativo")
    private Integer jackpotStart;

    @NotNull(message = "La deadline è obbligatoria")
    @Min(value = 1, message = "La deadline deve essere almeno 1 minuto")
    private Integer betDeadlineMinutes;

    @Min(value = 2, message = "Il numero di squadre deve essere almeno 2")
    private Integer maxTeams;
}
