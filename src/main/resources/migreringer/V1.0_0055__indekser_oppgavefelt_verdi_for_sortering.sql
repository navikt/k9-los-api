create index if not exists oppgavefelt_verdi_oppgave_id_oppgavefelt_id_verdi_oppgave_aapen
    on public.oppgavefelt_verdi
        using btree (oppgave_id, oppgavefelt_id, verdi)
    where aktiv = true and oppgavestatus in ('AAPEN');

create index if not exists oppgavefelt_verdi_oppgave_id_oppgavefelt_id_verdi_oppgaveventer
    on public.oppgavefelt_verdi
        using btree (oppgave_id, oppgavefelt_id, verdi)
    where aktiv = true and oppgavestatus in ('VENTER');