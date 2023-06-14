create extension if not exists btree_gist;
create table if not exists RESERVASJON_V3
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    saksbehandler_epost         varchar(100)                            NOT NULL,
    reservasjonsnokkel          varchar(50)                             NOT NULL,
    gyldig_tidsrom              tsrange                                 NOT NULL,
    annullert_for_utlop         boolean                                 default false,
    opprettet                   timestamp                               NOT NULL default localtimestamp,
    sist_endret                 timestamp                               NOT NULL default localtimestamp,
    EXCLUDE USING gist (reservasjonsnokkel with =, gyldig_tidsrom with &&) where (not annullert_for_utlop)
);

comment on table RESERVASJON_V3 is 'Saksbehandleres reservasjoner på oppgaver';
comment on column RESERVASJON_V3.saksbehandler_epost is 'Brukes som identifikator på saksbehandler. På sikt vil vi ha en ID, men epost brukes i den gamle reservasjonsløsningen enn så lenge';
comment on column RESERVASJON_V3.reservasjonsnokkel is 'Identifiserer hva som reserveres. Reservasjonsnøkkelen går igjen på en eller flere oppgaver, hvor alle oppgaver med samme reservasjonsnøkkel vil anses som reservert av den samme reservasjonen.';
comment on column RESERVASJON_V3.gyldig_tidsrom is 'Når en reservasjon gjelder for. Systemet skal ikke tillate mer en 1 reservasjon på 1 nøkkel i et gitt tidsrom.';
comment on column RESERVASJON_V3.annullert_for_utlop is 'Hvis dette feltet er populert er ikke reservasjonen lenger gyldig, og er da unntatt eksklusjonskriteriet';
