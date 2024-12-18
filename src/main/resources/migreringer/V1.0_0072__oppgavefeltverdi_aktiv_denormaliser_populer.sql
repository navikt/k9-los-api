with fasit as (select ov.id         as f_id,
                      fd.ekstern_id as f_fd_ekstern_id,
                      o.ekstern_id  as f_omr_ekstern_id,
                      ot.ekstern_id as f_ot_ekstern_id
               from oppgavefelt_verdi_aktiv ov
                        inner join oppgave_v3_aktiv oa on ov.oppgave_id = oa.id
                        inner join oppgavefelt f on ov.oppgavefelt_id = f.id
                        inner join feltdefinisjon fd on f.feltdefinisjon_id = fd.id
                        inner join omrade o on fd.omrade_id = o.id
                        inner join oppgavetype ot on ot.id = oa.oppgavetype_id
               where ov.omrade_ekstern_id is null)
update oppgavefelt_verdi_aktiv ova
set omrade_ekstern_id         = f_omr_ekstern_id,
    oppgavetype_ekstern_id    = f_ot_ekstern_id,
    feltdefinisjon_ekstern_id = f_fd_ekstern_id from fasit
where fasit.f_id = ova.id
  and ova.omrade_ekstern_id is null;