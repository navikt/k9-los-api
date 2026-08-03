-- Kobler reservasjon_v3, oppgaveko_v3, saksbehandler, oppgave_pep_cache og event_nokkel til omrade.
-- Eksisterende rader tilhoerer K9. Tung validering/indexbygging tas i fase 2 via forvaltning-endepunkter.

insert into omrade(ekstern_id)
values ('K9')
on conflict do nothing;

do
$$
declare
    k9_id bigint;
begin
    select id
    into strict k9_id
    from omrade
    where ekstern_id = 'K9';

    execute format('alter table reservasjon_v3 add column omrade_id bigint not null default %s', k9_id);
    alter table reservasjon_v3 alter column omrade_id drop default;
    alter table reservasjon_v3 add constraint fk_reservasjon_v3_omrade foreign key (omrade_id) references omrade (id) not valid;
    comment on column reservasjon_v3.omrade_id is 'Omraadet raden tilhoerer';

    execute format('alter table oppgaveko_v3 add column omrade_id bigint not null default %s', k9_id);
    alter table oppgaveko_v3 alter column omrade_id drop default;
    alter table oppgaveko_v3 add constraint fk_oppgaveko_v3_omrade foreign key (omrade_id) references omrade (id) not valid;
    comment on column oppgaveko_v3.omrade_id is 'Omraadet raden tilhoerer';

    execute format('alter table saksbehandler add column omrade_id bigint not null default %s', k9_id);
    alter table saksbehandler alter column omrade_id drop default;
    alter table saksbehandler add constraint fk_saksbehandler_omrade foreign key (omrade_id) references omrade (id) not valid;
    comment on column saksbehandler.omrade_id is 'Omraadet raden tilhoerer';

    execute format('alter table oppgave_pep_cache add column omrade_id bigint not null default %s', k9_id);
    alter table oppgave_pep_cache alter column omrade_id drop default;
    alter table oppgave_pep_cache add constraint fk_oppgave_pep_cache_omrade foreign key (omrade_id) references omrade (id) not valid;
    comment on column oppgave_pep_cache.omrade_id is 'Omraadet raden tilhoerer';

    execute format('alter table event_nokkel add column omrade_id bigint not null default %s', k9_id);
    alter table event_nokkel alter column omrade_id drop default;
    alter table event_nokkel add constraint fk_event_nokkel_omrade foreign key (omrade_id) references omrade (id) not valid;
    comment on column event_nokkel.omrade_id is 'Omraadet raden tilhoerer';
end
$$;

-- Smaa tabeller valideres med en gang, store tabeller valideres i backfill-endepunkt.
alter table saksbehandler validate constraint fk_saksbehandler_omrade;
alter table oppgaveko_v3 validate constraint fk_oppgaveko_v3_omrade;

