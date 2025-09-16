create table if not exists eventlager
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    FAGSYSTEM                   VARCHAR(50)     NOT NULL,
    EKSTERN_ID                  VARCHAR(100)    NOT NULL,
    EKSTERN_VERSJON             VARCHAR(100)    NOT NULL,
    DATA                        jsonb           NOT NULL,
    dirty                       boolean         NOT NULL DEFAULT TRUE,
    opprettet                   timestamp       NOT NULL default localtimestamp,
    unique (FAGSYSTEM, EKSTERN_ID, EKSTERN_VERSJON)
);

CREATE TABLE if not exists eventlager_historikkvask_bestilt
(
    EKSTERN_ID                  VARCHAR(100)    NOT NULL PRIMARY KEY,
    FAGSYSTEM                   VARCHAR(50)     NOT NULL
);