CREATE TABLE uttrekk (
    id SERIAL PRIMARY KEY,
    opprettet_tidspunkt TIMESTAMP NOT NULL DEFAULT NOW(),
    status TEXT NOT NULL,
    lagret_sok INT REFERENCES lagret_sok(id),
    resultat JSONB,
    feilmelding TEXT,
    type_kjoring TEXT,
    startet_tidspunkt TIMESTAMP,
    fullfort_tidspunkt TIMESTAMP,
    antall INT
);