alter table behandling_prosess_events_k9 add column if not exists dirty boolean not null default true;

CREATE TABLE if not exists OPPGAVE_V3_SENDT_DVH
(
    id                          BIGINT     NOT NULL PRIMARY KEY,
    CONSTRAINT FK_OPPGAVE_V3_ID
        FOREIGN KEY(id) references OPPGAVE_V3(id)
);