CREATE TABLE if not exists MERKNAD
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY    NOT NULL PRIMARY KEY,
    behandling_id               BIGINT                                 NOT NULL,
    ekstern_referanse           VARCHAR(36)                            NOT NULL,
    merknad_koder               JSONB                                  NOT NULL,
    oppgave_ider                JSONB                                  NOT NULL,
    oppgave_koder               JSONB                                  NOT NULL,
    fritekst                    VARCHAR(500)                           NULL,
    saksbehandler               VARCHAR(30)                            NOT NULL,
    opprettet                   TIMESTAMP(3)                           NOT NULL,
    sist_endret                 TIMESTAMP(3)                           NULL,
    slettet                     BOOLEAN                                DEFAULT FALSE
);

CREATE INDEX idx_behandlingid on MERKNAD (behandling_id);
CREATE INDEX idx_eksternreferanse on MERKNAD (ekstern_referanse);


CREATE TABLE if not exists BEHANDLING_DATA
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY    NOT NULL PRIMARY KEY,
    ekstern_referanse           VARCHAR(36)                            NOT NULL,
    data                        JSONB                                  NOT NULL
);

ALTER TABLE BEHANDLING DROP COLUMN SKJERMET;
ALTER TABLE BEHANDLING DROP COLUMN KODE6;

