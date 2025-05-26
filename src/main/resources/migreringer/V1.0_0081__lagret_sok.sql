CREATE TABLE lagret_sok
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    versjon                     BIGINT                                  NOT NULL,
    tittel                      VARCHAR(100)                            NOT NULL,
    beskrivelse                 VARCHAR(4000)                           NOT NULL,
    query                       jsonb                                   NOT NULL,
    laget_av                    BIGINT                                  NOT NULL REFERENCES SAKSBEHANDLER(id)
);