alter table saksbehandler add column id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL;
alter table saksbehandler add constraint saksbehandler_id_key UNIQUE (id);

create extension if not exists btree_gist;
create table if not exists RESERVASJON_V3
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    reservertAv                 BIGINT                                  NOT NULL,
    reservasjonsnokkel          varchar(50)                             NOT NULL,
    gyldig_tidsrom              tsrange                                 NOT NULL,
    annullert_for_utlop         boolean                                 NOT NULL default false,
    opprettet                   timestamp                               NOT NULL default localtimestamp,
    sist_endret                 timestamp                               NOT NULL default localtimestamp,
    EXCLUDE USING gist (reservasjonsnokkel with =, gyldig_tidsrom with &&) where (not annullert_for_utlop),
    CONSTRAINT FK_RESERVASJON_V3_01
        FOREIGN KEY (reservertAv) references SAKSBEHANDLER(id)
);

comment on table RESERVASJON_V3 is 'Saksbehandleres reservasjoner på oppgaver';

comment on column RESERVASJON_V3.reservertAv is 'Saksbehandler som har reservert';
comment on column RESERVASJON_V3.reservasjonsnokkel is 'Identifiserer hva som reserveres. Reservasjonsnøkkelen går igjen på en eller flere oppgaver, hvor alle oppgaver med samme reservasjonsnøkkel vil anses som reservert av den samme reservasjonen.';
comment on column RESERVASJON_V3.gyldig_tidsrom is 'Når en reservasjon gjelder for. Systemet skal ikke tillate mer en 1 reservasjon på 1 nøkkel i et gitt tidsrom. Tsrange inkluderer timestamp-fra men ikke -til aka: [fra, til)';
comment on column RESERVASJON_V3.annullert_for_utlop is 'Hvis dette feltet er populert er ikke reservasjonen lenger gyldig, og er da unntatt eksklusjonskriteriet';

create table if not exists RESERVASJON_V3_ENDRING
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    annullert_reservasjon_id       BIGINT                                  NOT NULL,
    ny_reservasjon_id           BIGINT                                  NOT NULL,
    endretAv                    BIGINT                                  NOT NULL,
    opprettet                   timestamp                               NOT NULL default localtimestamp,

    CONSTRAINT FK_RESERVASJON_V3_ENDRING_01
        FOREIGN KEY(annullert_reservasjon_id) references RESERVASJON_V3(id),
    CONSTRAINT FK_RESERVASJON_V3_ENDRING_02
        FOREIGN KEY(ny_reservasjon_id) references RESERVASJON_V3(id),
    CONSTRAINT FK_RESERVASJON_V3
        FOREIGN KEY (endretAv) references SAKSBEHANDLER(id)
);

comment on table RESERVASJON_V3_ENDRING is 'Historikk på endring/overføring av reservasjoner';

comment on column RESERVASJON_V3_ENDRING.annullert_reservasjon_id is 'Peker på reservasjonen som ble annullert når man overførte reservasjon til ny saksbehandler, eller bare annullerte reservasjonen';
comment on column RESERVASJON_V3_ENDRING.ny_reservasjon_id is 'Peker på reservasjonen som blir aktiv etter overføring dersom reservasjonen har blitt overført til ny saksbehandler. Hvis reservasjonen har blitt annullert er kolonnen null';
comment on column RESERVASJON_V3_ENDRING.endretAv is 'Brukeren som har endret reservasjonen';