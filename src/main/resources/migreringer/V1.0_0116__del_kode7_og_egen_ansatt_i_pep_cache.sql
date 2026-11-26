alter table oppgave_pep_cache
    add column kode7 boolean,
    add column egen_ansatt boolean;

update oppgave_pep_cache
set kode7 = kode7_eller_egen_ansatt,
    egen_ansatt = kode7_eller_egen_ansatt;
