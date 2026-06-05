ALTER TABLE feltdefinisjon ADD COLUMN synlighet VARCHAR(20) NOT NULL DEFAULT 'UNDER_STREKEN';
UPDATE feltdefinisjon SET synlighet = 'INTERNT' WHERE vis_til_bruker = false;
UPDATE feltdefinisjon SET synlighet = 'OVER_STREKEN' WHERE kokriterie = true AND vis_til_bruker = true;
ALTER TABLE feltdefinisjon DROP COLUMN kokriterie;
ALTER TABLE feltdefinisjon DROP COLUMN vis_til_bruker;
