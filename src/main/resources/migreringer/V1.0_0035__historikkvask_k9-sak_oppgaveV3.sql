CREATE TABLE if not exists behandling_prosess_events_k9_historikkvask_ferdig
(
    id                          VARCHAR(100)     NOT NULL PRIMARY KEY,
    CONSTRAINT fk_bpe_k9_historikkvask_ferdig
        FOREIGN KEY(id) references behandling_prosess_events_k9(id)
);

CREATE TABLE if not exists behandling_prosess_events_klage_historikkvask_ferdig
(
    id                          VARCHAR(100)     NOT NULL PRIMARY KEY,
    CONSTRAINT fk_bpe_klage_historikkvask_ferdig
        FOREIGN KEY(id) references behandling_prosess_events_klage(id)
);