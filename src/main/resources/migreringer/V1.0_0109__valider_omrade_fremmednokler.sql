-- Fremmednoklene ble opprettet som NOT VALID i V1.0_0107 for aa unngaa
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
