create table if not exists eventlager_punsj
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    EKSTERN_ID                  VARCHAR(100)    NOT NULL PRIMARY KEY,
    EVENTNR_FOR_OPPGAVE         INT             NOT NULL,
    DATA                        jsonb           NOT NULL,
    dirty                       boolean         NOT NULL DEFAULT TRUE
);

CREATE TABLE if not exists eventlager_punsj_historikkvask_ferdig
(
    id                  VARCHAR(100)    NOT NULL PRIMARY KEY,
    CONSTRAINT fk_eventlager_punsj_historikkvask_ferdig
        FOREIGN KEY(id) references eventlager_punsj(id)
);