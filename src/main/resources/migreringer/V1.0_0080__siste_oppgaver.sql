CREATE TABLE siste_oppgaver
(
    oppgave_ekstern_id          varchar(100) NOT NULL,
    oppgavetype_id              int8 NOT NULL references oppgavetype(id),
    saksbehandler               varchar(200) references saksbehandler(epost) NOT NULL,
    tidspunkt                   timestamp NOT NULL,
    PRIMARY KEY (oppgave_ekstern_id, oppgavetype_id, saksbehandler)
);