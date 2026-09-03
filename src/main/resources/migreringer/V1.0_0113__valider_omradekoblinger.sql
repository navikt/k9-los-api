-- Fremmednøklene ble opprettet som NOT VALID i V1.0_0108 for å unngå
-- tabellskann under den migreringen. VALIDATE CONSTRAINT tillater samtidig
-- lesing og skriving, men verifiserer alle eksisterende rader.

alter table reservasjon_v3
    validate constraint fk_reservasjon_v3_omrade;

alter table lagret_sok
    validate constraint fk_lagret_sok_omrade;

alter table oppgave_pep_cache
    validate constraint fk_oppgave_pep_cache_omrade;

alter table event_nokkel
    validate constraint fk_event_nokkel_omrade;

alter table oppgave_id_part
    validate constraint fk_oppgave_id_part_omrade;

alter table oppgave_v3_part
    validate constraint chk_oppgave_v3_part_k9_omrade;

alter table oppgavefelt_verdi_part
    validate constraint chk_oppgavefelt_verdi_part_k9_omrade;
