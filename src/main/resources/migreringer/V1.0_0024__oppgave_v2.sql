CREATE TABLE if not exists BEHANDLING
(
     id                          VARCHAR(36)     NOT NULL PRIMARY KEY,
     ekstern_referanse           VARCHAR(36)     NOT NULL,
     fagsystem                   VARCHAR(20)     NOT NULL,
     ytelse_type                 VARCHAR(20)     NOT NULL,
     behandling_type             VARCHAR(20)     NULL,
     ferdigstilt_tidspunkt       TIMESTAMP       NULL,
     soekers_id                  VARCHAR(20)     NULL,
     kode6                       BOOLEAN         NULL,
     skjermet                    BOOLEAN NULL
);

CREATE TABLE if not exists DELOPPGAVE(
    id                          VARCHAR(36)     NOT NULL PRIMARY KEY,
    behandling_id               VARCHAR(36)     NOT NULL,
    ekstern_referanse           VARCHAR(36)     NOT NULL,
    oppgave_status              VARCHAR(20)     NOT NULL,
    oppgave_kode                VARCHAR(20)     NOT NULL,
    opprettet                   TIMESTAMP       NOT NULL,
    sist_endret                 TIMESTAMP       NOT NULL,
    ferdigstilt_tidspunkt       TIMESTAMP       NULL,
    ferdigstilt_saksbehandler   VARCHAR(10)     NULL,
    ferdigstilt_enhet           VARCHAR(10)     NULL,
    frist                       TIMESTAMP       NULL
);

CREATE UNIQUE INDEX idx_id_referanse on BEHANDLING (id, ekstern_referanse);
CREATE UNIQUE INDEX idx_referanse_fagsystem on BEHANDLING (ekstern_referanse, fagsystem);
CREATE UNIQUE INDEX idx_referanse_fagytelse on BEHANDLING (ekstern_referanse, ytelse_type);
CREATE INDEX idx_kode6 on BEHANDLING (kode6);
CREATE INDEX idx_skjermet on BEHANDLING (skjermet);
CREATE INDEX idx_ferdigstilt on BEHANDLING (ferdigstilt_tidspunkt);

CREATE INDEX idx_oppgave_aktiv on DELOPPGAVE (oppgave_status);
CREATE INDEX idx_oppgave_opprettet on DELOPPGAVE (opprettet);
CREATE INDEX idx_oppgave_ferdigstilt on DELOPPGAVE (ferdigstilt_tidspunkt);
