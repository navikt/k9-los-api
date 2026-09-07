alter table oppgaveko_saksbehandler
    drop constraint fk_saksbehandler_epost,
    drop column saksbehandler_epost;

alter table saksbehandler
    add constraint saksbehandler_epost_key unique (epost);

alter table saksbehandler
    drop constraint saksbehandler_pkey,
    add constraint saksbehandler_pkey primary key (id);
