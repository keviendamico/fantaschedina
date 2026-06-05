-- ============================================================
-- Dev seeder: "Lega Dev" con 12 utenti e relative squadre
-- Password di tutti gli utenti = stessa dell'admin (kappanove)
-- ============================================================

-- 1. Lega
INSERT INTO leagues (name, season, matchday_cost, jackpot_start, bet_deadline_minutes, status)
VALUES ('Lega Dev', '2025-26', 10, 0, 5, 'SETUP');

INSERT INTO jackpots (league_id, current_amount)
SELECT id, 0 FROM leagues WHERE name = 'Lega Dev' AND season = '2025-26';

-- 2. Utenti (stessa password hash dell'admin)
INSERT INTO users (username, email, password_hash, role, enabled) VALUES
('user01', 'user01@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user02', 'user02@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user03', 'user03@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user04', 'user04@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user05', 'user05@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user06', 'user06@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user07', 'user07@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user08', 'user08@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user09', 'user09@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user10', 'user10@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user11', 'user11@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE),
('user12', 'user12@fantaschedina.dev', '$2b$12$1lE8kMpytIX7kLUIWdRjjuzgcsrarMM6JUfddZBMJYP40qGxTtODy', 'USER', TRUE);

-- 3. Membership (balance = 380 = 38 giornate × 10 cr, utile per testare subito)
INSERT INTO league_memberships (league_id, user_id, balance, joined_at)
SELECT
    (SELECT id FROM leagues WHERE name = 'Lega Dev' AND season = '2025-26'),
    u.id,
    380,
    NOW()
FROM users u
WHERE u.username IN (
    'user01','user02','user03','user04','user05','user06',
    'user07','user08','user09','user10','user11','user12'
);

-- 4. FantaTeam (uno per membership)
INSERT INTO fanta_teams (league_id, league_membership_id, name)
SELECT
    lm.league_id,
    lm.id,
    CASE u.username
        WHEN 'user01' THEN 'Aquile Romane'
        WHEN 'user02' THEN 'Stella del Nord'
        WHEN 'user03' THEN 'Rossoneri FC'
        WHEN 'user04' THEN 'Bianconeri United'
        WHEN 'user05' THEN 'Partenopei FC'
        WHEN 'user06' THEN 'Viola FC'
        WHEN 'user07' THEN 'Nerazzurri FC'
        WHEN 'user08' THEN 'Granata FC'
        WHEN 'user09' THEN 'Giallorossi FC'
        WHEN 'user10' THEN 'Laziali FC'
        WHEN 'user11' THEN 'Sampdoriani FC'
        WHEN 'user12' THEN 'Atalantini FC'
    END
FROM league_memberships lm
JOIN users u ON u.id = lm.user_id
WHERE u.username IN (
    'user01','user02','user03','user04','user05','user06',
    'user07','user08','user09','user10','user11','user12'
)
AND lm.league_id = (SELECT id FROM leagues WHERE name = 'Lega Dev' AND season = '2025-26');

-- 5. Transazioni di deposito iniziale
INSERT INTO credit_transactions (league_membership_id, type, amount, balance_after, created_at, note)
SELECT
    lm.id,
    'INITIAL_DEPOSIT',
    380,
    380,
    NOW(),
    'Deposito iniziale seeder dev'
FROM league_memberships lm
JOIN users u ON u.id = lm.user_id
WHERE u.username IN (
    'user01','user02','user03','user04','user05','user06',
    'user07','user08','user09','user10','user11','user12'
)
AND lm.league_id = (SELECT id FROM leagues WHERE name = 'Lega Dev' AND season = '2025-26');