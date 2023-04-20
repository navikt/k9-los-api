create table if not exists KODEVERK
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    omrade_id                   BIGINT                                  NOT NULL,
    ekstern_id                  VARCHAR(100)                            NOT NULL,
    beskrivelse                 VARCHAR(200)                            ,
    CONSTRAINT FK_KODEVERK_01
        FOREIGN KEY(omrade_id) references OMRADE(id),
    UNIQUE(omrade_id, ekstern_id)
);

comment on table KODEVERK is 'Spesifiserer kjente og/eller lovlige verdier for en feltdefinisjon innenfor et område, og holder på visningsnavn til GUI og forklaringstekster';
comment on column KODEVERK.ekstern_id is 'Human readable navn på kodeverk, som refereres av feltdefinisjon. Flere feltdefinisjoner i et område kan bruke samme kodeverk, men forskjellige oppgaver innenfor et område skal ikke kunne bruke forskjellige kodeverk for samme feltdefinisjon.';
comment on column KODEVERK.beskrivelse is 'Funksjonell beskrivelse av hva verdien som bruker kodeverket brukes til';

create table if not exists KODEVERK_VERDI
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    kodeverk_id                 BIGINT                                  NOT NULL,
    verdi                       VARCHAR(100)                            NOT NULL,
    visningsnavn                VARCHAR(200)                            NOT NULL,
    beskrivelse                 VARCHAR(200)                            ,
    CONSTRAINT FK_KODEVRK_VERDI_01
        FOREIGN KEY(kodeverk_id) references KODEVERK(id),
    UNIQUE (kodeverk_id, verdi)
);

comment on table KODEVERK_VERDI is 'Holder verdiene tilknyttet et kodeverk';
comment on column KODEVERK_VERDI.kodeverk_id is 'Fremmednøkkel for gruppering på kodeverk';
comment on column KODEVERK_VERDI.verdi is 'Verdi som kan oppgis, feks fra en enum';
comment on column KODEVERK_VERDI.visningsnavn is 'Tekst for verdi som brukes i skjermbilder';
comment on column KODEVERK_VERDI.beskrivelse is 'Beskrivelse eller funksjonell forklaring på hva den aktuelle verdien betyr';

alter table FELTDEFINISJON add column kodeverkreferanse VARCHAR(201);

comment on column FELTDEFINISJON.kodeverkreferanse is 'Referanse som kan brukes for å hente ut kodeverk for feltet';