-- slette kolonne som ikke behøves
alter table oppgavefelt_verdi drop column oppgavestatus;


-- populer aktivkolonne, som er false for alle rader nå
UPDATE oppgavefelt_verdi ofv SET aktiv = true FROM oppgave_v3 o WHERE ofv.oppgave_id = o.id AND o.aktiv = true;


-- legge til kolonner
alter table oppgavefelt_verdi add column omrade_ekstern_id varchar(100);
alter table oppgavefelt_verdi add column oppgavetype_ekstern_id varchar(100);
alter table oppgavefelt_verdi add column feltdefinisjon_ekstern_id varchar(100);


-- populer nye kolonner
with fasit as (select ov.id         as f_id,
                      fd.ekstern_id as f_fd_ekstern_id,
                      o.ekstern_id  as f_omr_ekstern_id,
                      ot.ekstern_id as f_ot_ekstern_id
               from oppgavefelt_verdi ov
                        inner join oppgave_v3 oa on ov.oppgave_id = oa.id
                        inner join oppgavefelt f on ov.oppgavefelt_id = f.id
                        inner join feltdefinisjon fd on f.feltdefinisjon_id = fd.id
                        inner join omrade o on fd.omrade_id = o.id
                        inner join oppgavetype ot on ot.id = oa.oppgavetype_id
               where ov.omrade_ekstern_id is null)
update oppgavefelt_verdi ova
set omrade_ekstern_id         = f_omr_ekstern_id,
    oppgavetype_ekstern_id    = f_ot_ekstern_id,
    feltdefinisjon_ekstern_id = f_fd_ekstern_id from fasit
where fasit.f_id = ova.id
  and ova.omrade_ekstern_id is null;


-- not null
alter table oppgavefelt_verdi alter column omrade_ekstern_id set not null;
alter table oppgavefelt_verdi alter column feltdefinisjon_ekstern_id set not null;
alter table oppgavefelt_verdi alter column oppgavetype_ekstern_id set not null;
