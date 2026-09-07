alter table saksbehandler
    add column sist_oppdatert timestamp(3) null;

comment on column saksbehandler.sist_oppdatert is
    'Tidspunkt for siste vedlikehold av saksbehandlerdata ved innlogging';
