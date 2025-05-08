CREATE TABLE if not exists behandling_prosess_events_tilbake_historikkvask_ferdig
(
    id                          VARCHAR(100)     NOT NULL PRIMARY KEY,
    CONSTRAINT fk_bpe_tilbake_historikkvask_ferdig
        FOREIGN KEY(id) references behandling_prosess_events_tilbake(id)
);
