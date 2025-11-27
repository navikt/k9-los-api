CREATE TABLE uttrekk (
    id SERIAL PRIMARY KEY,
    opprettet_tidspunkt TIMESTAMP NOT NULL DEFAULT NOW(),
    status TEXT NOT NULL,
    lagret_sok INT REFERENCES lagret_sok(id),
    kjoreplan TEXT,
    resultat JSONB,
    startet_tidspunkt TIMESTAMP,
    fullfort_tidspunkt TIMESTAMP
);