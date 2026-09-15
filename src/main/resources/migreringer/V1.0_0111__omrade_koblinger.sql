insert into omrade(ekstern_id)
values ('K9')
    on conflict do nothing;

do $$
declare
k9_id bigint;
begin
select id
into strict k9_id
from omrade
where ekstern_id = 'K9';

-- Legg til omrade_id på reservasjon_v3, not valid constraint
execute format(
        'alter table reservasjon_v3 add column omrade_id bigint not null default %s',
        k9_id
        );
alter table reservasjon_v3
    add constraint fk_reservasjon_v3_omrade
        foreign key (omrade_id) references omrade (id) not valid;

-- Legg til omrade_id på oppgaveko_v3
execute format(
        'alter table oppgaveko_v3 add column omrade_id bigint not null default %s',
        k9_id
        );
alter table oppgaveko_v3
    add constraint fk_oppgaveko_v3_omrade
        foreign key (omrade_id) references omrade (id);

-- Opprett saksbehandler_omrade tabell
create table if not exists saksbehandler_omrade (
                                                    saksbehandler_id bigint not null,
                                                    omrade_id bigint not null,
                                                    constraint pk_saksbehandler_omrade primary key (saksbehandler_id, omrade_id),
    constraint fk_saksbehandler_omrade_saksbehandler foreign key (saksbehandler_id)
    references saksbehandler (id) on delete cascade,
    constraint fk_saksbehandler_omrade_omrade foreign key (omrade_id)
    references omrade (id)
    );

-- Populer saksbehandler_omrade
execute format(
        'insert into saksbehandler_omrade (saksbehandler_id, omrade_id) '
            'select id, %s from saksbehandler on conflict do nothing',
        k9_id
        );

-- Legg til omrade_id på lagret_sok
execute format(
        'alter table lagret_sok add column omrade_id bigint not null default %s',
        k9_id
        );
alter table lagret_sok
    add constraint fk_lagret_sok_omrade
        foreign key (laget_av, omrade_id) references saksbehandler_omrade (saksbehandler_id, omrade_id);

-- Legg til omrade_id på driftsmeldinger
execute format(
        'alter table driftsmeldinger add column omrade_id bigint not null default %s',
        k9_id
        );
alter table driftsmeldinger
    add constraint fk_driftsmeldinger_omrade
        foreign key (omrade_id) references omrade (id);

-- Legg til omrade_id på oppgave_id_part, not valid constraint
execute format('alter table oppgave_id_part add column omrade_id bigint not null default %s', k9_id);
alter table oppgave_id_part add constraint fk_oppgave_id_part_omrade foreign key (omrade_id) references omrade (id) not valid;

-- Legg til omrade_id på uttrekk
execute format('alter table uttrekk add column omrade_id bigint not null default %s', k9_id);
alter table uttrekk add constraint fk_uttrekk_omrade foreign key (omrade_id) references omrade (id);

end $$;