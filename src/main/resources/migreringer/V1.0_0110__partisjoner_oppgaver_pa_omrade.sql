alter table oppgave_v3_part rename to oppgave_v3_k9_part;

create table oppgave_v3_part
(
    id                          int4 references oppgave_id_part(id),
    oppgave_ekstern_id          varchar(100) not null,
    oppgave_ekstern_versjon     varchar(100) not null,
    oppgavetype_ekstern_id      varchar(100) not null,
    reservasjonsnokkel          varchar(50)  not null,
    endret_tidspunkt            timestamp(3) not null,
    oppgavestatus               varchar(50)  not null,
    ferdigstilt_dato            date,
    omrade_ekstern_id           varchar(100) not null default 'K9'
) partition by list (omrade_ekstern_id);

alter table oppgave_v3_part
    attach partition oppgave_v3_k9_part for values in ('K9');

create table oppgave_v3_aktivitetspenger_part
    partition of oppgave_v3_part for values in ('AKTIVITETSPENGER');

alter table oppgavefelt_verdi_part rename to oppgavefelt_verdi_k9_part;

create table oppgavefelt_verdi_part
(
    oppgave_id                 int4 references oppgave_id_part(id),
    feltdefinisjon_ekstern_id  varchar(100) not null,
    verdi                      varchar(100) not null,
    verdi_bigint               int8,
    oppgavestatus              varchar(50)  not null,
    ferdigstilt_dato           date,
    omrade_ekstern_id          varchar(100) not null default 'K9'
) partition by list (omrade_ekstern_id);

alter table oppgavefelt_verdi_part
    attach partition oppgavefelt_verdi_k9_part for values in ('K9');

create table oppgavefelt_verdi_aktivitetspenger_part
    partition of oppgavefelt_verdi_part for values in ('AKTIVITETSPENGER');

create index idx_oppgave_v3_aktivitetspenger_id
    on oppgave_v3_aktivitetspenger_part (id);

create index idx_oppgavefelt_verdi_aktivitetspenger_oppgave_id
    on oppgavefelt_verdi_aktivitetspenger_part (oppgave_id);

create index idx_oppgavefelt_verdi_aktivitetspenger_oppgave_felt_verdi
    on oppgavefelt_verdi_aktivitetspenger_part (oppgave_id, feltdefinisjon_ekstern_id, verdi);

create index idx_ofv_ap_oppgave_felt_verdi_bigint
    on oppgavefelt_verdi_aktivitetspenger_part (oppgave_id, feltdefinisjon_ekstern_id, verdi_bigint)
    where verdi_bigint is not null;

create index idx_oppgavefelt_verdi_aktivitetspenger_felt_verdi_oppgave
    on oppgavefelt_verdi_aktivitetspenger_part (feltdefinisjon_ekstern_id, verdi, oppgave_id);

create index idx_ofv_ap_felt_verdi_bigint_oppgave
    on oppgavefelt_verdi_aktivitetspenger_part (feltdefinisjon_ekstern_id, verdi_bigint, oppgave_id)
    where verdi_bigint is not null;
