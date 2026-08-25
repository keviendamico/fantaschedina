-- Jackpot snapshot taken when the matchday closes (after auto-submits are charged).
-- It is the amount actually distributed to that matchday's winners, so a matchday
-- waiting for a postponed match does not collect the stakes of later matchdays.
-- Nullable: matchdays closed before this migration fall back to the live jackpot.
ALTER TABLE matchdays ADD COLUMN jackpot_at_close INTEGER;