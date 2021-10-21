alter table nye_og_ferdigstilte add column kilde VARCHAR(100) NOT NULL default 'K9SAK';

ALTER TABLE nye_og_ferdigstilte DROP CONSTRAINT nye_og_ferdigstilte_pkey;

ALTER TABLE nye_og_ferdigstilte ADD PRIMARY KEY (behandlingType, fagsakYtelseType, dato, kilde);

ALTER TABLE nye_og_ferdigstilte add unique (behandlingType, fagsakYtelseType, dato, kilde);
