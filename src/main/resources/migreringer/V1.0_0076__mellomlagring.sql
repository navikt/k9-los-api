create table mellomlagring(
    nøkkel varchar(20) primary key,
    tidspunkt timestamp not null,
    utløp timestamp not null,
    verdi jsonb not null,
    check (utløp >= tidspunkt)
);