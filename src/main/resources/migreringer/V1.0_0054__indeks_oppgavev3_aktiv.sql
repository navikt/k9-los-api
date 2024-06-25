drop index if exists public.aktive_oppgaver_idx;

CREATE INDEX if not exists oppgave_v3_kildeomrade_ekstern_id_where_aktiv ON public.oppgave_v3 USING btree (kildeomrade, ekstern_id) where aktiv = true;