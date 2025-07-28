DROP TABLE oppgavefelt_verdi_aapen_venter_part;

-- For aktive oppgaver, deler videre etter oppgavestatus
CREATE TABLE oppgavefelt_verdi_aapen_venter_uavklart_part
    PARTITION OF oppgavefelt_verdi_part
    FOR VALUES IN ('AAPEN', 'VENTER', 'UAVKLART');

-- Legg til constraint for å sikre at ferdigstilt_dato er NULL for AAPEN/VENTER
ALTER TABLE oppgavefelt_verdi_aapen_venter_part
    ADD CONSTRAINT ferdigstilt_dato_is_null
        CHECK (ferdigstilt_dato IS NULL);
