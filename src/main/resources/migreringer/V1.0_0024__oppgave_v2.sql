CREATE TABLE if not exists BEHANDLING
(
     id                          BIGINT GENERATED ALWAYS AS IDENTITY    NOT NULL PRIMARY KEY,
     ekstern_referanse           VARCHAR(36)                            NOT NULL,
     fagsystem                   VARCHAR(50)                            NOT NULL,
     ytelse_type                 VARCHAR(100)                           NOT NULL,
     behandling_type             VARCHAR(100)                           NULL,
     opprettet                   TIMESTAMP(3)                           NULL,
     sist_endret                 TIMESTAMP(3)                           NULL,
     ferdigstilt_tidspunkt       TIMESTAMP(3)                           NULL,
     soekers_id                  VARCHAR(20)                            NULL,
     kode6                       BOOLEAN                                NULL,
     skjermet                    BOOLEAN                                NULL
);

CREATE TABLE if not exists OPPGAVE_V2(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    behandling_id               BIGINT                                  NOT NULL,
    ekstern_referanse           VARCHAR(36)                             NOT NULL,
    oppgave_status              VARCHAR(20)                             NOT NULL,
    oppgave_kode                VARCHAR(20)                             NOT NULL,
    opprettet                   TIMESTAMP(3)                            NOT NULL,
    sist_endret                 TIMESTAMP(3)                            NOT NULL,
    beslutter                   BOOLEAN                                 NOT NULL,
    ferdigstilt_tidspunkt       TIMESTAMP(3)                            NULL,
    ferdigstilt_saksbehandler   VARCHAR(20)                             NULL,
    ferdigstilt_enhet           VARCHAR(50)                             NULL,
    frist                       TIMESTAMP(3)                            NULL
);

CREATE UNIQUE INDEX idx_id_referanse on BEHANDLING (id, ekstern_referanse);
CREATE UNIQUE INDEX idx_referanse_fagsystem on BEHANDLING (ekstern_referanse, fagsystem);
CREATE UNIQUE INDEX idx_referanse_fagytelse on BEHANDLING (ekstern_referanse, ytelse_type);
CREATE INDEX idx_kode6 on BEHANDLING (kode6);
CREATE INDEX idx_skjermet on BEHANDLING (skjermet);
CREATE INDEX idx_ferdigstilt on BEHANDLING (ferdigstilt_tidspunkt);

CREATE INDEX idx_oppgave_aktiv on OPPGAVE_V2 (oppgave_status);
CREATE INDEX idx_oppgave_opprettet on OPPGAVE_V2 (opprettet);
CREATE INDEX idx_oppgave_ferdigstilt on OPPGAVE_V2 (ferdigstilt_tidspunkt);
