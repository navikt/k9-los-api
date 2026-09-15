alter table oppgave_pep_cache
    rename column kildeomrade to omrade;

alter table oppgave_pep_cache
    rename constraint pep_kildeomrade_eksternid to pep_omrade_eksternid;

alter table oppgave_pep_cache
    rename column kode7 to kode7_eller_egen_ansatt;

alter table oppgave_pep_cache
    drop column egen_ansatt;