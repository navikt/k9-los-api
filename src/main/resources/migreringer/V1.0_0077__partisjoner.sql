CREATE TABLE oppgavefelt_verdi_part
(
    oppgave_id                int8         NOT NULL REFERENCES oppgave_v3(id),
    omrade_ekstern_id         varchar(100) NOT NULL,
    oppgavetype_ekstern_id    varchar(100) NOT NULL,
    feltdefinisjon_ekstern_id varchar(100) NOT NULL,
    verdi                     varchar(100) NOT NULL,
    verdi_bigint              int8 NULL,
    -- partisjoneringsfelter
    aktiv                     bool         NOT NULL,
    oppgavestatus             varchar(50)  NOT NULL,
    ferdigstilt_dato          date NULL
) PARTITION BY LIST (aktiv);

-- Hovedpartisjoner for aktiv/inaktiv
CREATE TABLE oppgavefelt_verdi_aktiv_part
    PARTITION OF oppgavefelt_verdi_part
    FOR VALUES IN (true)
    PARTITION BY LIST(oppgavestatus);

CREATE TABLE oppgavefelt_verdi_inaktiv_part
    PARTITION OF oppgavefelt_verdi_part
    FOR VALUES IN (false);

-- For aktive oppgaver, deler videre etter oppgavestatus
CREATE TABLE oppgavefelt_verdi_aktiv_aapen_venter_part
    PARTITION OF oppgavefelt_verdi_aktiv_part
    FOR VALUES IN ('AAPEN', 'VENTER');

-- Legg til constraint for å sikre at ferdigstilt_dato er NULL for AAPEN/VENTER
ALTER TABLE oppgavefelt_verdi_aktiv_aapen_venter_part
    ADD CONSTRAINT ferdigstilt_dato_is_null
        CHECK (ferdigstilt_dato IS NULL);

CREATE TABLE oppgavefelt_verdi_aktiv_lukket_part
    PARTITION OF oppgavefelt_verdi_aktiv_part
    FOR VALUES IN ('LUKKET')
    PARTITION BY RANGE (ferdigstilt_dato);

-- Legg til constraint for å sikre at ferdigstilt_dato IKKE er NULL for LUKKET
ALTER TABLE oppgavefelt_verdi_aktiv_lukket_part
    ADD CONSTRAINT ferdigstilt_dato_not_null
        CHECK (ferdigstilt_dato IS NOT NULL);

-- Årlige partisjoner for lukkede oppgaver (2020-2025)
CREATE TABLE oppgavefelt_verdi_aktiv_lukket_2020_part
    PARTITION OF oppgavefelt_verdi_aktiv_lukket_part
    FOR VALUES FROM ('2020-01-01') TO ('2021-01-01');

CREATE TABLE oppgavefelt_verdi_aktiv_lukket_2021_part
    PARTITION OF oppgavefelt_verdi_aktiv_lukket_part
    FOR VALUES FROM ('2021-01-01') TO ('2022-01-01');

CREATE TABLE oppgavefelt_verdi_aktiv_lukket_2022_part
    PARTITION OF oppgavefelt_verdi_aktiv_lukket_part
    FOR VALUES FROM ('2022-01-01') TO ('2023-01-01');

CREATE TABLE oppgavefelt_verdi_aktiv_lukket_2023_part
    PARTITION OF oppgavefelt_verdi_aktiv_lukket_part
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');

CREATE TABLE oppgavefelt_verdi_aktiv_lukket_2024_part
    PARTITION OF oppgavefelt_verdi_aktiv_lukket_part
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE oppgavefelt_verdi_aktiv_lukket_2025_part
    PARTITION OF oppgavefelt_verdi_aktiv_lukket_part
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- Indekser
create index ofv_oppgave_id_idx on oppgavefelt_verdi_part(oppgave_id);
create index ofv_aktiv_aapen_venter_verdi_idx on oppgavefelt_verdi_aktiv_aapen_venter_part(omrade_ekstern_id, feltdefinisjon_ekstern_id, verdi, oppgave_id);
create index ofv_aktiv_aapen_venter_verdi_bigint_idx on oppgavefelt_verdi_aktiv_aapen_venter_part(omrade_ekstern_id, feltdefinisjon_ekstern_id, verdi_bigint, oppgave_id);
create index ofv_aktiv_aapen_venter_idx on oppgavefelt_verdi_aktiv_aapen_venter_part(omrade_ekstern_id, feltdefinisjon_ekstern_id, oppgave_id);
create index ofv_aktiv_lukket_ferdigstilt_dato_idx on oppgavefelt_verdi_aktiv_lukket_part(
    ferdigstilt_dato,
    omrade_ekstern_id,
    feltdefinisjon_ekstern_id,
    oppgavetype_ekstern_id,
    verdi,
    oppgave_id
    );
create index ofv_aktiv_lukket_verdi_idx on oppgavefelt_verdi_aktiv_lukket_part(
    omrade_ekstern_id,
    feltdefinisjon_ekstern_id,
    verdi,
    oppgave_id
    );
create index ofv_aktiv_lukket_verdi_bigint_idx on oppgavefelt_verdi_aktiv_lukket_part(
    omrade_ekstern_id,
    feltdefinisjon_ekstern_id,
    verdi,
    oppgave_id
    );