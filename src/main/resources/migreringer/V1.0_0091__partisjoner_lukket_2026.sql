CREATE TABLE oppgave_v3_lukket_2026_part
    PARTITION OF oppgave_v3_lukket_part
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE TABLE oppgavefelt_verdi_lukket_2026_part
    PARTITION OF oppgavefelt_verdi_lukket_part
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');