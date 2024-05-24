alter table oppgavefelt_verdi
    add column aktiv boolean not null default false,
    add column oppgavestatus varchar(50);