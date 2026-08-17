-- Denormaliserer omraadet ned paa oppgave_v3_part slik at oppgavespoerringer kan filtrere paa
-- omraade uten aa joine mot oppgavetype. Eksisterende rader tilhoerer K9.
--
-- Kolonnen foelger formen til oppgave_v3.omrade_ekstern_id (V1.0_0093) og tabellens egen
-- oppgavetype_ekstern_id, altsaa varchar uten fremmednoekkel, framfor omrade_id-formen i
-- V1.0_0107. Grunnen er at NOT VALID ikke stoettes for fremmednoekler paa partisjonerte
-- tabeller foer PostgreSQL 18, og en validert fremmednoekkel ville lest gjennom hele
-- tabellen under deploy.
--
-- ADD COLUMN med konstant default er en ren metadataoperasjon fra PG11, ogsaa naar den
-- rekurserer ned i partisjonene, saa migreringen tar samme tid uansett radantall.
-- Defaulten fjernes umiddelbart slik at nye rader maa sette omraadet eksplisitt.
--
-- Indeks bygges ikke her. Den hoerer hjemme i fase 2 med CREATE INDEX CONCURRENTLY,
-- jf. V1.0_0107 og OmraadeKoblingRepository.

alter table oppgave_v3_part
    add column omrade_ekstern_id varchar(100) not null default 'K9';

alter table oppgave_v3_part
    alter column omrade_ekstern_id drop default;

comment on column oppgave_v3_part.omrade_ekstern_id is 'Omraadet oppgaven tilhoerer. Denormalisert fra oppgavetype.omrade_id.';
