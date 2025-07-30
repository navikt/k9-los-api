DROP TABLE oppgave_v3_aapen_venter_part;

-- Deler videre etter oppgavestatus-
CREATE TABLE oppgave_v3_aapen_venter_uavklart_part
    PARTITION OF oppgave_v3_part
    FOR VALUES IN ('AAPEN', 'VENTER','UAVKLART');

-- Legg til constraint for å sikre at ferdigstilt_dato er NULL for AAPEN/VENTER
ALTER TABLE oppgave_v3_aapen_venter_uavklart_part
    ADD CONSTRAINT ferdigstilt_dato_is_null
        CHECK (ferdigstilt_dato IS NULL);