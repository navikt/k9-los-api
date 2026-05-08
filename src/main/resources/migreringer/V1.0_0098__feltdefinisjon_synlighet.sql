ALTER TABLE feltdefinisjon ADD COLUMN synlighet VARCHAR(20) NOT NULL DEFAULT 'UNDER_STREKEN';
UPDATE feltdefinisjon SET synlighet = 'OVER_STREKEN' WHERE kokriterie = true;
ALTER TABLE feltdefinisjon DROP COLUMN kokriterie;
