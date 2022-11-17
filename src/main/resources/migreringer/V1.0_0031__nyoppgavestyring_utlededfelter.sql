alter table oppgavefelt add column if not exists feltutleder varchar(200);

comment on column oppgavefelt.feltutleder is 'Klassereferanse til utlederimplementasjon feks no.nav.k9.los.feltutledere.AkkumulertVentetidSaksbehandler';