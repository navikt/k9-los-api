-- Én rad som angir hvilken appversjon som har lov til å kjøre skrivende bakgrunnsarbeid.
-- versjon = null betyr at vakten er inaktiv (f.eks. før første registrering, eller i tester).
CREATE TABLE IF NOT EXISTS aktiv_appversjon
(
    id        INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    versjon   TEXT,
    oppdatert TIMESTAMP
);

INSERT INTO aktiv_appversjon (id, versjon, oppdatert)
VALUES (1, NULL, NULL)
ON CONFLICT DO NOTHING;
