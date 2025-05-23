create table if not exists eventlager_punsj
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    EKSTERN_ID                  VARCHAR(100)    NOT NULL,
    EKSTERN_VERSJON             VARCHAR(100)    NOT NULL,
    EVENTNR_FOR_OPPGAVE         INT             NOT NULL,
    DATA                        jsonb           NOT NULL,
    dirty                       boolean         NOT NULL DEFAULT TRUE,
    opprettet                   timestamp       NOT NULL default localtimestamp,
    unique (EKSTERN_ID, EKSTERN_VERSJON)
);

CREATE TABLE if not exists eventlager_punsj_historikkvask_ferdig
(
    id                  BIGINT    NOT NULL PRIMARY KEY,
    CONSTRAINT fk_eventlager_punsj_historikkvask_ferdig
        FOREIGN KEY(id) references eventlager_punsj(id)
);