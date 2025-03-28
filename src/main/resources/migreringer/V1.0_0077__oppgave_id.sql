CREATE TABLE oppgave_id_part(
    id SERIAL PRIMARY KEY,
    oppgave_ekstern_id VARCHAR(100) NOT NULL,
    oppgavetype_ekstern_id VARCHAR(100) NOT NULL,
    CONSTRAINT unique_oppgave_oppgavetype UNIQUE (oppgave_ekstern_id, oppgavetype_ekstern_id)
);