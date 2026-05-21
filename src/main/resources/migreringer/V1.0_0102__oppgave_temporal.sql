-- Stable identity for a logical oppgave (one row per business key)
create table if not exists oppgave (
    id                          bigint generated always as identity not null primary key,
    omrade_ekstern_id           varchar(100)                        not null,
    oppgavetype_ekstern_id      varchar(100)                        not null,
    oppgave_ekstern_id          varchar(100)                        not null,
    opprettet_tidspunkt         timestamp(3)                        not null default now(),
    unique (omrade_ekstern_id, oppgavetype_ekstern_id, oppgave_ekstern_id)
    );

comment on table oppgave is 'Stabil identitet for oppgave på tvers av versjoner/perioder';

-- Temporal oppgave state (one row per validity interval)
create table if not exists oppgave_v3_temporal (
    id                          bigint generated always as identity not null primary key,
    oppgave_id                  bigint                               not null,
    ekstern_versjon             varchar(100)                         not null,
    status                      varchar(50)                          not null,
    reservasjonsnokkel          varchar(50)                          not null,
    kildeomrade                 varchar(30)                          not null,
    endret_tidspunkt            timestamp(3)                         not null,
    gyldig_fra                  timestamp(3)                         not null,
    gyldig_til                  timestamp(3)                         null,
    constraint fk_oppgave_v3_temporal_oppgave
    foreign key (oppgave_id) references oppgave(id),
    constraint chk_oppgave_v3_temporal_intervall
    check (gyldig_til is null or gyldig_til > gyldig_fra)
    );

comment on table oppgave_v3_temporal is 'Temporal oppgavestatus med gyldighetsintervall';
comment on column oppgave_v3_temporal.gyldig_fra is 'Inklusiv gyldighetsstart';
comment on column oppgave_v3_temporal.gyldig_til is 'Eksklusiv gyldighetsslutt, NULL betyr aktiv';

-- Temporal field values (high-volume table)
create table if not exists oppgavefelt_verdi_temporal (
    id                          bigint generated always as identity not null primary key,
    oppgave_id                  bigint                               not null,
    oppgavefelt_id              bigint                               not null,
    verdi                       varchar(100)                         not null,
    verdi_bigint                bigint                               null,
    gyldig_fra                  timestamp(3)                         not null,
    gyldig_til                  timestamp(3)                         null,
    constraint fk_ofv_temporal_oppgave
    foreign key (oppgave_id) references oppgave(id),
    constraint fk_ofv_temporal_oppgavefelt
    foreign key (oppgavefelt_id) references oppgavefelt(id),
    constraint chk_ofv_temporal_intervall
    check (gyldig_til is null or gyldig_til > gyldig_fra)
    );

comment on table oppgavefelt_verdi_temporal is 'Temporale feltverdier per oppgave';

-- ---- Current-path constraints/indexes ----

-- Exactly one active oppgavestatus row per oppgave
create unique index if not exists uq_oppgave_v3_temporal_aktiv
    on oppgave_v3_temporal (oppgave_id)
    where gyldig_til is null;

-- Fast current lookup (aktiv=true equivalent)
create index if not exists idx_oppgave_v3_temporal_aktiv_status
    on oppgave_v3_temporal (status, oppgave_id)
    where gyldig_til is null;

create index if not exists idx_oppgave_v3_temporal_aktiv_endret
    on oppgave_v3_temporal (endret_tidspunkt desc, oppgave_id)
    where gyldig_til is null;

-- Current field filters (important hot path)
create index if not exists idx_ofv_temporal_aktiv_felt_verdi
    on oppgavefelt_verdi_temporal (oppgavefelt_id, verdi, oppgave_id)
    where gyldig_til is null;

create index if not exists idx_ofv_temporal_aktiv_felt_bigint
    on oppgavefelt_verdi_temporal (oppgavefelt_id, verdi_bigint, oppgave_id)
    where gyldig_til is null and verdi_bigint is not null;

-- ---- As-of indexes ----
create index if not exists idx_oppgave_v3_temporal_asof
    on oppgave_v3_temporal (oppgave_id, gyldig_fra, gyldig_til);

create index if not exists idx_ofv_temporal_asof
    on oppgavefelt_verdi_temporal (oppgave_id, oppgavefelt_id, gyldig_fra, gyldig_til);