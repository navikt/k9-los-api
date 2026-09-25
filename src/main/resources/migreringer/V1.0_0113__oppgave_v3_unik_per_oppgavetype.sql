alter table oppgave_v3 drop constraint oppgave_v3_kildeomrade_ekstern_id_versjon_key;
alter table oppgave_v3 add constraint oppgave_v3_kildeomrade_oppgavetype_ekstern_id_versjon_key
    unique (oppgavetype_id, ekstern_id, versjon);

