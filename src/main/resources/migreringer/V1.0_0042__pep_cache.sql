CREATE TABLE IF NOT EXISTS OPPGAVE_PEP_CACHE(
        id              BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
        kildeomrade     VARCHAR(30)                             NOT NULL,
        ekstern_id      VARCHAR(100)                            NOT NULL,
        kode6           BOOLEAN                                 NOT NULL,
        kode7           BOOLEAN                                 NOT NULL,
        egen_ansatt     BOOLEAN                                 NOT NULL,
        oppdatert       timestamp                               NOT NULL
);

CREATE INDEX ON OPPGAVE_PEP_CACHE(oppdatert);
CREATE UNIQUE INDEX idx_pep_kildeomrade_eksternid ON OPPGAVE_PEP_CACHE(kildeomrade, ekstern_id);

ALTER TABLE OPPGAVE_PEP_CACHE
    ADD CONSTRAINT pep_kildeomrade_eksternid
        UNIQUE USING INDEX idx_pep_kildeomrade_eksternid;