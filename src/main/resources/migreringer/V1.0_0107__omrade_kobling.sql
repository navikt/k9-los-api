-- Kobler reservasjon_v3, oppgaveko_v3, saksbehandler, lagret_sok, oppgave_pep_cache og event_nokkel til omrade.
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

    create table if not exists saksbehandler_omrade
    (
        saksbehandler_id bigint not null,
        omrade_id        bigint not null,
        constraint pk_saksbehandler_omrade primary key (saksbehandler_id, omrade_id),
        constraint fk_saksbehandler_omrade_saksbehandler foreign key (saksbehandler_id) references saksbehandler (id) on delete cascade,
        constraint fk_saksbehandler_omrade_omrade foreign key (omrade_id) references omrade (id) not valid
    );
    comment on table saksbehandler_omrade is 'Koblingstabell mellom saksbehandler og omrade';
    comment on column saksbehandler_omrade.saksbehandler_id is 'Referanse til saksbehandler';
    comment on column saksbehandler_omrade.omrade_id is 'Referanse til omrade';

    execute format(
            'insert into saksbehandler_omrade (saksbehandler_id, omrade_id) select id, %s from saksbehandler on conflict do nothing',
            k9_id
            );

    execute format('alter table lagret_sok add column omrade_id bigint not null default %s', k9_id);
    alter table lagret_sok alter column omrade_id drop default;
    alter table lagret_sok add constraint fk_lagret_sok_omrade foreign key (laget_av, omrade_id) references saksbehandler_omrade (saksbehandler_id, omrade_id) not valid;
    comment on column lagret_sok.omrade_id is 'Omraadet raden tilhoerer';

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
alter table saksbehandler_omrade validate constraint fk_saksbehandler_omrade_omrade;
alter table oppgaveko_v3 validate constraint fk_oppgaveko_v3_omrade;
