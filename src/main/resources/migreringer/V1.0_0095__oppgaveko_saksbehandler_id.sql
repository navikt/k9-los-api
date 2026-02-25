alter table oppgaveko_saksbehandler
    add column saksbehandler_id bigint;

update oppgaveko_saksbehandler
set saksbehandler_id = saksbehandler.id
from saksbehandler
where oppgaveko_saksbehandler.saksbehandler_epost = saksbehandler.epost;

alter table oppgaveko_saksbehandler
    add constraint oppgaveko_saksbehandler_id_fkey
    foreign key (saksbehandler_id)
    references saksbehandler(id);

alter table oppgaveko_saksbehandler
    alter column saksbehandler_id set not null;

alter table oppgaveko_saksbehandler
    drop column saksbehandler_epost;