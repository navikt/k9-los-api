CREATE TABLE if not exists OMRADE
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique
);

CREATE TABLE if not exists DATATYPE
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique,
    omrade_id                   BIGINT                                  NOT NULL, --eier
    liste_type                  boolean                                 NOT NULL,
    implementasjonstype         VARCHAR(100)                            NOT NULL,
    --vis_til_bruker              boolean                                 NOT NULL DEFAULT TRUE --ikke aktuelt for statistikk
    CONSTRAINT FK_DATATYPE_01
        FOREIGN KEY(omrade_id) references OMRADE(id)
);

CREATE TABLE if not exists OPPGAVETYPE
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique,
    omrade_id                   BIGINT                                  NOT NULL,
    definisjonskilde            VARCHAR(100)                            NOT NULL,
    --offentlig                   boolean                                 NOT NULL DEFAULT FALSE
    CONSTRAINT FK_OPPGAVETYPE_01
        FOREIGN KEY(omrade_id) REFERENCES OMRADE(id)
);

CREATE TABLE IF NOT EXISTS OPPGAVEFELT
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    datatype_id                 BIGINT                                  NOT NULL,
    oppgavetype_id              BIGINT                                  NOT NULL,
    --vis_pa_oppgave              boolean                                 NOT NULL DEFAULT TRUE, --ikke aktuelt for statistikk,
    pakrevd                     boolean                                 NOT NULL DEFAULT FALSE,
    CONSTRAINT FK_OPPGAVEFELT_01
        FOREIGN KEY(datatype_id) REFERENCES DATATYPE(id),
    CONSTRAINT FK_OPPGAVEFELT_02
        FOREIGN KEY(oppgavetype_id) REFERENCES OPPGAVETYPE(id)
);

CREATE TABLE if not exists OPPGAVE_V3
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique,
    oppgavetype_id              BIGINT                                  NOT NULL,
    versjon                     int                                     NOT NULL,
    aktiv                       boolean                                 NOT NULL DEFAULT TRUE,
    opprettet                   timestamp(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fullfort                    timestamp(3),
    CONSTRAINT FK_OPPGAVE_01
        FOREIGN KEY(oppgavetype_id) REFERENCES OPPGAVETYPE(id)
);

CREATE TABLE if not exists OPPGAVEFELT_VERDI
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    oppgave_id                  BIGINT                                  NOT NULL,
    oppgavefelt_id              BIGINT                                  NOT NULL,
    verdi                       VARCHAR(100)                            NOT NULL,
    CONSTRAINT FK_OPPGAVEFELT_VERDI_01
        FOREIGN KEY(oppgave_id) REFERENCES OPPGAVE_V3(id),
    CONSTRAINT FK_OPPGAVEFELT_VERDI_02
        FOREIGN KEY(oppgavefelt_id) REFERENCES OPPGAVEFELT(id)
)