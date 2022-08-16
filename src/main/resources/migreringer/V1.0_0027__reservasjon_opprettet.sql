alter table reservasjon add column if not exists opprettet timestamp(3);

create index if not exists idx_reservasjon_opprettet
    on reservasjon (opprettet);