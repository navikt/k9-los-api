ALTER TABLE nye_og_ferdigstilte DROP CONSTRAINT nye_og_ferdigstilte_pkey;

ALTER TABLE nye_og_ferdigstilte
    DROP COLUMN IF EXISTS kilde;

ALTER TABLE nye_og_ferdigstilte ADD PRIMARY KEY (behandlingType, fagsakYtelseType, dato);

ALTER TABLE nye_og_ferdigstilte add unique (behandlingType, fagsakYtelseType, dato);

