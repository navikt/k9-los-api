alter table saksbehandler
    add column sist_oppdatert timestamp null;

comment on column saksbehandler.sist_oppdatert is
    'Tidspunkt for siste vedlikehold av saksbehandlerdata ved innlogging';
