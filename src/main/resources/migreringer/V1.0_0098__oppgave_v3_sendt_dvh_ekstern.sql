CREATE TABLE if not exists OPPGAVE_V3_SENDT_DVH_EKSTERN
(
    ekstern_id      TEXT NOT NULL,
    ekstern_versjon TEXT NOT NULL,
    PRIMARY KEY (ekstern_id, ekstern_versjon)
);

