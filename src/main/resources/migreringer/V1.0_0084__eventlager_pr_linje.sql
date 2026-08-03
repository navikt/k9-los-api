create table event_nokkel
(
    ID                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    EKSTERN_ID                  VARCHAR(100)    NOT NULL,
    FAGSYSTEM                   VARCHAR(50)     NOT NULL,
    unique (FAGSYSTEM, EKSTERN_ID)
);

create table event
(
    EVENT_NOKKEL_ID        BIGINT          NOT NULL REFERENCES event_nokkel (id) ,
    EKSTERN_VERSJON             VARCHAR(100)    NOT NULL,
    DATA                        jsonb           NOT NULL,
    dirty                       boolean         NOT NULL DEFAULT TRUE,
    opprettet                   timestamp       NOT NULL default localtimestamp,
    primary key (EVENT_NOKKEL_ID, EKSTERN_VERSJON)
);

CREATE TABLE event_historikkvask_bestilt
(
    EVENT_NOKKEL_ID        BIGINT NOT NULL PRIMARY KEY REFERENCES event_nokkel(id)
);