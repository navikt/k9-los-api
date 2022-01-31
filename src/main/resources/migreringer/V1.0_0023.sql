CREATE INDEX ON oppgave((data->>'aktiv'));
CREATE INDEX ON oppgave((data->>'system'));
CREATE INDEX ON oppgave((data->>'kode6'));
CREATE INDEX ON oppgave((data->'behandlingType'->>'kode'));
CREATE INDEX ON oppgave((data->'fagsakYtelseType'->>'kode'));
CREATE INDEX ON oppgave((data->'behandlingStatus'->>'kode'));
CREATE INDEX ON oppgave((data->>'aktorId'));
CREATE INDEX ON oppgave((data->>'pleietrengendeAktørId'));
CREATE INDEX ON oppgave((data->>'journalpostId'));

CREATE INDEX ON oppgave USING GIN (data);

