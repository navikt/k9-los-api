CREATE TYPE oppgavestatus AS ENUM ('AAPEN', 'VENTER', 'LUKKET');

CREATE TABLE if not exists OPPGAVE_V3_AKTIV
(
    id                          integer GENERATED ALWAYS AS IDENTITY    NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL,
    ekstern_versjon             VARCHAR(100)                            NOT NULL,
    oppgavetype_id              integer                                 NOT NULL,
    status                      oppgavestatus                           NOT NULL,
    kildeomrade                 VARCHAR(30)                             NOT NULL,
    versjon                     int                                     NOT NULL,
    endret_tidspunkt            timestamp(3)                            NOT NULL,
    reservasjonsnokkel          varchar(50)                             NOT NULL,
    CONSTRAINT FK_OPPGAVE_01
        FOREIGN KEY(oppgavetype_id) REFERENCES OPPGAVETYPE(id),
    UNIQUE(kildeomrade, ekstern_id)
) with (FILLFACTOR = 90);

comment on table OPPGAVE_V3_AKTIV is 'Den aktive versjonen av en konkret instans av en definert oppgavetype. Historiske versjoner (inkl aktiv) ligger i Oppgave_V3';
comment on column OPPGAVE_V3_AKTIV.ekstern_id is 'Ekstern nøkkel for å unikt identifisere en oppgave. Eies av adapterne. Må være unik for en gitt oppgave, og ikke kollidere med feks andre oppgaver på samme område.';
comment on column OPPGAVE_V3_AKTIV.ekstern_versjon is 'Ekstern versjonsindikator for versjonering/idempotens av oppgaver. Eies av adapterne';
comment on column OPPGAVE_V3_AKTIV.status is 'Status på oppgaven. Enum styrt av los. Feks ÅPEN, UTFØRT';
comment on column OPPGAVE_V3_AKTIV.versjon is 'Generasjonsteller for oppdateringer av en konkret oppgave.';
comment on column OPPGAVE_V3_AKTIV.endret_tidspunkt is 'Tidspunktet den aktuelle versjonen av oppgaven ble opprettet. Angis av adapter';
comment on column OPPGAVE_V3_AKTIV.reservasjonsnokkel is 'Nøkkel, definert av domeneadapter som eier oppgaven, som saksbehandler låser oppgaven (og andre oppgaver med samme reservasjonsnøkkel) med når de reserverer';

drop index oppgave_v3_aktiv_status_reservasjonsnokkel;
CREATE INDEX oppgave_v3_aktiv_status_reservasjonsnokkel ON oppgave_v3_aktiv(status, reservasjonsnokkel);
drop index oppgave_v3_kildeomrade_ekstern_id_where_aktiv;
CREATE INDEX oppgave_v3_aktiv_kildeomrade_ekstern_id ON oppgave_v3_aktiv USING btree (kildeomrade, ekstern_id);

CREATE TABLE if not exists OPPGAVEFELT_VERDI_AKTIV
(
    id                          integer GENERATED ALWAYS AS IDENTITY    NOT NULL PRIMARY KEY,
    oppgave_id                  integer                                 NOT NULL,
    oppgavestatus               oppgavestatus                           NOT NULL,
    oppgavefelt_id              smallint                                NOT NULL,
    verdi                       VARCHAR(100)                            NOT NULL,

    CONSTRAINT FK_OPPGAVEFELT_VERDI_01
    FOREIGN KEY(oppgave_id) REFERENCES OPPGAVE_V3_AKTIV(id),
    CONSTRAINT FK_OPPGAVEFELT_VERDI_02
    FOREIGN KEY(oppgavefelt_id) REFERENCES OPPGAVEFELT(id)
    ) with (FILLFACTOR = 90);

comment on table OPPGAVEFELT_VERDI_AKTIV is 'Konkrete verdier på en oppgave, som predefinert i OPPGAVEFELT.';
comment on column OPPGAVEFELT_VERDI_AKTIV.verdi is 'Verdiens egenskaper bestemmes av tabellen DATATYPE via fremmednøkkel i oppgavefelt.';

create index ofv_aktiv_oppgave_id_oppgavefelt_id_verdi_oppgave_aapen
    on OPPGAVEFELT_VERDI_AKTIV
        using btree (oppgave_id, oppgavefelt_id, verdi)
    where oppgavestatus = 'AAPEN';

create index ofv_aktiv_oppgave_id_oppgavefelt_id_verdi_oppgave_venter
    on OPPGAVEFELT_VERDI_AKTIV
        using btree (oppgave_id, oppgavefelt_id, verdi)
    where oppgavestatus = 'VENTER';


CREATE TABLE if not exists behandling_prosess_events_k9_sak_aktivvask_ferdig
(
    id                          VARCHAR(100)     NOT NULL PRIMARY KEY,
    CONSTRAINT fk_bpe_k9_aktivvask_ferdig
        FOREIGN KEY(id) references behandling_prosess_events_k9(id)
);

CREATE TABLE if not exists behandling_prosess_events_k9_klage_aktivvask_ferdig
(
    id                          VARCHAR(100)     NOT NULL PRIMARY KEY,
    CONSTRAINT fk_bpe_klage_aktivvask_ferdig
        FOREIGN KEY(id) references behandling_prosess_events_klage(id)
);

CREATE TABLE if not exists behandling_prosess_events_k9_punsj_aktivvask_ferdig
(
    id                          VARCHAR(100)     NOT NULL PRIMARY KEY,
    CONSTRAINT fk_bpe_k9_aktivvask_ferdig
        FOREIGN KEY(id) references behandling_prosess_events_k9_punsj(id)
);
