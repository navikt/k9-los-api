CREATE TABLE uttrekk (
    id SERIAL PRIMARY KEY,
    opprettet_tidspunkt TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status TEXT NOT NULL,
    lagret_sok INT REFERENCES lagret_sok(id),
    kjoreplan TEXT,
    resultat JSONB
);