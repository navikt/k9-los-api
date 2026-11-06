alter table oppgave_v3_dvh_pending
    add column omrade_ekstern_id TEXT NOT NULL DEFAULT 'K9';

alter table oppgave_v3_dvh_pending
    alter column omrade_ekstern_id drop default;

alter table oppgave_v3_dvh_pending drop constraint oppgave_v3_dvh_pending_pkey;

alter table oppgave_v3_dvh_pending
    add primary key (omrade_ekstern_id, oppgavetype_ekstern_id, ekstern_id, ekstern_versjon);
