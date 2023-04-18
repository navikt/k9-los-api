CREATE TABLE if not exists OPPGAVEKO_V3
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    versjon                     BIGINT                                  NOT NULL,
    tittel                      VARCHAR(100)                            NOT NULL,
    beskrivelse                 VARCHAR(4000)                           NOT NULL,
    query                       TEXT                                    NOT NULL,
    fritt_valg_av_oppgave       BOOL                                    NOT NULL
);

CREATE TABLE if not exists OPPGAVEKO_SAKSBEHANDLER
(
    id                            BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    oppgaveko_v3_id               BIGINT                                  NOT NULL,
    saksbehandler_epost           VARCHAR(200)                            NOT NULL,

    CONSTRAINT FK_OPPGAVEKO_V3_ID FOREIGN KEY(oppgaveko_v3_id) references OPPGAVEKO_V3(id)
    CONSTRAINT FK_SAKSBEHANDLER_EPOST FOREIGN KEY(saksbehandler_epost) references SAKSBEHANDLER(epost)
);