create index idx_oppgavefelt_verdi_denorm_1 on oppgavefelt_verdi(oppgave_id, omrade_ekstern_id, feltdefinisjon_ekstern_id) where aktiv is true;
create index idx_oppgavefelt_verdi_denorm_2 on oppgavefelt_verdi(oppgave_id, verdi) where aktiv is true;
create index idx_oppgavefelt_verdi_denorm_3 on oppgavefelt_verdi(oppgave_id, omrade_ekstern_id, feltdefinisjon_ekstern_id, oppgavetype_ekstern_id, verdi) where aktiv is true;
create index idx_oppgavefelt_verdi_denorm_3_int on oppgavefelt_verdi(oppgave_id, omrade_ekstern_id, feltdefinisjon_ekstern_id, oppgavetype_ekstern_id, verdi_bigint) where aktiv is true;
create index idx_oppgavefelt_verdi_denorm_4 on oppgavefelt_verdi(feltdefinisjon_ekstern_id, verdi) where aktiv is true;
create index idx_oppgavefelt_verdi_denorm_4_int on oppgavefelt_verdi(feltdefinisjon_ekstern_id, verdi_bigint) where aktiv is true;
