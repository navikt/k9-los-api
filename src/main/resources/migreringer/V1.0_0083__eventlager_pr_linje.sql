create table if not exists eventlager
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    FAGSYSTEM                   VARCHAR(50)     NOT NULL,
    EKSTERN_ID                  VARCHAR(100)    NOT NULL,
    EKSTERN_VERSJON             VARCHAR(100)    NOT NULL,
    INTERN_VERSJON              INT             NOT NULL,
    DATA                        jsonb           NOT NULL,
    dirty                       boolean         NOT NULL DEFAULT TRUE,
    opprettet                   timestamp       NOT NULL default localtimestamp,
    unique (FAGSYSTEM, EKSTERN_ID, EKSTERN_VERSJON),
    unique (FAGSYSTEM, EKSTERN_ID, INTERN_VERSJON)
);

CREATE TABLE if not exists eventlager_historikkvask_ferdig
(
    id                  BIGINT    NOT NULL PRIMARY KEY,
    CONSTRAINT fk_eventlager_historikkvask_ferdig
        FOREIGN KEY(id) references eventlager(id)
);