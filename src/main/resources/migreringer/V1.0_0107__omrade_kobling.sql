-- Kobler RESERVASJON_V3, OPPGAVEKO_V3, SAKSBEHANDLER, OPPGAVE_PEP_CACHE og EVENT_NOKKEL til OMRADE.
-- Alle eksisterende rader tilhoerer omraade 'K9', som er det eneste omraadet som finnes i dag.
--
-- Skriptet er utformet for aa unngaa table rewrite og lange laaser paa store tabeller:
--
--   * ADD COLUMN ... NOT NULL DEFAULT <konstant> er en ren metadataoperasjon i PostgreSQL 11+.
--     Eksisterende rader faar verdien uten at tabellen skrives om, uansett radantall.
--     Derfor trengs ingen UPDATE-backfill.
--   * DEFAULT droppes umiddelbart etterpaa. Det er ogsaa metadata-only, og eksisterende rader
--     beholder verdien. Nye rader maa dermed angi omraade eksplisitt, slik at feil ikke
--     kan snike seg inn via en implisitt standardverdi.
--   * Fremmednoeklene legges til som NOT VALID. Det tar ingen scan, men haandheves likevel
--     for alle nye og endrede rader. Validering av eksisterende rader er utsatt til
--     forvaltningsendepunktet POST /forvaltning/omrade/fremmednokkel/valider, som tar
--     SHARE UPDATE EXCLUSIVE og dermed ikke blokkerer lesing eller skriving.
--
-- Det opprettes bevisst ingen indeks paa omrade_id: med kun ett omraade har kolonnen
-- kardinalitet 1, og en slik indeks vil aldri brukes av planleggeren. Den kan legges til
-- naar flere omraader faktisk tas i bruk.

insert into OMRADE(ekstern_id)
values ('K9')
on conflict do nothing;

do
$$
    declare
        k9_id  bigint;
        tabell text;
    begin
        select id into strict k9_id from OMRADE where ekstern_id = 'K9';

        foreach tabell in array array [
            'reservasjon_v3',
            'oppgaveko_v3',
            'saksbehandler',
            'oppgave_pep_cache',
            'event_nokkel'
            ]
            loop
                execute format(
                        'alter table %I add column omrade_id bigint not null default %s',
                        tabell, k9_id);

                execute format(
                        'alter table %I alter column omrade_id drop default',
                        tabell);

                execute format(
                        'alter table %I add constraint fk_%s_omrade foreign key (omrade_id) references OMRADE (id) not valid',
                        tabell, tabell);

                execute format(
                        'comment on column %I.omrade_id is %L',
                        tabell, 'Omraadet raden tilhoerer');
            end loop;
    end
$$;

-- Disse to tabellene er smaa (antall saksbehandlere / antall koeer), saa fremmednoekkelen
-- valideres med en gang. De store tabellene valideres via forvaltningsendepunktet.
alter table SAKSBEHANDLER
    validate constraint fk_saksbehandler_omrade;

alter table OPPGAVEKO_V3
    validate constraint fk_oppgaveko_v3_omrade;

