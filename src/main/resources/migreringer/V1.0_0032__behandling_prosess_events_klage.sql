create table if not exists BEHANDLING_PROSESS_EVENTS_KLAGE
(
    ID   VARCHAR(100) NOT NULL PRIMARY KEY,
    DATA jsonb        NOT NULL,
    dirty boolean     NOT NULL DEFAULT TRUE
);
