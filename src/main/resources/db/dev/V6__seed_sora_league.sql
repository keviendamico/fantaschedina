-- ============================================================
-- Seeder: "Fantacalcio Sora" con 12 utenti e relative squadre
-- Password di tutti gli utenti = Password1!
-- ============================================================

-- 1. Lega
INSERT INTO leagues (name, season, matchday_cost, jackpot_start, bet_deadline_minutes, status)
VALUES ('Fantacalcio Sora', '2025-26', 1, 0, 5, 'SETUP');

INSERT INTO jackpots (league_id, current_amount)
SELECT id, 0 FROM leagues WHERE name = 'Fantacalcio Sora' AND season = '2025-26';

-- 2. Utenti
INSERT INTO users (username, email, password_hash, role, enabled) VALUES
('mammut',      'mammut@fantaschedina.dev',      '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('mvb992',      'mvb992@fantaschedina.dev',      '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('nino_sol',    'nino_sol@fantaschedina.dev',    '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('zibrido',     'zibrido@fantaschedina.dev',     '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('battaglin',   'battaglin@fantaschedina.dev',   '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('kevien992',   'kevien992@fantaschedina.dev',   '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('ramo_oc',     'ramo_oc@fantaschedina.dev',     '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('il_barbiere', 'il_barbiere@fantaschedina.dev', '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('derp88',      'derp88@fantaschedina.dev',      '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('doppiag8',    'doppiag8@fantaschedina.dev',    '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('snai',        'snai@fantaschedina.dev',        '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE),
('finnibaldi',  'finnibaldi@fantaschedina.dev',  '$2a$10$7y6YlDrlRKFHYvI76UlpZOsF.3xNJlEktHlp3L13QFmPplFXrSRpe', 'USER', TRUE);

-- 3. Membership (balance = 35 = 35 giornate × 1 credito)
INSERT INTO league_memberships (league_id, user_id, balance, joined_at)
SELECT
    (SELECT id FROM leagues WHERE name = 'Fantacalcio Sora' AND season = '2025-26'),
    u.id,
    35,
    NOW()
FROM users u
WHERE u.username IN (
    'mammut','mvb992','nino_sol','zibrido','battaglin','kevien992',
    'ramo_oc','il_barbiere','derp88','doppiag8','snai','finnibaldi'
);

-- 4. FantaTeam (uno per membership)
INSERT INTO fanta_teams (league_id, league_membership_id, name)
SELECT
    lm.league_id,
    lm.id,
    CASE u.username
        WHEN 'mammut'      THEN 'A.C. Padova'
        WHEN 'mvb992'      THEN 'Sora'
        WHEN 'nino_sol'    THEN 'Fiorentina'
        WHEN 'zibrido'     THEN 'Atletico Foggia'
        WHEN 'battaglin'   THEN 'FC COMO 1907'
        WHEN 'kevien992'   THEN 'Venezia FC'
        WHEN 'ramo_oc'     THEN 'Parma Calcio 1913'
        WHEN 'il_barbiere' THEN 'A.S.D. Latte Dolce Calcio'
        WHEN 'derp88'      THEN 'Bari'
        WHEN 'doppiag8'    THEN 'SS Sambenedettese'
        WHEN 'snai'        THEN 'Sassuolo'
        WHEN 'finnibaldi'  THEN 'S.C. Pisa'
    END
FROM league_memberships lm
JOIN users u ON u.id = lm.user_id
WHERE u.username IN (
    'mammut','mvb992','nino_sol','zibrido','battaglin','kevien992',
    'ramo_oc','il_barbiere','derp88','doppiag8','snai','finnibaldi'
)
AND lm.league_id = (SELECT id FROM leagues WHERE name = 'Fantacalcio Sora' AND season = '2025-26');

-- 5. Transazioni di deposito iniziale
INSERT INTO credit_transactions (league_membership_id, type, amount, balance_after, created_at, note)
SELECT
    lm.id,
    'INITIAL_DEPOSIT',
    35,
    35,
    NOW(),
    'Deposito iniziale Fantacalcio Sora 2025-26'
FROM league_memberships lm
JOIN users u ON u.id = lm.user_id
WHERE u.username IN (
    'mammut','mvb992','nino_sol','zibrido','battaglin','kevien992',
    'ramo_oc','il_barbiere','derp88','doppiag8','snai','finnibaldi'
)
AND lm.league_id = (SELECT id FROM leagues WHERE name = 'Fantacalcio Sora' AND season = '2025-26');