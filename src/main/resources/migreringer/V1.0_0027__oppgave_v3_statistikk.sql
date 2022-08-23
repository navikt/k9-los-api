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
    implementasjonstype         VARCHAR(100)                            NOT NULL
    --vis_til_bruker              boolean                                 NOT NULL DEFAULT TRUE --ikke aktuelt for statistikk
);

CREATE TABLE if not exists OPPGAVETYPE
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique,
    omrade_id                   BIGINT                                  NOT NULL,
    definisjonskilde            VARCHAR(100)                            NOT NULL
    --offentlig                   boolean                                 NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS OPPGAVEFELT
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    datatype_id                 BIGINT                                  NOT NULL,
    oppgavetype_id              BIGINT                                  NOT NULL,
    --vis_pa_oppgave              boolean                                 NOT NULL DEFAULT TRUE, --ikke aktuelt for statistikk
    pakrevd                     boolean                                 NOT NULL DEFAULT FALSE
);

CREATE TABLE if not exists OPPGAVE
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique,
    oppgavetype_id              BIGINT                                  NOT NULL,
    versjon                     int                                     NOT NULL,
    aktiv                       boolean                                 NOT NULL DEFAULT TRUE,
    opprettet                   timestamp(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fullfort                    timestamp(3)
);

CREATE TABLE if not exists OPPGAVEFELT_VERDI
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    oppgave_id                  BIGINT                                  NOT NULL,
    oppgavefelt_id              BIGINT                                  NOT NULL,
    verdi                       VARCHAR(100)                            NOT NULL
)