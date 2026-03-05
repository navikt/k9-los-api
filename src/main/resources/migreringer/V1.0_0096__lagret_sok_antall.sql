ALTER TABLE lagret_sok ADD COLUMN antall INT;
ALTER TABLE lagret_sok ADD COLUMN antall_oppdatert TIMESTAMP;
ALTER TABLE lagret_sok DROP COLUMN versjon;