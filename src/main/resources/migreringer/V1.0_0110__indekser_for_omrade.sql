-- CONCURRENTLY unngaar aa blokkere skriving mens indeksene bygges.
create index concurrently idx_reservasjon_v3_omrade_id
    on reservasjon_v3 (omrade_id);

create index concurrently idx_event_nokkel_omrade_id
    on event_nokkel (omrade_id);

create index concurrently idx_oppgave_pep_cache_omrade_id
    on oppgave_pep_cache (omrade_id);

-- PostgreSQL 16 stoetter ikke CREATE INDEX CONCURRENTLY direkte paa en
-- partisjonert parent. Indeksene bygges derfor paa de faktiske bladpartisjonene.
create index concurrently idx_oppgave_v3_avu_omrade
    on oppgave_v3_aapen_venter_uavklart_part (omrade_ekstern_id);

create index concurrently idx_oppgave_v3_lukket_2020_omrade
    on oppgave_v3_lukket_2020_part (omrade_ekstern_id);

create index concurrently idx_oppgave_v3_lukket_2021_omrade
    on oppgave_v3_lukket_2021_part (omrade_ekstern_id);

create index concurrently idx_oppgave_v3_lukket_2022_omrade
    on oppgave_v3_lukket_2022_part (omrade_ekstern_id);

create index concurrently idx_oppgave_v3_lukket_2023_omrade
    on oppgave_v3_lukket_2023_part (omrade_ekstern_id);

create index concurrently idx_oppgave_v3_lukket_2024_omrade
    on oppgave_v3_lukket_2024_part (omrade_ekstern_id);

create index concurrently idx_oppgave_v3_lukket_2025_omrade
    on oppgave_v3_lukket_2025_part (omrade_ekstern_id);

create index concurrently idx_oppgave_v3_lukket_2026_omrade
    on oppgave_v3_lukket_2026_part (omrade_ekstern_id);
