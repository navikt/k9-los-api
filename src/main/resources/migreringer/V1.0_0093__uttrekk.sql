CREATE TABLE uttrekk (
    id SERIAL PRIMARY KEY,
    opprettet_tidspunkt TIMESTAMP NOT NULL DEFAULT NOW(),
    status TEXT NOT NULL,
    tittel TEXT NOT NULL,
    query JSONB NOT NULL,
    type_kjoring TEXT NOT NULL,
    laget_av BIGINT NOT NULL REFERENCES SAKSBEHANDLER(id),
    avgrensning_limit INT,
    avgrensning_offset INT,
    resultat JSONB,
    feilmelding TEXT,
    startet_tidspunkt TIMESTAMP,
    fullfort_tidspunkt TIMESTAMP,
    antall INT
);