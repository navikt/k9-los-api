-- Kobler område til tabellene som må avgrenses når flere områder tas i bruk.
-- Eksisterende rader tilhører K9. Constraints opprettes som NOT VALID slik at denne
-- migreringen ikke skanner de store tabellene. Valideringen skjer etter commit i
-- V1.0_0109, slik at låsene fra tabellendringene slippes før skanningen starter.

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
    alter table reservasjon_v3 add constraint fk_reservasjon_v3_omrade foreign key (omrade_id) references omrade (id) not valid;

    execute format('alter table oppgaveko_v3 add column omrade_id bigint not null default %s', k9_id);
    alter table oppgaveko_v3 add constraint fk_oppgaveko_v3_omrade foreign key (omrade_id) references omrade (id) not valid;

    create table if not exists saksbehandler_omrade
    (
        saksbehandler_id bigint not null,
        omrade_id        bigint not null,
        constraint pk_saksbehandler_omrade primary key (saksbehandler_id, omrade_id),
        constraint fk_saksbehandler_omrade_saksbehandler foreign key (saksbehandler_id) references saksbehandler (id) on delete cascade,
        constraint fk_saksbehandler_omrade_omrade foreign key (omrade_id) references omrade (id) not valid
    );

    execute format(
            'insert into saksbehandler_omrade (saksbehandler_id, omrade_id) select id, %s from saksbehandler on conflict do nothing',
            k9_id
            );

    execute format('alter table lagret_sok add column omrade_id bigint not null default %s', k9_id);
    alter table lagret_sok add constraint fk_lagret_sok_omrade foreign key (laget_av, omrade_id) references saksbehandler_omrade (saksbehandler_id, omrade_id) not valid;

    execute format('alter table oppgave_pep_cache add column omrade_id bigint not null default %s', k9_id);
    alter table oppgave_pep_cache add constraint fk_oppgave_pep_cache_omrade foreign key (omrade_id) references omrade (id) not valid;

    execute format('alter table event_nokkel add column omrade_id bigint not null default %s', k9_id);
    alter table event_nokkel add constraint fk_event_nokkel_omrade foreign key (omrade_id) references omrade (id) not valid;

    execute format('alter table oppgave_id_part add column omrade_id bigint not null default %s', k9_id);
    alter table oppgave_id_part add constraint fk_oppgave_id_part_omrade foreign key (omrade_id) references omrade (id) not valid;
end
$$;

-- Små tabeller valideres med en gang. Store tabeller valideres etter commit i V1.0_0109.
alter table saksbehandler_omrade validate constraint fk_saksbehandler_omrade_omrade;
alter table oppgaveko_v3 validate constraint fk_oppgaveko_v3_omrade;

alter table oppgave_v3_part
    add column omrade_ekstern_id varchar(100) not null default 'K9';

alter table oppgavefelt_verdi_part
    add column omrade_ekstern_id varchar(100) not null default 'K9';

alter table oppgave_v3_part
    add constraint chk_oppgave_v3_part_k9_omrade
        check (omrade_ekstern_id = 'K9') not valid;

alter table oppgavefelt_verdi_part
    add constraint chk_oppgavefelt_verdi_part_k9_omrade
        check (omrade_ekstern_id = 'K9') not valid;
