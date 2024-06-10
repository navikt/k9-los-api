alter table behandling_prosess_events_k9_punsj add column dirty boolean not null default true;

CREATE TABLE if not exists behandling_prosess_events_k9_punsj_historikkvask_ferdig
(
    id                          VARCHAR(100)     NOT NULL PRIMARY KEY,
    CONSTRAINT fk_bpe_k9_punsj_historikkvask_ferdig
        FOREIGN KEY(id) references behandling_prosess_events_k9_punsj(id)
);