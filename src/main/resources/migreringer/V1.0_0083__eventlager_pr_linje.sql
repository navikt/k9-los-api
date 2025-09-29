create table if not exists eventlager
(
    EVENTLAGER_NOKKEL_ID        BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY REFERENCES eventlager_nokkel (id) ,
    EKSTERN_VERSJON             VARCHAR(100)    NOT NULL,
    DATA                        jsonb           NOT NULL,
    dirty                       boolean         NOT NULL DEFAULT TRUE,
    opprettet                   timestamp       NOT NULL default localtimestamp,
    unique (ID, EKSTERN_VERSJON)
);

create table eventlager_nokkel
(
    ID                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    EKSTERN_ID                  VARCHAR(100)    NOT NULL,
    FAGSYSTEM                   VARCHAR(50)     NOT NULL,
    unique (FAGSYSTEM, EKSTERN_ID)
)

CREATE TABLE if not exists eventlager_historikkvask_bestilt
(
    EVENTLAGER_NOKKEL_ID        PRIMARY KEY REFERENCES eventlager_nokkel(id)
);