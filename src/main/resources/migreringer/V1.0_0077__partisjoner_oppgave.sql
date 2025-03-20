CREATE TABLE oppgave_v3_part
(
    oppgave_ekstern_id              varchar(100) NOT NULL,
    oppgave_ekstern_versjon         varchar(100) NOT NULL,
    oppgavetype_ekstern_id          varchar(100) NOT NULL,
    reservasjonsnokkel              varchar(50)  NOT NULL,
    endret_tidspunkt                timestamp(3) NOT NULL,
    -- partisjoneringsfelter
    oppgavestatus                   varchar(50)  NOT NULL,
    ferdigstilt_dato                date NULL
) PARTITION BY LIST (oppgavestatus);


-- Deler videre etter oppgavestatus
CREATE TABLE oppgave_v3_aapen_venter_part
    PARTITION OF oppgave_v3_part
    FOR VALUES IN ('AAPEN', 'VENTER');

-- Legg til constraint for å sikre at ferdigstilt_dato er NULL for AAPEN/VENTER
ALTER TABLE oppgave_v3_aapen_venter_part
    ADD CONSTRAINT ferdigstilt_dato_is_null
        CHECK (ferdigstilt_dato IS NULL);

CREATE TABLE oppgave_v3_lukket_part
    PARTITION OF oppgave_v3_part
    FOR VALUES IN ('LUKKET')
    PARTITION BY RANGE (ferdigstilt_dato);

-- Legg til constraint for å sikre at ferdigstilt_dato IKKE er NULL for LUKKET
ALTER TABLE oppgave_v3_lukket_part
    ADD CONSTRAINT ferdigstilt_dato_not_null
        CHECK (ferdigstilt_dato IS NOT NULL);

-- Årlige partisjoner for lukkede oppgaver (2020-2025)
CREATE TABLE oppgave_v3_lukket_2020_part
    PARTITION OF oppgave_v3_lukket_part
    FOR VALUES FROM ('2020-01-01') TO ('2021-01-01');

CREATE TABLE oppgave_v3_lukket_2021_part
    PARTITION OF oppgave_v3_lukket_part
    FOR VALUES FROM ('2021-01-01') TO ('2022-01-01');

CREATE TABLE oppgave_v3_lukket_2022_part
    PARTITION OF oppgave_v3_lukket_part
    FOR VALUES FROM ('2022-01-01') TO ('2023-01-01');

CREATE TABLE oppgave_v3_lukket_2023_part
    PARTITION OF oppgave_v3_lukket_part
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');

CREATE TABLE oppgave_v3_lukket_2024_part
    PARTITION OF oppgave_v3_lukket_part
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE oppgave_v3_lukket_2025_part
    PARTITION OF oppgave_v3_lukket_part
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- Indekser
create index oppgave_id_idx on oppgave_v3_part(oppgave_ekstern_id, oppgave_ekstern_versjon);
