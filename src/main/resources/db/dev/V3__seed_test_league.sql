INSERT INTO leagues (name, season, matchday_cost, jackpot_start, bet_deadline_minutes, status)
VALUES ('Lega Test', '2025-26', 1, 0, 5, 'ACTIVE');

INSERT INTO jackpots (league_id, current_amount)
VALUES (1, 0);
