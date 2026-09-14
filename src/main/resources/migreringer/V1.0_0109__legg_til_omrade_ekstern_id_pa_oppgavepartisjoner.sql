alter table oppgave_v3_part
    add column omrade_ekstern_id varchar(100) not null default 'K9';

alter table oppgavefelt_verdi_part
    add column omrade_ekstern_id varchar(100) not null default 'K9';

analyze oppgave_v3_part;

analyze oppgavefelt_verdi_part;