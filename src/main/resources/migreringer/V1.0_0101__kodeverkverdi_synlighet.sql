ALTER TABLE kodeverk_verdi ADD COLUMN synlighet VARCHAR(20) NOT NULL DEFAULT 'UNDER_STREKEN';
ALTER TABLE kodeverk_verdi ADD COLUMN rekkefolge INT;
UPDATE kodeverk_verdi SET synlighet = 'OVER_STREKEN' WHERE favoritt = true;
ALTER TABLE kodeverk_verdi DROP COLUMN favoritt;
