CREATE TABLE oppgavefelt_verdi_part
(
    oppgave_ekstern_id        varchar(100) NOT NULL,
    oppgave_ekstern_versjon   varchar(100) NOT NULL,
    omrade_ekstern_id         varchar(100) NOT NULL,
    oppgavetype_ekstern_id    varchar(100) NOT NULL,
    feltdefinisjon_ekstern_id varchar(100) NOT NULL,
    verdi                     varchar(100) NOT NULL,
    verdi_bigint              int8 NULL,
    -- partisjoneringsfelter
    oppgavestatus             varchar(50)  NOT NULL,
    ferdigstilt_dato          date NULL
) PARTITION BY LIST (oppgavestatus);

-- For aktive oppgaver, deler videre etter oppgavestatus
CREATE TABLE oppgavefelt_verdi_aapen_venter_part
    PARTITION OF oppgavefelt_verdi_part
    FOR VALUES IN ('AAPEN', 'VENTER');

-- Legg til constraint for å sikre at ferdigstilt_dato er NULL for AAPEN/VENTER
ALTER TABLE oppgavefelt_verdi_aapen_venter_part
    ADD CONSTRAINT ferdigstilt_dato_is_null
        CHECK (ferdigstilt_dato IS NULL);

CREATE TABLE oppgavefelt_verdi_lukket_part
    PARTITION OF oppgavefelt_verdi_part
    FOR VALUES IN ('LUKKET')
    PARTITION BY RANGE (ferdigstilt_dato);

-- Legg til constraint for å sikre at ferdigstilt_dato IKKE er NULL for LUKKET
ALTER TABLE oppgavefelt_verdi_lukket_part
    ADD CONSTRAINT ferdigstilt_dato_not_null
        CHECK (ferdigstilt_dato IS NOT NULL);

-- Årlige partisjoner for lukkede oppgaver (2020-2025)
CREATE TABLE oppgavefelt_verdi_lukket_2020_part
    PARTITION OF oppgavefelt_verdi_lukket_part
    FOR VALUES FROM ('2020-01-01') TO ('2021-01-01');

CREATE TABLE oppgavefelt_verdi_lukket_2021_part
    PARTITION OF oppgavefelt_verdi_lukket_part
    FOR VALUES FROM ('2021-01-01') TO ('2022-01-01');

CREATE TABLE oppgavefelt_verdi_lukket_2022_part
    PARTITION OF oppgavefelt_verdi_lukket_part
    FOR VALUES FROM ('2022-01-01') TO ('2023-01-01');

CREATE TABLE oppgavefelt_verdi_lukket_2023_part
    PARTITION OF oppgavefelt_verdi_lukket_part
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');

CREATE TABLE oppgavefelt_verdi_lukket_2024_part
    PARTITION OF oppgavefelt_verdi_lukket_part
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE oppgavefelt_verdi_lukket_2025_part
    PARTITION OF oppgavefelt_verdi_lukket_part
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- Indekser
create index ofv_oppgave_id_idx on oppgavefelt_verdi_part (oppgave_ekstern_id);
create index ofv_oppgave_id_verdi_idx on oppgavefelt_verdi_part (oppgave_ekstern_id, feltdefinisjon_ekstern_id, verdi);
create index ofv_oppgave_id_verdi_bigint_idx on oppgavefelt_verdi_part (oppgave_ekstern_id, feltdefinisjon_ekstern_id, verdi_bigint) where verdi_bigint is not null;

create index ofv_feltdefinisjon_verdi_idx on oppgavefelt_verdi_part (feltdefinisjon_ekstern_id, verdi, oppgave_ekstern_id);
create index ofv_feltdefinisjon_verdi_bigint_idx on oppgavefelt_verdi_part (feltdefinisjon_ekstern_id, verdi_bigint, oppgave_ekstern_id) where verdi_bigint is not null;

create index ofv_lukket_ferdigstilt_dato_verdi_idx on oppgavefelt_verdi_lukket_part (ferdigstilt_dato desc, feltdefinisjon_ekstern_id, verdi, oppgave_ekstern_id);
create index ofv_lukket_ferdigstilt_dato_verdi_bigint_idx on oppgavefelt_verdi_lukket_part (ferdigstilt_dato desc, feltdefinisjon_ekstern_id, verdi_bigint, oppgave_ekstern_id) where verdi_bigint is null;