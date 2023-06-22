alter table OPPGAVEKO_V3 add column endret_tidspunkt timestamp(3);

comment on column OPPGAVEKO_V3.endret_tidspunkt is 'Tidspunktet køen sist ble endret';
