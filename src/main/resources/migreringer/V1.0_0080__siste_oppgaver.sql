CREATE TABLE siste_oppgaver
(
    oppgave_ekstern_id          varchar(100) NOT NULL,
    oppgavetype_id              int8 NOT NULL references oppgavetype(id),
    bruker_ident                varchar(100) NOT NULL,
    tidspunkt                   timestamp NOT NULL,
    PRIMARY KEY (oppgave_ekstern_id, oppgavetype_id, bruker_ident)
);