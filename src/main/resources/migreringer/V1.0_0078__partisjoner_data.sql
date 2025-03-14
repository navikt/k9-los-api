INSERT INTO oppgavefelt_verdi_part (oppgave_id, verdi, verdi_bigint, omrade_ekstern_id,
                                    oppgavetype_ekstern_id, feltdefinisjon_ekstern_id, aktiv, oppgavestatus,
                                    ferdigstilt_dato)
SELECT ofv.oppgave_id,
       ofv.verdi,
       ofv.verdi_bigint,
       om.ekstern_id,
       ot.ekstern_id,
       fd.ekstern_id,
       o.aktiv,
       o.status,
       case o.status when 'LUKKET' then o.endret_tidspunkt::date else null end
FROM oppgavefelt_verdi ofv
    INNER JOIN oppgave_v3 o ON ofv.oppgave_id = o.id
    INNER JOIN oppgavetype ot ON o.oppgavetype_id = ot.id
    INNER JOIN oppgavefelt ofelt ON ofv.oppgavefelt_id = ofelt.id
    INNER JOIN feltdefinisjon fd ON ofelt.feltdefinisjon_id = fd.id
    INNER JOIN omrade om ON om.id = fd.omrade_id
ORDER BY ofv.oppgave_id;