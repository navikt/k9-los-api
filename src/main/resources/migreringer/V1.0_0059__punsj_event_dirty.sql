alter table behandling_prosess_events_k9_punsj add column dirty boolean not null default true;

create table if not exists behandling_prosess_events_k9_punsj_historikkvask_ferdig
(
    id                          varchar(100)     not null primary key,
    constraint fk_bpe_k9_punsj_historikkvask_ferdig
    foreign key(id) references behandling_prosess_events_k9_punsj(id)
);