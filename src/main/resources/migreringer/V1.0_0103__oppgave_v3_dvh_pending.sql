-- Pending-tabell for DVH-statistikk-sending.
-- Erstatter den omvendte bokholdingen via anti-join mot oppgave_v3_sendt_dvh_ekstern.
-- Populeres via forvaltnings-endepunkt (ikke her, pga. Flyway timeout-risiko på store tabeller).
CREATE TABLE IF NOT EXISTS oppgave_v3_dvh_pending
(
    ekstern_id              TEXT NOT NULL,
    ekstern_versjon         TEXT NOT NULL,
    oppgavetype_ekstern_id  TEXT NOT NULL,
    PRIMARY KEY (ekstern_id, ekstern_versjon)
);

CREATE INDEX IF NOT EXISTS idx_oppgave_v3_dvh_pending_oppgavetype
    ON oppgave_v3_dvh_pending (oppgavetype_ekstern_id);

