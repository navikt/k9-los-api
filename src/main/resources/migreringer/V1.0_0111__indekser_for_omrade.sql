-- CONCURRENTLY unngår å blokkere skriving mens indeksene bygges.
-- Drop først gjør migreringen restartbar etter en avbrutt concurrent-bygging.
drop index concurrently if exists idx_reservasjon_v3_omrade_id;
drop index concurrently if exists idx_event_nokkel_omrade_id;
drop index concurrently if exists idx_oppgave_pep_cache_omrade_id;
drop index concurrently if exists idx_oppgave_id_part_omrade_id;

create index concurrently idx_reservasjon_v3_omrade_id
    on reservasjon_v3 (omrade_id);

create index concurrently idx_event_nokkel_omrade_id
    on event_nokkel (omrade_id);

create index concurrently idx_oppgave_pep_cache_omrade_id
    on oppgave_pep_cache (omrade_id);

create index concurrently idx_oppgave_id_part_omrade_id
    on oppgave_id_part (omrade_id);
