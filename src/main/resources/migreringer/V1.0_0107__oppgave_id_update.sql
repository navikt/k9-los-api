-- Update oppgave_id_part schema for temporal use
do
$$
begin
    if to_regclass('public.oppgave_id') is null and to_regclass('public.oppgave_id_part') is not null then
        alter table oppgave_id_part rename to oppgave_id;
    end if;
end
$$;

alter table if exists oppgave_id
    add column if not exists omrade_ekstern_id varchar(100);

update oppgave_id
set omrade_ekstern_id = 'K9'
where omrade_ekstern_id is null;

alter table oppgave_id
    alter column omrade_ekstern_id set not null;

alter table oppgave_id
    drop constraint if exists unique_oppgave_oppgavetype;

alter table oppgave_id
    add constraint unique_oppgave_omrade_oppgavetype unique (omrade_ekstern_id, oppgavetype_ekstern_id, oppgave_ekstern_id);

comment on table oppgave_id is 'Stabil identitet for oppgave på tvers av versjoner/perioder';

