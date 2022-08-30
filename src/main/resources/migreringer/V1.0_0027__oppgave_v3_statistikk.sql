CREATE TABLE if not exists OMRADE
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique
);

comment on table OMRADE is 'Spesifiserer subdomene for oppgaver';
comment on column OMRADE.ekstern_id is 'Navn på område, feks "k9"';

CREATE TABLE if not exists FELTDEFINISJON
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    eksternt_navn               VARCHAR(100)                            NOT NULL,
    omrade_id                   BIGINT                                  NOT NULL, --eier
    liste_type                  boolean                                 NOT NULL,
    parses_som                  VARCHAR(100)                            NOT NULL,
    --vis_til_bruker              boolean                                 NOT NULL DEFAULT TRUE --ikke aktuelt for statistikk
    CONSTRAINT FK_FELTDEFINISJON_01
        FOREIGN KEY(omrade_id) references OMRADE(id),
    UNIQUE(omrade_id, eksternt_navn)
);

comment on table FELTDEFINISJON is 'Spesifiserer lovlige datatyper for et gitt område';
comment on column FELTDEFINISJON.eksternt_navn is 'Human readable navn på datatype som også er eksponert eksternt. Feks "k9.saksnummer". Skal prefikses med område-id for å unngå navnekollisjoner/unique constraint.';
comment on column FELTDEFINISJON.liste_type is 'Flagg som bestemmer om datatypen skal deserialiseres til en list eller om det er en enkeltverdi.';
comment on column FELTDEFINISJON.parses_som is 'Hvilken datatype som brukes, feks String, int, Date.';
--comment on column DATATYPE.vis_til_bruker is 'Flagg som bestemmer om datatypen skal kunne brukes til filtrering og/eller koprioritering, eller om det er et rent infofelt.'

CREATE TABLE if not exists OPPGAVETYPE
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL unique,
    omrade_id                   BIGINT                                  NOT NULL,
    definisjonskilde            VARCHAR(100)                            NOT NULL,
    --offentlig                   boolean                                 NOT NULL DEFAULT FALSE
    CONSTRAINT FK_OPPGAVETYPE_01
        FOREIGN KEY(omrade_id) REFERENCES OMRADE(id)
);

comment on table OPPGAVETYPE is 'Spesifiserer lovlige oppgavetyper for et gitt område.';
comment on column OPPGAVETYPE.ekstern_id is 'Human readable navn på oppgavetype som også er eksponert eksternt. Feks "k9.aksjonspunkt". Skal prefikses med område-id for å unngå navnekollisjoner/unique constraint.';
comment on column OPPGAVETYPE.definisjonskilde is 'Referanse som beskriver hvor definisjonen av oppgavetypen kommer fra. I praksis en "eier" av en gitt oppgavetype. Typisk vil dette være domeneadaptere som konverterer eventer fra fagsystemer til oppgaver i los.';
--comment on column OPPGAVETYPE.offentlig is 'Flagg som bestemmer om andre domeneadaptere enn eier skal få lov til å generere oppgaver av en gitt type';

CREATE TABLE IF NOT EXISTS OPPGAVEFELT
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    feltdefinisjon_id           BIGINT                                  NOT NULL,
    oppgavetype_id              BIGINT                                  NOT NULL,
    --vis_pa_oppgave              boolean                                 NOT NULL DEFAULT TRUE, --ikke aktuelt for statistikk,
    pakrevd                     boolean                                 NOT NULL DEFAULT FALSE,
    CONSTRAINT FK_OPPGAVEFELT_01
        FOREIGN KEY(feltdefinisjon_id) REFERENCES FELTDEFINISJON(id),
    CONSTRAINT FK_OPPGAVEFELT_02
        FOREIGN KEY(oppgavetype_id) REFERENCES OPPGAVETYPE(id)
);

comment on table OPPGAVEFELT is 'Spesifiserer hvilke definerte datatyper som anvendes på en gitt oppgave.';
--comment on column OPPGAVEFELT.vis_pa_oppgave is 'Angir om et felt på en oppgave skal være synlig i frontend eller ikke.';
comment on column OPPGAVEFELT.pakrevd is 'Angir om et felt er påkrevd eller frivillig på den aktuelle oppgaven.';

CREATE TABLE if not exists OPPGAVE_V3
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    ekstern_id                  VARCHAR(100)                            NOT NULL,
    oppgavetype_id              BIGINT                                  NOT NULL,
    status                      VARCHAR(50)                             NOT NULL,
    versjon                     int                                     NOT NULL,
    aktiv                       boolean                                 NOT NULL DEFAULT TRUE,
    kildeomrade                 VARCHAR(30)                             NOT NULL,
    endret_tidspunkt            timestamp(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_OPPGAVE_01
        FOREIGN KEY(oppgavetype_id) REFERENCES OPPGAVETYPE(id),
    UNIQUE(kildeomrade, ekstern_id)
);

comment on table OPPGAVE_V3 is 'Konkrete oppgaver av en definert oppgavetype';
comment on column OPPGAVE_V3.ekstern_id is 'Ekstern nøkkel for idempotens ved opprettelse/oppdatering og oppslag på oppgave. Eies av adapterne. Må være unik for en gitt oppgave, og ikke kollidere med feks andre oppgaver på samme område.';
comment on column OPPGAVE_V3.status is 'Status på oppgaven. Enum styrt av los. Feks ÅPEN, UTFØRT';
comment on column OPPGAVE_V3.versjon is 'Generasjonsteller for oppdateringer av en konkret oppgave. Bevarer historikk';
comment on column OPPGAVE_V3.aktiv is 'Flagg som angir om en versjon av oppgaven er den gyldige';


CREATE TABLE if not exists OPPGAVEFELT_VERDI
(
    id                          BIGINT GENERATED ALWAYS AS IDENTITY     NOT NULL PRIMARY KEY,
    oppgave_id                  BIGINT                                  NOT NULL,
    oppgavefelt_id              BIGINT                                  NOT NULL,
    verdi                       VARCHAR(100)                            NOT NULL,
    CONSTRAINT FK_OPPGAVEFELT_VERDI_01
        FOREIGN KEY(oppgave_id) REFERENCES OPPGAVE_V3(id),
    CONSTRAINT FK_OPPGAVEFELT_VERDI_02
        FOREIGN KEY(oppgavefelt_id) REFERENCES OPPGAVEFELT(id)
);

comment on table OPPGAVEFELT_VERDI is 'Konkrete verdier på en oppgave, som predefinert i OPPGAVEFELT.';
comment on column OPPGAVEFELT_VERDI.verdi is 'Verdiens egenskaper bestemmes av tabellen DATATYPE via fremmednøkkel i oppgavefelt.'