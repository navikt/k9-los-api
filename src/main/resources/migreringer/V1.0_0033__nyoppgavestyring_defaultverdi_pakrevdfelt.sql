alter table oppgavefelt add column if not exists defaultverdi varchar(200);

comment on column oppgavefelt.defaultverdi is 'Defaultverdi for påkrevd felt. Blir lagt på oppgaveversjoner som er eldre enn innføringen av et obligatorisk felt';