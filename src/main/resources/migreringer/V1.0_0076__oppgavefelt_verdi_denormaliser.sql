alter table oppgavefelt_verdi add column omrade_ekstern_id varchar(100);
alter table oppgavefelt_verdi add column oppgavetype_ekstern_id varchar(100);
alter table oppgavefelt_verdi add column feltdefinisjon_ekstern_id varchar(100);

create index idx_oppgavefelt_verdi_denorm_1 on oppgavefelt_verdi(oppgave_id, omrade_ekstern_id, feltdefinisjon_ekstern_id) where aktiv is true;
create index idx_oppgavefelt_verdi_denorm_2 on oppgavefelt_verdi(oppgave_id, verdi) where aktiv is true;
