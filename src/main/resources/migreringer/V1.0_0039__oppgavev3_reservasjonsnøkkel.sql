alter table OPPGAVE_V3 add column reservasjonsnøkkel varchar(50);

comment on column OPPGAVE_V3.reservasjonsnøkkel is 'Nøkkel, definert av domeneadapter som eier oppgaven, som saksbehandler låser oppgaven (og andre oppgaver med samme reservasjonsnøkkel) med når de reserverer';
